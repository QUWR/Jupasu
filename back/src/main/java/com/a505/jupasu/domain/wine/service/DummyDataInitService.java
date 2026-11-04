package com.a505.jupasu.domain.wine.service;

import com.a505.jupasu.domain.wine.entity.Food;
import com.a505.jupasu.domain.wine.entity.Wine;
import com.a505.jupasu.domain.wine.entity.WineFoodPairing;
import com.a505.jupasu.domain.wine.entity.WineType;
import com.a505.jupasu.domain.wine.entity.vo.Origin;
import com.a505.jupasu.domain.wine.entity.vo.TasteProfile;
import com.a505.jupasu.domain.wine.entity.vo.WinePriceAndRating;
import com.a505.jupasu.domain.wine.repository.FoodRepository;
import com.a505.jupasu.domain.wine.repository.WineFoodPairingRepository;
import com.a505.jupasu.domain.wine.repository.WineRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class DummyDataInitService {

    private final WineRepository wineRepository;
    private final FoodRepository foodRepository;
    private final WineFoodPairingRepository wineFoodPairingRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void generateDummyData() {
        long startTime = System.currentTimeMillis();
        log.info("🍷 더미 데이터 생성을 시작합니다...");

        // 1. 대분류 음식 데이터 10건 생성 및 저장
        List<String> foodNames = List.of(
                "소고기", "돼지고기", "닭고기", "해산물", "파스타",
                "치즈", "디저트", "샐러드", "피자", "아시안푸드"
        );
        // food.name은 UNIQUE이므로 이미 있는 음식(실데이터와 겹치는 소고기·파스타 등)은 재사용하고 없는 것만 만든다
        List<Food> foods = new ArrayList<>();
        for (String name : foodNames) {
            foods.add(foodRepository.findByName(name)
                    .orElseGet(() -> foodRepository.save(Food.builder().name(name).build())));
        }

        Random random = new Random();
        WineType[] wineTypes = WineType.values();

        List<Wine> wineBatch = new ArrayList<>();
        List<WineFoodPairing> pairingBatch = new ArrayList<>();

        int totalWines = 10000;
        int batchSize = 1000; // 메모리 초과 방지를 위해 1000건씩 끊어서 저장

        // 2. 와인 10,000건 생성 루프
        for (int i = 1; i <= totalWines; i++) {
            Wine wine = Wine.builder()
                    .nameKr("더미 와인 " + i)
                    .isRealNameKr(true)
                    .nameEn("Dummy Wine " + i)
                    .isRealNameEn(true)
                    .type(wineTypes[random.nextInt(wineTypes.length)])
                    .grapeVariety("Grape " + (random.nextInt(20) + 1))
                    .description("이 와인은 테스트를 위해 생성된 " + i + "번째 더미 데이터입니다.")
                    .alcoholDegree(10.0f + random.nextFloat() * 5.0f) // 10.0 ~ 15.0
                    .isRealAlcoholDegree(true)
                    .imageUrl("/images/default_wine.png")
                    // Origin 세팅
                    .origin(Origin.builder()
                            .country("Country " + (random.nextInt(10) + 1))
                            .isRealCountry(true)
                            .region("Region " + (random.nextInt(20) + 1))
                            .isRealRegion(true)
                            .winery("Winery " + (random.nextInt(50) + 1))
                            .isRealWinery(true)
                            .build())
                    // TasteProfile 세팅 (0.0 ~ 5.0)
                    .tasteProfile(TasteProfile.builder()
                            .sweetness(Math.round(random.nextFloat() * 5.0f * 10) / 10.0f)
                            .isRealSweetness(true)
                            .acidity(Math.round(random.nextFloat() * 5.0f * 10) / 10.0f)
                            .isRealAcidity(true)
                            .body(Math.round(random.nextFloat() * 5.0f * 10) / 10.0f)
                            .isRealBody(true)
                            .tannin(Math.round(random.nextFloat() * 5.0f * 10) / 10.0f)
                            .isRealTannin(true)
                            .build())
                    // 가격 및 평점 세팅
                    .priceAndRating(WinePriceAndRating.builder()
                            .price(10000 + random.nextInt(190000)) // 10,000 ~ 200,000
                            .isRealPrice(true)
                            .averageRating(Math.round(random.nextDouble() * 5.0 * 10) / 10.0)
                            .isRealRating(true)
                            .build())
                    .style("Style " + (random.nextInt(10) + 1))
                    .build();

            wineBatch.add(wine);

            // 3. 1,000건이 찰 때마다 DB에 털어내기 (Batch 처리)
            if (i % batchSize == 0) {
                // 와인 저장 (ID가 발급됨)
                wineRepository.saveAll(wineBatch);

                // 저장된 와인에 대해 각각 랜덤 음식 연관관계 생성 (1~3개)
                for (Wine savedWine : wineBatch) {
                    int pairingCount = 1 + random.nextInt(3);

                    // 음식 리스트를 섞은 후 필요한 개수만큼 뽑아서 중복 방지
                    List<Food> shuffledFoods = new ArrayList<>(foods);
                    Collections.shuffle(shuffledFoods);

                    for (int j = 0; j < pairingCount; j++) {
                        pairingBatch.add(WineFoodPairing.builder()
                                .wine(savedWine)
                                .food(shuffledFoods.get(j))
                                .build());
                    }
                }

                // 연관관계 저장
                wineFoodPairingRepository.saveAll(pairingBatch);
                log.info("👉 {}건 저장 완료...", i);

                // 배치로 DB에 반영한 뒤 영속성 컨텍스트를 비워 엔티티가 쌓이지 않게 한다
                entityManager.flush();
                entityManager.clear();
                wineBatch.clear();
                pairingBatch.clear();
            }
        }

        long endTime = System.currentTimeMillis();
        log.info("🎉 더미 데이터 생성 완료! 소요 시간: {}ms", (endTime - startTime));
    }
}