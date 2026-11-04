package com.a505.jupasu.domain.wine.repository;

import com.a505.jupasu.domain.wine.entity.Wine;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WineRepository extends JpaRepository<Wine, Long>, WineRepositoryCustom, WineSearchRepositoryCustom {
    // 키워드가 포함된 와인 검색 (임시 구현)
    List<Wine> findByNameKrContainingOrNameEnContainingIgnoreCase(String nameKr, String nameEn);

    @Query(value =
            "SELECT id FROM wine " +
                    "WHERE :keyword <% name_kr " +
                    "ORDER BY word_similarity(:keyword, name_kr) DESC " +
                    "LIMIT 100", nativeQuery = true)
    List<Long> findMatchingIdsByKeyword(@Param("keyword") String keyword);

    List<Wine> findAllByIdIn(List<Long> wineIds);

    /** 적재 재실행 시 이미 있는 와인을 건너뛰기 위한 영문명 목록 */
    @Query("SELECT w.nameEn FROM Wine w WHERE w.nameEn IS NOT NULL")
    List<String> findAllNameEn();

    /**
     * 사용자 리뷰 별점 변화를 와인의 평점 집계(RatingSummary)에 원자적으로 반영한다.
     * <p>
     * 엔티티를 읽어 자바에서 계산한 뒤 저장하면 동시 리뷰 작성 시 한쪽 변경이 덮어써진다(Lost Update).
     * UPDATE 한 문장으로 DB가 행을 잠근 채 계산하므로 동시 요청에도 값이 사라지지 않고, 와인 엔티티를 조회할 필요도 없다.
     * PostgreSQL의 SET 식은 모두 갱신 전 값을 참조하므로 개수·합계·평균이 같은 기준으로 계산된다.
     *
     * @param countDelta    리뷰 수 변화 (작성 +1, 수정 0, 삭제 -1)
     * @param sumDelta      별점 합계 변화
     * @param addedRating   새로 더해지는 별점 (없으면 0)
     * @param removedRating 빠지는 별점 (없으면 0)
     * @return 갱신된 행 수. 0이면 와인이 존재하지 않는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE wine SET
                user_review_count = COALESCE(user_review_count, 0) + :countDelta,
                user_rating_sum   = COALESCE(user_rating_sum, 0) + :sumDelta,
                user_star1_count  = COALESCE(user_star1_count, 0) + (CASE WHEN :addedRating = 1 THEN 1 ELSE 0 END) - (CASE WHEN :removedRating = 1 THEN 1 ELSE 0 END),
                user_star2_count  = COALESCE(user_star2_count, 0) + (CASE WHEN :addedRating = 2 THEN 1 ELSE 0 END) - (CASE WHEN :removedRating = 2 THEN 1 ELSE 0 END),
                user_star3_count  = COALESCE(user_star3_count, 0) + (CASE WHEN :addedRating = 3 THEN 1 ELSE 0 END) - (CASE WHEN :removedRating = 3 THEN 1 ELSE 0 END),
                user_star4_count  = COALESCE(user_star4_count, 0) + (CASE WHEN :addedRating = 4 THEN 1 ELSE 0 END) - (CASE WHEN :removedRating = 4 THEN 1 ELSE 0 END),
                user_star5_count  = COALESCE(user_star5_count, 0) + (CASE WHEN :addedRating = 5 THEN 1 ELSE 0 END) - (CASE WHEN :removedRating = 5 THEN 1 ELSE 0 END),
                display_review_count = COALESCE(external_rating_count, 0) + COALESCE(user_review_count, 0) + :countDelta,
                display_average_rating = CASE
                    WHEN COALESCE(external_rating_count, 0) + COALESCE(user_review_count, 0) + :countDelta = 0 THEN 0
                    ELSE (COALESCE(external_average_rating, 0) * COALESCE(external_rating_count, 0) + COALESCE(user_rating_sum, 0) + :sumDelta)
                         / (COALESCE(external_rating_count, 0) + COALESCE(user_review_count, 0) + :countDelta)
                    END,
                average_rating = CASE
                    WHEN COALESCE(external_rating_count, 0) + COALESCE(user_review_count, 0) + :countDelta = 0 THEN 0
                    ELSE (COALESCE(external_average_rating, 0) * COALESCE(external_rating_count, 0) + COALESCE(user_rating_sum, 0) + :sumDelta)
                         / (COALESCE(external_rating_count, 0) + COALESCE(user_review_count, 0) + :countDelta)
                    END
            WHERE id = :wineId
            """, nativeQuery = true)
    int applyUserRatingChange(@Param("wineId") Long wineId,
                              @Param("countDelta") int countDelta,
                              @Param("sumDelta") int sumDelta,
                              @Param("addedRating") int addedRating,
                              @Param("removedRating") int removedRating);

    @Query(value =
            "SELECT * FROM wine w " +
            "WHERE w.embedding IS NOT NULL " +
            "AND (:wineType IS NULL OR w.type = :wineType) " +
            "ORDER BY w.embedding <=> cast(:queryEmbedding as vector) " +
            "LIMIT :limit", nativeQuery = true)
    List<Wine> findSimilarWinesByVector(
            @Param("queryEmbedding") String queryEmbedding,
            @Param("wineType") String wineType,
            @Param("limit") int limit
    );
}
