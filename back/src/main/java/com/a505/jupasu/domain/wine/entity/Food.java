package com.a505.jupasu.domain.wine.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_food_name", columnNames = "name"))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor // Builder를 위해 추가
public class Food {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 적재 코드가 이름으로 음식을 찾으므로(findByName) 같은 이름이 두 번 생기면 안 된다
    @Column(nullable = false)
    private String name;
}