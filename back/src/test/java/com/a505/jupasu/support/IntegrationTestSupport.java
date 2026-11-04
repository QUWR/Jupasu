package com.a505.jupasu.support;

import com.a505.jupasu.domain.user.entity.User;
import com.a505.jupasu.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 실제 PostgreSQL(application-test.yml)에 붙는 통합 테스트 공통 지원 클래스.
 * 테스트가 만든 데이터는 커밋되므로 @AfterEach에서 직접 정리한다.
 */
@SpringBootTest(properties = {
        "JWT_SECRET=VGhpc0lzQUR1bW15U2VjcmV0S2V5Rm9yVGVzdGluZ1B1cnBvc2VzMTIzNDU2Nzg5MA=="
})
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    /** 실데이터 ID와 겹치지 않도록 테스트 와인은 큰 ID를 직접 지정한다. */
    private static final AtomicLong TEST_WINE_ID = new AtomicLong(9_000_000_000L + (System.nanoTime() % 1_000_000) * 1_000);

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected EntityManagerFactory entityManagerFactory;

    private final List<Long> createdWineIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    /** JPA를 거치지 않고 테스트 와인을 넣는다 (외부 평점 1,000건 · 평균 4.0). */
    protected long insertTestWine(String nameKr, int price) {
        long id = TEST_WINE_ID.incrementAndGet();
        jdbcTemplate.update("""
                INSERT INTO wine (id, name_kr, name_en, type, price, average_rating,
                    external_average_rating, external_rating_count,
                    external_star1_count, external_star2_count, external_star3_count, external_star4_count, external_star5_count,
                    user_review_count, user_rating_sum,
                    user_star1_count, user_star2_count, user_star3_count, user_star4_count, user_star5_count,
                    display_review_count, display_average_rating,
                    sweetness, acidity, body, tannin, alcohol_degree, created_at, updated_at)
                VALUES (?, ?, ?, 'RED', ?, 4.0,
                    4.0, 1000, 0, 0, 0, 1000, 0,
                    0, 0, 0, 0, 0, 0, 0,
                    1000, 4.0,
                    2.0, 3.0, 4.0, 3.0, 13.0, now(), now())
                """, id, nameKr, nameKr + " EN " + id, price);
        createdWineIds.add(id);
        return id;
    }

    protected User createUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .email("it-" + suffix + "@example.com")
                .passwordHash("password1234")
                .nickname("it" + suffix)
                .character("cat")
                .build());
        createdUserIds.add(user.getId());
        return user;
    }

    protected Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    protected void trackWine(long wineId) {
        createdWineIds.add(wineId);
    }

    @AfterEach
    void cleanUpCreatedRows() {
        for (Long wineId : createdWineIds) {
            jdbcTemplate.update("DELETE FROM review WHERE wine_id = ?", wineId);
            jdbcTemplate.update("DELETE FROM wine_scrap WHERE wine_id = ?", wineId);
            jdbcTemplate.update("DELETE FROM wine_food_pairing WHERE wine_id = ?", wineId);
            jdbcTemplate.update("DELETE FROM wine_taste_group WHERE wine_id = ?", wineId);
            jdbcTemplate.update("DELETE FROM wine WHERE id = ?", wineId);
        }
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM review WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM wine_scrap WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId);
        }
        createdWineIds.clear();
        createdUserIds.clear();
    }
}
