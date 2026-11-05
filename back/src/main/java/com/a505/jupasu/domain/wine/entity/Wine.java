package com.a505.jupasu.domain.wine.entity;

import com.a505.jupasu.domain.wine.entity.vo.Origin;
import com.a505.jupasu.domain.wine.entity.vo.RatingSummary;
import com.a505.jupasu.domain.wine.entity.vo.TasteProfile;
import com.a505.jupasu.domain.wine.entity.vo.WinePriceAndRating;
import com.a505.jupasu.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Getter
@Builder
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Wine extends BaseEntity {

    // IDENTITY는 INSERT 후에야 ID를 알 수 있어 Hibernate가 JDBC 배치를 끈다.
    // 데이터 적재 시 대량 INSERT를 묶어 보내기 위해 SEQUENCE(50개씩 미리 할당)를 사용한다.
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "wine_seq_generator")
    @SequenceGenerator(name = "wine_seq_generator", sequenceName = "wine_seq", allocationSize = 50)
    private Long id;

    private String nameKr;
    @Builder.Default private Boolean isRealNameKr = false;

    private String nameEn;
    @Builder.Default private Boolean isRealNameEn = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WineType type;

    @Embedded
    private Origin origin;

    @Embedded
    private TasteProfile tasteProfile;

    @Embedded
    private WinePriceAndRating priceAndRating;

    @Embedded
    @Builder.Default
    private RatingSummary ratingSummary = RatingSummary.builder().build();

    private String grapeVariety;

    @Builder.Default
    private Float alcoholDegree = 0.0f;
    @Builder.Default
    private Boolean isRealAlcoholDegree = false;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String style;

    private String imageUrl;

    @Column(columnDefinition = "TEXT")
    private String richDescription;

    private String embeddingModel;

    private OffsetDateTime embeddingGeneratedAt;

    // VectorConverter가 float[]를 "[0.1, 0.2, ...]" 문자열로 바꾸고, DB에서 ?::vector로 변환한다.
    // @JdbcTypeCode(SqlTypes.OTHER)를 함께 쓰면 값이 bytea로 전달되어 "cannot cast type bytea to vector"로
    // 모든 와인 INSERT가 실패했으므로 제거했다 (문자열로 전달 → vector로 명시적 변환).
    @org.hibernate.annotations.ColumnTransformer(write = "?::vector")
    @jakarta.persistence.Convert(converter = com.a505.jupasu.domain.wine.util.VectorConverter.class)
    @Column(columnDefinition = "vector(1536)")
    private float[] embedding;

    public void initializeExternalRatings(
            double externalAverageRating,
            int externalRatingCount,
            int star1,
            int star2,
            int star3,
            int star4,
            int star5
    ) {
        this.ratingSummary.initializeExternal(
                externalAverageRating, externalRatingCount,
                star1, star2, star3, star4, star5
        );
        this.priceAndRating.updateAverageRating(this.ratingSummary.getDisplayAverageRating());
    }

    // 사용자 리뷰 별점 반영(작성·수정·삭제)은 WineRepository.applyUserRatingChange의 원자적 UPDATE로 처리한다.
    // 엔티티에서 읽고 계산해 저장하면 동시 리뷰 작성 시 Lost Update가 발생하기 때문이다.
}
