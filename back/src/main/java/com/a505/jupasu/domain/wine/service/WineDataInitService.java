package com.a505.jupasu.domain.wine.service;

import com.a505.jupasu.domain.wine.dto.parsing.VivinoRawData;
import com.a505.jupasu.domain.wine.entity.*;
import com.a505.jupasu.domain.wine.entity.vo.Origin;
import com.a505.jupasu.domain.wine.entity.vo.RatingSummary;
import com.a505.jupasu.domain.wine.entity.vo.TasteProfile;
import com.a505.jupasu.domain.wine.entity.vo.WinePriceAndRating;
import com.a505.jupasu.domain.wine.repository.*;
import com.a505.jupasu.domain.wine.util.WineDataParser;
import com.a505.jupasu.global.exception.CustomException;
import com.a505.jupasu.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.cache.annotation.CacheEvict;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WineDataInitService {

    private final WineRepository wineRepository;
    private final FoodRepository foodRepository;
    private final WineFoodPairingRepository wineFoodPairingRepository;
    private final TasteGroupRepository tasteGroupRepository;
    private final WineTasteGroupRepository wineTasteGroupRepository;

    private static final int CHUNK_SIZE = 1000;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${SOURCE_IMAGE_DIR:/app/data/image}")
    private String sourceImageDir;

    @Value("${TARGET_IMAGE_DIR:/app/uploads/images/wines}")
    private String targetImageDir;

    @Value("${DATA_FILE_PATH:/app/data/vivino_ultra_results_kr_food_dedup.json}")
    private String dataFilePath;

    /** 적재 결과: 파일의 전체 건수, 새로 저장한 건수, 이미 있어 건너뛴 건수 */
    public record ImportResult(int totalInFile, int inserted, int skipped) {
    }

    /**
     * 크롤링 JSON을 읽어 와인·음식·향 매핑을 저장한다.
     * <ul>
     *     <li>와인·매핑 엔티티는 SEQUENCE 전략 + hibernate.jdbc.batch_size 설정으로 JDBC 배치 INSERT된다.
     *         (IDENTITY였을 때는 saveAll()이어도 한 건씩 INSERT됐다)</li>
     *     <li>1,000건마다 flush()/clear()로 영속성 컨텍스트를 비워 메모리와 변경 감지 비용이 쌓이지 않게 한다.</li>
     *     <li>이미 있는 와인(영문명 기준)은 건너뛰어, 다시 실행해도 중복 저장되지 않는다.</li>
     *     <li>오류가 나면 예외를 던져 전체를 롤백한다. 예전에는 예외를 잡고 로그만 남겨
     *         ① 자바 코드 오류면 앞 청크만 커밋된 채 "성공" 응답, ② DB 오류면 rollback-only로
     *         UnexpectedRollbackException이 나는 등 실패를 알기 어려웠다.</li>
     * </ul>
     */
    @CacheEvict(value = "wines", allEntries = true, cacheManager = "localCacheManager")
    @Transactional
    public ImportResult importWineDataFromJson() {
        log.info("🍷 [1/4] 데이터 적재 시작: {}", dataFilePath);
        List<VivinoRawData> rawDataList = readRawData();
        log.info("🍷 [2/4] JSON에서 {}건을 읽었습니다.", rawDataList.size());
        prepareImageDirectory();

        Set<String> existingNames = new HashSet<>(wineRepository.findAllNameEn());
        Map<String, Food> foodCache = new HashMap<>();
        Map<String, TasteGroup> tasteGroupCache = new HashMap<>();
        List<Wine> wineBatch = new ArrayList<>();
        List<VivinoRawData> rawBatch = new ArrayList<>();
        int inserted = 0;
        int skipped = 0;

        log.info("🍷 [3/4] 와인 엔티티 변환 및 연관관계 저장 진행 중...");
        try {
            for (VivinoRawData raw : rawDataList) {
                // 이미 저장된 와인(또는 파일 안의 중복)은 건너뛴다
                if (StringUtils.hasText(raw.wineName()) && !existingNames.add(raw.wineName())) {
                    skipped++;
                    continue;
                }
                wineBatch.add(toWine(raw));
                rawBatch.add(raw);

                if (wineBatch.size() == CHUNK_SIZE) {
                    inserted += saveChunk(wineBatch, rawBatch, foodCache, tasteGroupCache);
                    log.info("👉 {}건 저장 완료 (건너뜀 {}건)", inserted, skipped);
                }
            }
            if (!wineBatch.isEmpty()) {
                inserted += saveChunk(wineBatch, rawBatch, foodCache, tasteGroupCache);
            }
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            // 예외를 다시 던져야 트랜잭션 전체가 롤백되고 호출한 쪽도 실패를 알 수 있다
            log.error("❌ 와인 데이터 적재 실패 (저장 {}건 진행 중): {}", inserted, e.getMessage(), e);
            throw new CustomException(ErrorCode.WINE_DATA_IMPORT_FAILED, "(" + e.getClass().getSimpleName() + ")");
        }

        log.info("🎉 [4/4] 적재 완료: 전체 {}건, 저장 {}건, 건너뜀 {}건", rawDataList.size(), inserted, skipped);
        return new ImportResult(rawDataList.size(), inserted, skipped);
    }

    private List<VivinoRawData> readRawData() {
        File jsonFile = new File(dataFilePath);
        if (!jsonFile.exists()) {
            throw new CustomException(ErrorCode.WINE_DATA_FILE_NOT_FOUND, "(" + dataFilePath + ")");
        }
        try {
            return objectMapper.readValue(jsonFile, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("❌ JSON 파싱 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.WINE_DATA_IMPORT_FAILED, "(JSON 파싱 실패)");
        }
    }

    private void prepareImageDirectory() {
        try {
            Path uploadPath = Paths.get(targetImageDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
        } catch (Exception folderEx) {
            log.warn("⚠️ 이미지 저장 디렉토리 생성 실패 (이미 있을 수 있음): {}", folderEx.getMessage());
        }
    }

    /**
     * 와인 한 청크와 매핑을 저장하고 영속성 컨텍스트를 비운다.
     * SEQUENCE 전략이라 persist 시점에는 INSERT 없이 ID만 할당되고, flush() 때 배치로 묶여 전송된다.
     * clear() 후 캐시의 Food·TasteGroup은 준영속이 되지만, 매핑의 FK로는 ID만 쓰이므로 그대로 사용할 수 있다.
     */
    private int saveChunk(List<Wine> wineBatch, List<VivinoRawData> rawBatch,
                          Map<String, Food> foodCache, Map<String, TasteGroup> tasteGroupCache) {
        List<Wine> savedWines = wineRepository.saveAll(wineBatch);
        List<WineFoodPairing> pairingBatch = new ArrayList<>();
        List<WineTasteGroup> tasteGroupBatch = new ArrayList<>();

        // saveAll이 돌려준 와인과 같은 순서의 원본 데이터를 짝지어 매핑을 만든다
        for (int j = 0; j < savedWines.size(); j++) {
            Wine savedWine = savedWines.get(j);
            VivinoRawData raw = rawBatch.get(j);

            for (String foodName : distinctNames(raw.foodPairings())) {
                // 같은 음식은 이름당 한 번만 조회·생성한다 (매핑 22,253건 → 조회 30회 수준)
                Food food = foodCache.computeIfAbsent(foodName, key ->
                        foodRepository.findByName(key).orElseGet(() -> foodRepository.save(Food.builder().name(key).build())));
                pairingBatch.add(WineFoodPairing.builder().wine(savedWine).food(food).build());
            }
            for (String tasteName : distinctNames(raw.top3TasteGroupsKr())) {
                TasteGroup tasteGroup = tasteGroupCache.computeIfAbsent(tasteName, key ->
                        tasteGroupRepository.findByName(key).orElseGet(() -> tasteGroupRepository.save(TasteGroup.builder().name(key).build())));
                tasteGroupBatch.add(WineTasteGroup.builder().wine(savedWine).tasteGroup(tasteGroup).build());
            }
        }

        wineFoodPairingRepository.saveAll(pairingBatch);
        wineTasteGroupRepository.saveAll(tasteGroupBatch);

        entityManager.flush();
        entityManager.clear();

        int saved = savedWines.size();
        wineBatch.clear();
        rawBatch.clear();
        return saved;
    }

    /** 빈 값과 중복을 제거한 이름 목록 (매핑 UNIQUE(wine_id, food_id) 위반 방지) */
    private List<String> distinctNames(List<String> names) {
        if (names == null) {
            return List.of();
        }
        return names.stream().filter(StringUtils::hasText).distinct().toList();
    }

    private Wine toWine(VivinoRawData raw) {
        Map<String, String> allFacts = raw.allFacts() != null ? raw.allFacts() : Map.of();
        Map<String, String> taste = raw.tasteProfile() != null ? raw.tasteProfile() : Map.of();

        Object[] priceInfo = WineDataParser.parsePrice(raw.price());
        Object[] alcInfo = WineDataParser.parseAlcohol(allFacts.get("Alcohol content"));
        Map<String, Object> ratings = parseRatingsMap(raw);
        double externalAverageRating = parseDouble(ratings.get("average"));
        int externalRatingCount = parseInt(ratings.get("count"));

        @SuppressWarnings("unchecked")
        Map<String, Object> distribution =
                ratings.get("distribution") instanceof Map<?, ?> dist
                        ? (Map<String, Object>) dist
                        : Map.of();

        int star1 = parseInt(distribution.get("1"));
        int star2 = parseInt(distribution.get("2"));
        int star3 = parseInt(distribution.get("3"));
        int star4 = parseInt(distribution.get("4"));
        int star5 = parseInt(distribution.get("5"));

        Object[] sweetnessInfo = WineDataParser.parseTaste(taste.get("sweetness"));
        Object[] acidityInfo = WineDataParser.parseTaste(taste.get("acidity"));
        Object[] bodyInfo = WineDataParser.parseTaste(taste.get("boldness"));
        Object[] tanninInfo = WineDataParser.parseTaste(taste.get("tannic"));

        String grapes = allFacts.getOrDefault("Grapes", "");
        String winery = StringUtils.hasText(raw.winery()) ? raw.winery() : allFacts.getOrDefault("Winery", "");
        String region = StringUtils.hasText(raw.region()) ? raw.region() : allFacts.getOrDefault("Region", "");
        String style = allFacts.getOrDefault("Wine style", "");

        String finalImageUrl = processImageFile(raw.localImagePaths());

        Wine wine = Wine.builder()
                .nameKr(raw.nameKr())
                .isRealNameKr(StringUtils.hasText(raw.nameKr()))
                .nameEn(raw.wineName())
                .isRealNameEn(StringUtils.hasText(raw.wineName()))
                .type(WineDataParser.parseType(raw.wineType()))
                .grapeVariety(grapes)
                .style(style)
                .description(raw.description())
                .imageUrl(finalImageUrl)
                .alcoholDegree((Float) alcInfo[0])
                .isRealAlcoholDegree((Boolean) alcInfo[1])
                .origin(Origin.builder()
                        .country(raw.country())
                        .isRealCountry(StringUtils.hasText(raw.country()))
                        .region(region)
                        .isRealRegion(StringUtils.hasText(region))
                        .winery(winery)
                        .isRealWinery(StringUtils.hasText(winery))
                        .build())
                .priceAndRating(WinePriceAndRating.builder()
                        .price((Integer) priceInfo[0])
                        .isRealPrice((Boolean) priceInfo[1])
                        .averageRating(externalAverageRating) // 초기값
                        .isRealRating(externalRatingCount > 0)
                        .build())
                .ratingSummary(RatingSummary.builder().build())
                .tasteProfile(TasteProfile.builder()
                        .sweetness((Float) sweetnessInfo[0])
                        .isRealSweetness((Boolean) sweetnessInfo[1])
                        .acidity((Float) acidityInfo[0])
                        .isRealAcidity((Boolean) acidityInfo[1])
                        .body((Float) bodyInfo[0])
                        .isRealBody((Boolean) bodyInfo[1])
                        .tannin((Float) tanninInfo[0])
                        .isRealTannin((Boolean) tanninInfo[1])
                        .build())
                .richDescription(raw.richDescription())
                .embedding(parseVector(raw.embedding()))
                .embeddingModel(raw.embeddingModel())
                .embeddingGeneratedAt(parseOffsetDateTime(raw.embeddingGeneratedAt()))
                .build();

        wine.initializeExternalRatings(
                externalAverageRating,
                externalRatingCount,
                star1, star2, star3, star4, star5
        );
        return wine;
    }

    private String processImageFile(Map<String, String> localImagePaths) {
        String defaultImageUrl = "/default_wine.png";
        if (localImagePaths == null || !localImagePaths.containsKey("bottle")) {
            return defaultImageUrl;
        }

        String rawPathStr = localImagePaths.get("bottle");
        if (!StringUtils.hasText(rawPathStr)) return defaultImageUrl;

        try {
            String fileNameOnly = rawPathStr;
            if (fileNameOnly.contains("\\")) fileNameOnly = fileNameOnly.substring(fileNameOnly.lastIndexOf("\\") + 1);
            if (fileNameOnly.contains("/")) fileNameOnly = fileNameOnly.substring(fileNameOnly.lastIndexOf("/") + 1);

            Path sourcePath = Paths.get(sourceImageDir, fileNameOnly);
            if (!Files.exists(sourcePath)) return defaultImageUrl;

            String extension = fileNameOnly.lastIndexOf(".") > 0 ? fileNameOnly.substring(fileNameOnly.lastIndexOf(".")) : "";
            String newFileName = UUID.randomUUID().toString() + extension;
            Path targetPath = Paths.get(targetImageDir, newFileName);

            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            return "/images/wines/" + newFileName;

        } catch (Exception e) {
            log.error("이미지 복사 실패: {}", e.getMessage());
            return defaultImageUrl;
        }
    }

    private int parseInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private double parseDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(value.toString().trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseRatingsMap(VivinoRawData raw) {
        if (raw.ratings() == null) return Map.of();
        return (Map<String, Object>) raw.ratings();
    }

    private OffsetDateTime parseOffsetDateTime(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception e) {
            log.warn("⚠️ 날짜 파싱 실패: {}", value);
            return null;
        }
    }

    private float[] parseVector(List<Double> values) {
        if (values == null || values.isEmpty()) return null;
        try {
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vector[i] = values.get(i).floatValue();
            }
            return vector;
        } catch (Exception e) {
            log.warn("⚠️ 벡터 변환 실패: {}", e.getMessage());
            return null;
        }
    }
}
