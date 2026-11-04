package com.a505.jupasu.domain.scrap.entity;

import com.a505.jupasu.domain.user.entity.User;
import com.a505.jupasu.domain.wine.entity.Wine;
import com.a505.jupasu.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;


@Entity
// 같은 와인을 두 번 찜하지 않도록 (user_id, wine_id) UNIQUE (연타·재시도 시 중복 방지)
@Table(name = "wine_scrap",
        uniqueConstraints = @UniqueConstraint(name = "uk_wine_scrap_user_wine", columnNames = {"user_id", "wine_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WineScrap extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wine_id")
    private Wine wine;

    @Builder
    public WineScrap(User user, Wine wine){
        this.user = user;
        this.wine = wine;
    }
}
