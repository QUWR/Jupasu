package com.a505.jupasu.domain.reviews.entity;

import com.a505.jupasu.domain.user.entity.User;
import com.a505.jupasu.domain.wine.entity.Wine;
import com.a505.jupasu.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 한 사용자는 와인 하나에 리뷰를 하나만 쓸 수 있다.
 * exists 확인만으로는 동시 요청(확인 후 저장 사이의 경쟁)을 막을 수 없어 DB UNIQUE 제약을 최종 방어선으로 둔다.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_review_user_wine", columnNames = {"user_id", "wine_id"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Review extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wine_id", nullable = false)
    private Wine wine;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Integer rating;

    public void updateReview(Integer rating, String content) {
        if (rating != null) this.rating = rating;
        if (content != null) this.content = content;
    }
}






