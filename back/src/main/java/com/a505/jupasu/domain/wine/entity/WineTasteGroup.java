package com.a505.jupasu.domain.wine.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 와인–향 다대다 매핑. (wine_id, taste_group_id) UNIQUE로 중복을 막고 와인별 조회 인덱스로도 쓴다.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_wine_taste_group_wine_taste", columnNames = {"wine_id", "taste_group_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WineTasteGroup {

    // 적재 시 JDBC 배치 INSERT를 위해 SEQUENCE 사용 (Wine 참고)
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "wine_taste_group_seq_generator")
    @SequenceGenerator(name = "wine_taste_group_seq_generator", sequenceName = "wine_taste_group_seq", allocationSize = 50)
    @Column(name = "wine_taste_group_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wine_id", nullable = false)
    private Wine wine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "taste_group_id", nullable = false)
    private TasteGroup tasteGroup;

    @Builder
    public WineTasteGroup(Wine wine, TasteGroup tasteGroup) {
        this.wine = wine;
        this.tasteGroup = tasteGroup;
    }
}