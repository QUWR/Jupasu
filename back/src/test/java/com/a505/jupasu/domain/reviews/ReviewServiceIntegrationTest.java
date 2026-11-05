package com.a505.jupasu.domain.reviews;

import com.a505.jupasu.domain.reviews.dto.ReviewCreateRequest;
import com.a505.jupasu.domain.reviews.dto.ReviewUpdateRequest;
import com.a505.jupasu.domain.reviews.service.ReviewService;
import com.a505.jupasu.domain.user.entity.User;
import com.a505.jupasu.domain.wine.entity.Wine;
import com.a505.jupasu.global.exception.CustomException;
import com.a505.jupasu.global.exception.ErrorCode;
import com.a505.jupasu.support.IntegrationTestSupport;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ReviewService reviewService;

    private Map<String, Object> ratingRow(long wineId) {
        return jdbcTemplate.queryForMap("""
                SELECT user_review_count, user_rating_sum, user_star1_count, user_star2_count, user_star4_count,
                       user_star5_count, display_review_count, display_average_rating, average_rating
                FROM wine WHERE id = ?
                """, wineId);
    }

    @Test
    @DisplayName("없는 와인 ID로 리뷰를 쓰면 500(FK 위반)이 아니라 WINE_NOT_FOUND(404)")
    void createReview_withMissingWine_throwsWineNotFound() {
        User user = createUser();

        assertThatThrownBy(() -> reviewService.createReview(user.getId(), 8_999_999_999L, new ReviewCreateRequest(5, "good")))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.WINE_NOT_FOUND);
    }

    @Test
    @DisplayName("내용 없이 별점만 남겨도 저장된다 (review.content NOT NULL 대응)")
    void createReview_withoutContent_succeeds() {
        User user = createUser();
        long wineId = insertTestWine("내용없는리뷰와인", 30000);

        Long reviewId = reviewService.createReview(user.getId(), wineId, new ReviewCreateRequest(4, null));

        assertThat(reviewId).isNotNull();
        assertThat(jdbcTemplate.queryForObject("SELECT content FROM review WHERE id = ?", String.class, reviewId)).isEmpty();
    }

    @Test
    @DisplayName("리뷰 작성 시 Wine 엔티티를 조회하지 않는다 (평점은 원자적 UPDATE로 반영)")
    void createReview_doesNotLoadWineEntity() {
        User user = createUser();
        long wineId = insertTestWine("조회없는평점와인", 30000);
        Statistics stats = statistics();
        stats.clear();

        reviewService.createReview(user.getId(), wineId, new ReviewCreateRequest(5, "good"));

        assertThat(stats.getEntityStatistics(Wine.class.getName()).getLoadCount()).isZero();
    }

    @Test
    @DisplayName("20명이 동시에 같은 와인에 리뷰를 써도 평점 집계가 하나도 사라지지 않는다 (Lost Update 방지)")
    void concurrentReviews_doNotLoseRatingUpdates() throws Exception {
        int users = 20;
        long wineId = insertTestWine("동시성평점와인", 30000);
        List<User> reviewers = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            reviewers.add(createUser());
        }

        List<Callable<Long>> tasks = new ArrayList<>();
        for (User reviewer : reviewers) {
            tasks.add(() -> reviewService.createReview(reviewer.getId(), wineId, new ReviewCreateRequest(5, "동시 작성")));
        }
        runConcurrently(tasks);

        Map<String, Object> row = ratingRow(wineId);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM review WHERE wine_id = ?", Integer.class, wineId)).isEqualTo(users);
        assertThat(((Number) row.get("user_review_count")).intValue()).isEqualTo(users);
        assertThat(((Number) row.get("user_rating_sum")).intValue()).isEqualTo(5 * users);
        assertThat(((Number) row.get("user_star5_count")).intValue()).isEqualTo(users);
        assertThat(((Number) row.get("display_review_count")).intValue()).isEqualTo(1000 + users);
        double expectedAverage = (4.0 * 1000 + 5 * users) / (1000.0 + users);
        assertThat(((Number) row.get("display_average_rating")).doubleValue()).isCloseTo(expectedAverage, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(((Number) row.get("average_rating")).doubleValue()).isCloseTo(expectedAverage, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("같은 사용자가 같은 와인에 동시에 두 번 요청해도 리뷰는 1개만 저장되고 나머지는 REVIEW_ALREADY_EXISTS")
    void concurrentDuplicateReviews_storeOnlyOne() throws Exception {
        for (int round = 0; round < 5; round++) {
            User user = createUser();
            long wineId = insertTestWine("중복리뷰와인" + round, 30000);

            List<Callable<Long>> tasks = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                tasks.add(() -> reviewService.createReview(user.getId(), wineId, new ReviewCreateRequest(3, "중복")));
            }
            List<Throwable> failures = runConcurrently(tasks);

            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM review WHERE wine_id = ?", Integer.class, wineId)).isEqualTo(1);
            assertThat(failures).hasSize(3).allSatisfy(t ->
                    assertThat(t).isInstanceOf(CustomException.class)
                            .extracting(e -> ((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.REVIEW_ALREADY_EXISTS));
            assertThat(((Number) ratingRow(wineId).get("user_review_count")).intValue()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("리뷰 수정·삭제 후에도 별점 합계·개수·분포가 정확하다")
    void updateAndDelete_keepRatingSummaryConsistent() {
        User user = createUser();
        long wineId = insertTestWine("수정삭제와인", 30000);

        Long reviewId = reviewService.createReview(user.getId(), wineId, new ReviewCreateRequest(5, "처음"));
        reviewService.updateReview(user.getId(), wineId, reviewId, new ReviewUpdateRequest(2, "수정"));

        Map<String, Object> afterUpdate = ratingRow(wineId);
        assertThat(((Number) afterUpdate.get("user_review_count")).intValue()).isEqualTo(1);
        assertThat(((Number) afterUpdate.get("user_rating_sum")).intValue()).isEqualTo(2);
        assertThat(((Number) afterUpdate.get("user_star5_count")).intValue()).isZero();
        assertThat(((Number) afterUpdate.get("user_star2_count")).intValue()).isEqualTo(1);
        assertThat(((Number) afterUpdate.get("display_average_rating")).doubleValue())
                .isCloseTo((4.0 * 1000 + 2) / 1001.0, org.assertj.core.data.Offset.offset(1e-9));

        reviewService.deleteReview(user.getId(), wineId, reviewId);

        Map<String, Object> afterDelete = ratingRow(wineId);
        assertThat(((Number) afterDelete.get("user_review_count")).intValue()).isZero();
        assertThat(((Number) afterDelete.get("user_rating_sum")).intValue()).isZero();
        assertThat(((Number) afterDelete.get("user_star2_count")).intValue()).isZero();
        assertThat(((Number) afterDelete.get("display_review_count")).intValue()).isEqualTo(1000);
        assertThat(((Number) afterDelete.get("display_average_rating")).doubleValue()).isCloseTo(4.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(jdbcTemplate.queryForObject("SELECT review_count FROM users WHERE user_id = ?", Integer.class, user.getId())).isZero();
    }

    /** 모든 작업을 동시에 시작시키고, 실패한 작업의 예외 목록을 돌려준다. */
    private List<Throwable> runConcurrently(List<Callable<Long>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();
        for (Callable<Long> task : tasks) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return task.call();
            }));
        }
        ready.await();
        start.countDown();
        List<Throwable> failures = new ArrayList<>();
        for (Future<Long> future : futures) {
            try {
                future.get(60, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException e) {
                failures.add(e.getCause());
            }
        }
        executor.shutdown();
        return failures;
    }
}
