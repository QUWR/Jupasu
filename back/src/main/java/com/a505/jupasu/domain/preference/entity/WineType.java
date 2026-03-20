package com.a505.jupasu.domain.preference.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 와인 종류 Enum
 */
@Getter
@RequiredArgsConstructor
public enum WineType {
    RED("레드"),
    WHITE("화이트"),
    ROSE("로제"),
    SPARKLING("스파클링"),
    DESSERT("디저트"),
    FORTIFIED("주정강화");;

    private final String description;
}
