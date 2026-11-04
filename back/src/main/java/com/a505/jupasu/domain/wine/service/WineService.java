package com.a505.jupasu.domain.wine.service;

import com.a505.jupasu.domain.preference.dto.response.PreferenceResponse;
import com.a505.jupasu.domain.preference.entity.Preference;
import com.a505.jupasu.domain.preference.repository.PreferenceRepository;
import com.a505.jupasu.domain.user.entity.User;
import com.a505.jupasu.domain.wine.dto.SimilarWineResponse;
import com.a505.jupasu.domain.wine.dto.WineDetailResponse;
import com.a505.jupasu.domain.wine.dto.WineSearchCondition;
import com.a505.jupasu.domain.wine.dto.WineSearchResponse;
import com.a505.jupasu.domain.wine.entity.Wine;
import com.a505.jupasu.domain.wine.repository.WineFoodPairingRepository;
import com.a505.jupasu.domain.wine.repository.WineRepository;
import com.a505.jupasu.domain.wine.repository.WineTasteGroupRepository;
import com.a505.jupasu.domain.wine.util.WineRecommendationCalculator;
import com.a505.jupasu.global.exception.CustomException;
import com.a505.jupasu.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WineService {

    private final WineRepository wineRepository;
    private final PreferenceRepository preferenceRepository;
    private final WineFoodPairingRepository wineFoodPairingRepository;
    private final WineTasteGroupRepository wineTasteGroupRepository;
    private final WineRecommendationCalculator wineRecommendationCalculator;
    private final WineSearchService wineSearchService;

    public Page<WineSearchResponse> searchWines(WineSearchCondition condition, Pageable pageable) {
        List<Long> matchingIds = null;

        // ⭐️ getKeyword() -> keyword() 로 변경
        if (StringUtils.hasText(condition.keyword())) {
            matchingIds = wineRepository.findMatchingIdsByKeyword(condition.keyword());

            if (matchingIds.isEmpty()) {
                return Page.empty(pageable);
            }
        }

        Page<Wine> wines = wineRepository.searchWines(condition, matchingIds, pageable);
        return wines.map(WineSearchResponse::from);
    }

    /**
     * 와인 상세 조회.
     * 로그인 사용자는 JWT 필터가 이미 DB에서 조회했으므로 다시 조회하지 않고, 취향도 한 번만 조회해 적중률 계산에 넘긴다.
     */
    public WineDetailResponse getWineDetail(User user, Long wineId) {
        // 1. 와인 정보 조회
        Wine wine = wineRepository.findById(wineId)
                .orElseThrow(() -> new CustomException(ErrorCode.WINE_NOT_FOUND));

        // 2. 사용자 취향 (적중률 계산에도 재사용)
        Preference preference = preferenceRepository.findByUserId(user.getId()).orElse(null);

        // 3. 페어링 푸드 목록 조회
        List<String> pairingFoods = wineFoodPairingRepository.findFoodNamesByWineId(wineId);

        List<String> tasteGroup = wineTasteGroupRepository.findTasteNamesByWineId(wineId);
        // 4. 취향 적중률 계산
        int matchRate = wineRecommendationCalculator.getMatch(user, wine, preference);

        //유사한 와인 3개 추천
        List<SimilarWineResponse> similarWines = wineRepository.findSimilarTastes(wine, 3).stream()
                .filter(similarWine -> !similarWine.getId().equals(wineId))
                .map(SimilarWineResponse::from)
                .toList();

        return WineDetailResponse.of(wine, matchRate, pairingFoods, tasteGroup, preference, similarWines);
    }
}