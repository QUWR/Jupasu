package com.a505.jupasu.domain.wine.entity.vo;

import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class RatingSummary {

    @Builder.Default
    private Double externalAverageRating = 0.0;

    @Builder.Default
    private Integer externalRatingCount = 0;

    // 외부 분포는 저장만 함. 평균 계산의 기준으로는 쓰지 않음.
    @Builder.Default private Integer externalStar1Count = 0;
    @Builder.Default private Integer externalStar2Count = 0;
    @Builder.Default private Integer externalStar3Count = 0;
    @Builder.Default private Integer externalStar4Count = 0;
    @Builder.Default private Integer externalStar5Count = 0;

    @Builder.Default
    private Integer userReviewCount = 0;

    @Builder.Default
    private Integer userRatingSum = 0;

    @Builder.Default private Integer userStar1Count = 0;
    @Builder.Default private Integer userStar2Count = 0;
    @Builder.Default private Integer userStar3Count = 0;
    @Builder.Default private Integer userStar4Count = 0;
    @Builder.Default private Integer userStar5Count = 0;

    @Builder.Default
    private Integer displayReviewCount = 0;

    @Builder.Default
    private Double displayAverageRating = 0.0;

    public void initializeExternal(
            double externalAverageRating,
            int externalRatingCount,
            int star1,
            int star2,
            int star3,
            int star4,
            int star5
    ) {
        this.externalAverageRating = externalAverageRating;
        this.externalRatingCount = externalRatingCount;
        this.externalStar1Count = star1;
        this.externalStar2Count = star2;
        this.externalStar3Count = star3;
        this.externalStar4Count = star4;
        this.externalStar5Count = star5;
        refresh();
    }

    // 사용자 리뷰 반영(addUserRating 등)은 동시성 문제(Lost Update)로 제거하고
    // WineRepository.applyUserRatingChange의 원자적 UPDATE로 옮겼다. 아래 refresh()와 같은 공식을 SQL로 계산한다.

    public int getDisplayStar5Count() {
        return externalStar5Count + userStar5Count;
    }

    public int getDisplayStar4Count() {
        return externalStar4Count + userStar4Count;
    }

    public int getDisplayStar3Count() {
        return externalStar3Count + userStar3Count;
    }

    public int getDisplayStar2Count() {
        return externalStar2Count + userStar2Count;
    }

    public int getDisplayStar1Count() {
        return externalStar1Count + userStar1Count;
    }

    private void refresh() {
        this.displayReviewCount = this.externalRatingCount + this.userReviewCount;

        double externalScoreSum = this.externalAverageRating * this.externalRatingCount;
        double totalScore = externalScoreSum + this.userRatingSum;

        this.displayAverageRating =
                this.displayReviewCount == 0 ? 0.0 : totalScore / this.displayReviewCount;
    }
}
