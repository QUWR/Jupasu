package com.a505.jupasu.domain.wine.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 와인–음식 다대다 매핑.
 * (wine_id, food_id) UNIQUE 제약은 중복 매핑을 막고, 그 인덱스가 와인별 음식 조회(WHERE wine_id = ?)에도 쓰인다.
 * (PostgreSQL은 FK에 인덱스를 자동으로 만들지 않는다)
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_wine_food_pairing_wine_food", columnNames = {"wine_id", "food_id"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor // Builder를 위해 추가
public class WineFoodPairing {
    // 적재 시 JDBC 배치 INSERT를 위해 SEQUENCE 사용 (Wine 참고)
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "wine_food_pairing_seq_generator")
    @SequenceGenerator(name = "wine_food_pairing_seq_generator", sequenceName = "wine_food_pairing_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wine_id", nullable = false)
    private Wine wine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_id", nullable = false)
    private Food food;
}
