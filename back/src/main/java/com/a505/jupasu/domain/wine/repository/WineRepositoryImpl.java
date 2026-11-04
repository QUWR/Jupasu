package com.a505.jupasu.domain.wine.repository;

import com.a505.jupasu.domain.wine.dto.WineSearchCondition;
import com.a505.jupasu.domain.wine.entity.Wine;
import com.a505.jupasu.domain.wine.entity.WineType;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static com.a505.jupasu.domain.wine.entity.QWine.wine;

@RequiredArgsConstructor
public class WineRepositoryImpl implements WineRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Wine> searchWines(WineSearchCondition condition, List<Long> matchingIds, Pageable pageable) {

        BooleanBuilder builder = new BooleanBuilder();

        boolean hasKeyword = StringUtils.hasText(condition.keyword());

        if (hasKeyword) {
            if (matchingIds != null) {
                if (matchingIds.isEmpty()) {
                    return new PageImpl<>(List.of(), pageable, 0);
                }
                builder.and(wine.id.in(matchingIds));
            } else {
                builder.and(keywordSimilarity(condition.keyword()));
            }
        }

        builder.and(typeIn(condition.types()));
        builder.and(priceGoe(condition.minPrice()));
        builder.and(priceLoe(condition.maxPrice()));
        builder.and(ratingGoe(condition.minRate()));
        builder.and(excludeZeroPriceWhenPriceAsc(pageable));


        OrderSpecifier<?>[] orderSpecifiers = getOrderSpecifier(pageable);

        JPAQuery<Wine> query = queryFactory
                .selectFrom(wine)
                .where(builder)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize());

        if (hasKeyword && pageable.getSort().isEmpty()) {
            NumberExpression<Double> similarity = Expressions.numberTemplate(
                    Double.class,
                    "function('word_similarity', {0}, {1})",
                    condition.keyword(),
                    wine.nameKr
            );
            query.orderBy(similarity.desc(), wine.id.desc());
        } else {
            query.orderBy(orderSpecifiers);
        }

        List<Wine> content = query.fetch();

        Long total = queryFactory
                .select(wine.count())
                .from(wine)
                .where(builder)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    //=============================================================
    // 동적 필터링을 위한 쿼리 메소드 모음
    //=============================================================
    /**
     * 와인 이름에 키워드가 포함되어있는지 검사
     */
    private BooleanExpression keywordSimilarity(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        // PostgreSQL word_similarity 함수 호출
        NumberExpression<Double> similarity = Expressions.numberTemplate(Double.class,
                "function('word_similarity', {0}, {1})", keyword, wine.nameKr);

        // 유사도 점수 0.1 이상인 것만 필터링 (기존 <% 연산자와 동일한 역할)
        return similarity.gt(0.1);
    }

    /**
     * 와인 종류가 일치하는지 검사
     */
    private BooleanExpression typeIn(List<WineType> types) {
        return types != null && !types.isEmpty() ? wine.type.in(types) : null;
    }

    /**
     * 와인 가격 범위 검사
     */
    private BooleanExpression priceGoe(Integer minPrice) {
        return minPrice != null ? wine.priceAndRating.price.goe(minPrice) : null;
    }

    private BooleanExpression priceLoe(Integer maxPrice) {
        return maxPrice != null ? wine.priceAndRating.price.loe(maxPrice) : null;
    }

    /**
     * 와인 평균 평점 범위 검사
     */
    private BooleanExpression ratingGoe(Double minRate) {
        return minRate != null ? wine.priceAndRating.averageRating.goe(minRate) : null;
    }


    //===================================================
    //정렬을 위한 메서드
    //===================================================

    /**
     * Spring Data JPA 의 Pageable객체 안에 있는 정렬 정보를
     * QueryDSL 이 사용할 수 있도록 OrderSpecifier 로 변환
     *
     * @param pageable 페이지 정렬 정보
     * @return
     */
    private OrderSpecifier<?>[] getOrderSpecifier(Pageable pageable) {
        List<OrderSpecifier<?>> orderSpecifiers = new ArrayList<>();

        if(pageable.getSort().isEmpty()){
            return new OrderSpecifier[]{new OrderSpecifier<>(Order.DESC, wine.id)};
        }
        for(Sort.Order order : pageable.getSort()) {
            Order direction = order.getDirection().isAscending() ? Order.ASC : Order.DESC;

            switch (order.getProperty()) {
                case "price":
                    orderSpecifiers.add(new OrderSpecifier<>(
                            direction,
                            wine.priceAndRating.price,
                            OrderSpecifier.NullHandling.NullsLast
                    ));
                    break;
                case "rating":
                    orderSpecifiers.add(new OrderSpecifier<>(
                            direction,
                            wine.priceAndRating.averageRating,
                            OrderSpecifier.NullHandling.NullsLast));
                    break;
                default:
                    orderSpecifiers.add(new OrderSpecifier<>(direction, wine.id));
                    break;
            }
        }
        // 가격·평점처럼 같은 값이 많은 기준만으로 정렬하면 같은 값끼리의 순서가 매번 달라질 수 있어
        // 페이지를 넘길 때 같은 와인이 두 번 나오거나 빠진다. 유일한 id를 마지막 정렬 기준으로 붙인다.
        boolean sortedById = pageable.getSort().stream().anyMatch(order -> "id".equals(order.getProperty()));
        if (!sortedById) {
            orderSpecifiers.add(new OrderSpecifier<>(Order.DESC, wine.id));
        }
        return orderSpecifiers.toArray(new OrderSpecifier[0]);
    }


    /**
     * 가격 오름차순 정렬시 0원 제외
     * @param pageable
     * @return
     */
    private BooleanExpression excludeZeroPriceWhenPriceAsc(Pageable pageable) {
        if (pageable == null || pageable.getSort().isEmpty()) {
            return null;
        }

        for (Sort.Order order : pageable.getSort()) {
            if ("price".equals(order.getProperty()) && order.isAscending()) {
                return wine.priceAndRating.price.gt(0);
            }
        }

        return null;
    }
}
