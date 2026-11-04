package com.a505.jupasu.domain.wine;

import com.a505.jupasu.domain.wine.service.DummyDataInitService;
import com.a505.jupasu.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DummyDataInitIntegrationTest extends IntegrationTestSupport {

    private static final List<String> DUMMY_ONLY_FOODS = List.of("샐러드", "피자", "아시안푸드");

    @Autowired
    private DummyDataInitService dummyDataInitService;

    private List<String> foodsBefore;

    @AfterEach
    void removeDummyRows() {
        jdbcTemplate.update("DELETE FROM wine_food_pairing WHERE wine_id IN (SELECT id FROM wine WHERE name_en LIKE 'Dummy Wine %')");
        jdbcTemplate.update("DELETE FROM wine WHERE name_en LIKE 'Dummy Wine %'");
        for (String food : DUMMY_ONLY_FOODS) {
            if (foodsBefore != null && !foodsBefore.contains(food)) {
                jdbcTemplate.update("DELETE FROM food WHERE name = ?", food);
            }
        }
    }

    @Test
    @DisplayName("더미 1만 건 생성: 실데이터와 이름이 같은 음식은 재사용한다 (food.name UNIQUE와 충돌하지 않음)")
    void generateDummyData_reusesExistingFoods() {
        foodsBefore = jdbcTemplate.queryForList("SELECT name FROM food", String.class);
        int foodCountBefore = foodsBefore.size();

        dummyDataInitService.generateDummyData();

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM wine WHERE name_en LIKE 'Dummy Wine %'", Integer.class))
                .isEqualTo(10000);
        long newFoods = DUMMY_ONLY_FOODS.stream().filter(f -> !foodsBefore.contains(f)).count();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM food", Integer.class))
                .isEqualTo(foodCountBefore + (int) newFoods);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) - count(DISTINCT name) FROM food", Integer.class)).isZero();
    }
}
