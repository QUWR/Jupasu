package com.a505.jupasu.domain.wine;

import com.a505.jupasu.domain.wine.entity.Wine;
import com.a505.jupasu.domain.wine.entity.WineType;
import com.a505.jupasu.domain.wine.repository.WineRepository;
import com.a505.jupasu.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class WineEmbeddingPersistenceTest extends IntegrationTestSupport {

    @Autowired
    private WineRepository wineRepository;

    @Test
    @DisplayName("임베딩이 없는 와인도 저장된다 (null이 bytea로 전달되어 vector 변환에 실패하던 문제)")
    void saveWine_withoutEmbedding() {
        Wine saved = wineRepository.save(Wine.builder().nameKr("임베딩없는와인").nameEn("No Embedding Wine").type(WineType.RED).build());
        trackWine(saved.getId());

        Wine reloaded = wineRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getEmbedding()).isNull();
    }

    @Test
    @DisplayName("1536차원 임베딩을 저장하고 다시 읽으면 같은 값이다")
    void saveWine_withEmbedding_roundTrip() {
        float[] embedding = new float[1536];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = (i % 100) / 100f;
        }
        Wine saved = wineRepository.save(Wine.builder().nameKr("임베딩와인").nameEn("Embedding Wine").type(WineType.RED).embedding(embedding).build());
        trackWine(saved.getId());

        Wine reloaded = wineRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getEmbedding()).containsExactly(embedding);
    }
}
