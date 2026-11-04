package com.a505.jupasu.domain.wine;

import com.a505.jupasu.domain.wine.service.WineDataInitService;
import com.a505.jupasu.global.exception.CustomException;
import com.a505.jupasu.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 크롤링 JSON 적재(WineDataInitService)를 실제 DB에 대해 검증한다.
 * 테스트용 와인은 name_en 접두어로 구분해 끝나면 지운다.
 */
class WineDataInitIntegrationTest extends IntegrationTestSupport {

    private static final int ROWS = 1200;   // 1,000건 청크 2개에 걸치도록

    @Autowired
    private WineDataInitService wineDataInitService;

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String prefix;
    private List<String> foodNames;
    private List<String> tasteNames;

    @BeforeEach
    void setUp() {
        prefix = "ZZIT-" + UUID.randomUUID().toString().substring(0, 6) + " ";
        foodNames = jdbcTemplate.queryForList("SELECT name FROM food ORDER BY id LIMIT 2", String.class);
        tasteNames = jdbcTemplate.queryForList("SELECT name FROM taste_group ORDER BY taste_group_id LIMIT 2", String.class);
        WineDataInitService target = AopTestUtils.getTargetObject(wineDataInitService);
        ReflectionTestUtils.setField(target, "sourceImageDir", tempDir.resolve("src").toString());
        ReflectionTestUtils.setField(target, "targetImageDir", tempDir.resolve("dst").toString());
    }

    @AfterEach
    void removeImportedWines() {
        String like = prefix + "%";
        jdbcTemplate.update("DELETE FROM wine_food_pairing WHERE wine_id IN (SELECT id FROM wine WHERE name_en LIKE ?)", like);
        jdbcTemplate.update("DELETE FROM wine_taste_group WHERE wine_id IN (SELECT id FROM wine WHERE name_en LIKE ?)", like);
        jdbcTemplate.update("DELETE FROM wine WHERE name_en LIKE ?", like);
    }

    private Path writeJson(int rows, Integer brokenIndex) throws Exception {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("wine_name", prefix + "Wine " + i);
            item.put("name_kr", (brokenIndex != null && i == brokenIndex) ? null : "테스트 와인 " + i);   // name_kr은 DB에서 NOT NULL
            item.put("wine_type", "Red wine");
            item.put("price", "₩" + (20000 + i));
            item.put("country", "France");
            item.put("food_pairings", foodNames);
            item.put("top_3_taste_groups_kr", tasteNames);
            item.put("taste_profile", Map.of("sweetness", "40%", "acidity", "60%", "boldness", "80%", "tannic", "70%"));
            item.put("all_facts", Map.of("Alcohol content", "13.5%", "Grapes", "Merlot"));
            item.put("ratings", Map.of("average", 4.1, "count", 100,
                    "distribution", Map.of("1", 1, "2", 4, "3", 15, "4", 50, "5", 30)));
            items.add(item);
        }
        Path file = tempDir.resolve("wines.json");
        objectMapper.writeValue(file.toFile(), items);
        return file;
    }

    private void useDataFile(Path file) {
        WineDataInitService target = AopTestUtils.getTargetObject(wineDataInitService);
        ReflectionTestUtils.setField(target, "dataFilePath", file.toString());
    }

    private int importedWineCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM wine WHERE name_en LIKE ?", Integer.class, prefix + "%");
    }

    @Test
    @DisplayName("1,200건 적재: 와인·매핑이 모두 저장되고 INSERT가 JDBC 배치로 묶인다")
    void import_savesAllRowsWithJdbcBatching() throws Exception {
        useDataFile(writeJson(ROWS, null));
        Statistics stats = statistics();
        stats.clear();

        wineDataInitService.importWineDataFromJson();

        assertThat(importedWineCount()).isEqualTo(ROWS);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM wine_food_pairing WHERE wine_id IN (SELECT id FROM wine WHERE name_en LIKE ?)
                """, Integer.class, prefix + "%")).isEqualTo(ROWS * foodNames.size());
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM wine_taste_group WHERE wine_id IN (SELECT id FROM wine WHERE name_en LIKE ?)
                """, Integer.class, prefix + "%")).isEqualTo(ROWS * tasteNames.size());

        long insertedRows = (long) ROWS * (1 + foodNames.size() + tasteNames.size());
        System.out.printf("[적재 통계] 저장 행=%d, prepare된 JDBC 문장=%d, 엔티티 INSERT=%d%n",
                insertedRows, stats.getPrepareStatementCount(), stats.getEntityInsertCount());
        // 배치가 없으면 행마다 문장을 준비하므로 prepare 수가 저장 행 수(6,000) 이상이 된다
        assertThat(stats.getPrepareStatementCount()).isLessThan(insertedRows / 10);
    }

    @Test
    @DisplayName("같은 파일로 다시 적재해도 와인이 중복 저장되지 않는다 (재실행 안전)")
    void import_twice_doesNotDuplicate() throws Exception {
        useDataFile(writeJson(50, null));

        wineDataInitService.importWineDataFromJson();
        wineDataInitService.importWineDataFromJson();

        assertThat(importedWineCount()).isEqualTo(50);
    }

    @Test
    @DisplayName("두 번째 청크에서 DB 오류가 나면 예외를 던지고 아무것도 저장하지 않는다 (부분 적재 방지)")
    void import_failureInSecondChunk_rollsBackEverything() throws Exception {
        useDataFile(writeJson(ROWS, 1100));

        assertThatThrownBy(() -> wineDataInitService.importWineDataFromJson())
                .isInstanceOf(CustomException.class);
        assertThat(importedWineCount()).isZero();
    }

    @Test
    @DisplayName("JSON 파일이 없으면 성공으로 응답하지 않고 예외를 던진다")
    void import_missingFile_throws() {
        useDataFile(tempDir.resolve("does-not-exist.json"));

        assertThatThrownBy(() -> wineDataInitService.importWineDataFromJson())
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("JSON 형식이 깨져 있으면 예외를 던진다")
    void import_malformedJson_throws() throws Exception {
        Path broken = tempDir.resolve("broken.json");
        Files.writeString(broken, "[{\"wine_name\": \"oops\",");
        useDataFile(broken);

        assertThatThrownBy(() -> wineDataInitService.importWineDataFromJson())
                .isInstanceOf(CustomException.class);
        assertThat(importedWineCount()).isZero();
    }
}
