package com.a505.jupasu.domain.reviews.repository;

import com.a505.jupasu.domain.reviews.entity.Review;
import com.a505.jupasu.domain.user.entity.User;
import com.a505.jupasu.domain.wine.entity.Wine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    @EntityGraph(attributePaths = {"user"})
    Page<Review> findAllByWineId(Long wineId, Pageable pageable);

    boolean existsByUserAndWine(User user, Wine wine);

    boolean existsByUserIdAndWineId(Long userId, Long wineId);

    // 탈퇴하는 사용자의 모든 리뷰를 삭제
    void deleteByUser(User user);

    /** 탈퇴 시 와인 평점 집계에서 뺄 [와인 ID, 별점] 목록 (엔티티를 읽지 않고 필요한 값만 조회) */
    @Query("select r.wine.id, r.rating from Review r where r.user.id = :userId")
    List<Object[]> findWineIdAndRatingByUserId(@Param("userId") Long userId);

    // 특정 유저가 작성한 와인 리뷰 조회
    @Query("select r from Review r join fetch r.wine w where r.user = :user order by r.createdAt desc")
    List<Review> findAllByUserWithWine(@Param("user") User user);

    @Query(
            value = "select r from Review r join fetch r.wine where r.user = :user order by r.createdAt desc",
            countQuery = "select count(r) from Review r where r.user = :user"
    )
    Page<Review> findAllByUserWithWine(@Param("user") User user, Pageable pageable);

    // 유저가 작성한 리뷰 수 조회
    long countByUser(User user);

    // 여러 유저의 리뷰 수를 한 번에 조회
    @Query("select r.user.id, count(r) from Review r where r.user.id in :userIds group by r.user.id")
    List<Object[]> countReviewsByUserIds(@Param("userIds") List<Long> userIds);
}
