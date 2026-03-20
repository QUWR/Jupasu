package com.a505.jupasu.domain.wine.dto;

import com.a505.jupasu.domain.wine.entity.Wine;
import lombok.Builder;

@Builder
public record WineRecommendationItem(
        Long wineId,
        String nameKr,
        String imageUrl,
        Integer matchRate,
        String recommendationReason,
        Float sweetness,
        Float acidity,
        Float body,
        Float tannin,
        String style
) {
    public static WineRecommendationItem of(Wine wine, Integer matchRate, String recommendationReason) {
        var tp = wine.getTasteProfile();
        return WineRecommendationItem.builder()
                .wineId(wine.getId())
                .nameKr(wine.getNameKr())
                .imageUrl(wine.getImageUrl())
                .matchRate(50 + matchRate/2)
                .recommendationReason(recommendationReason)
                .sweetness(tp != null && Boolean.TRUE.equals(tp.getIsRealSweetness()) ? tp.getSweetness() : null)
                .acidity(tp != null && Boolean.TRUE.equals(tp.getIsRealAcidity()) ? tp.getAcidity() : null)
                .body(tp != null && Boolean.TRUE.equals(tp.getIsRealBody()) ? tp.getBody() : null)
                .tannin(tp != null && Boolean.TRUE.equals(tp.getIsRealTannin()) ? tp.getTannin() : null)
                .style(wine.getStyle())
                .build();
    }

    public static WineRecommendationItem of(Wine wine, Integer matchRate){
        var tp = wine.getTasteProfile();
        return WineRecommendationItem.builder()
                .wineId(wine.getId())
                .nameKr(wine.getNameKr())
                .imageUrl(wine.getImageUrl())
                .matchRate(50 + matchRate/2)
                .recommendationReason("")
                .sweetness(tp != null && Boolean.TRUE.equals(tp.getIsRealSweetness()) ? tp.getSweetness() : null)
                .acidity(tp != null && Boolean.TRUE.equals(tp.getIsRealAcidity()) ? tp.getAcidity() : null)
                .body(tp != null && Boolean.TRUE.equals(tp.getIsRealBody()) ? tp.getBody() : null)
                .tannin(tp != null && Boolean.TRUE.equals(tp.getIsRealTannin()) ? tp.getTannin() : null)
                .build();
    }
}
