package com.a505.jupasu.domain.wine.controller;

import com.a505.jupasu.domain.preference.entity.DrinkingSituation;
import com.a505.jupasu.domain.wine.dto.FoodWineRecommendationRequest;
import com.a505.jupasu.domain.wine.dto.FoodWineRecommendationResponse;
import com.a505.jupasu.domain.wine.dto.InternalFoodWineRecommendationRequest;
import com.a505.jupasu.domain.wine.dto.WineDetailResponse;
import com.a505.jupasu.domain.wine.dto.WineQuickRecommendResponse;
import com.a505.jupasu.domain.wine.dto.WineRecommendationItem;
import com.a505.jupasu.domain.wine.dto.WineSearchCondition;
import com.a505.jupasu.domain.wine.dto.WineSearchResponse;
import com.a505.jupasu.domain.wine.dto.response.HybridSearchResponse;
import com.a505.jupasu.domain.wine.entity.Wine;
import com.a505.jupasu.domain.wine.repository.WineRepository;
import com.a505.jupasu.domain.wine.service.FoodWineRecommendationService;
import com.a505.jupasu.domain.wine.service.WineSearchService;
import com.a505.jupasu.domain.user.entity.User;
import com.a505.jupasu.domain.user.repository.UserRepository;
import com.a505.jupasu.domain.wine.dto.*;

import com.a505.jupasu.domain.wine.service.WineService;
import com.a505.jupasu.domain.wine.util.WineRecommendationCalculator;
import com.a505.jupasu.global.common.ApiResponse;
import com.a505.jupasu.global.exception.CustomException;
import com.a505.jupasu.global.exception.ErrorCode;
import com.a505.jupasu.global.security.auth.LoginUserCustom;
import jakarta.validation.Valid;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wines")
@RequiredArgsConstructor
public class WineController {

    private static final Set<DrinkingSituation> SUPPORTED_SITUATIONS =
            EnumSet.of(DrinkingSituation.ALONE, DrinkingSituation.DATE, DrinkingSituation.PARTY);

    private final WineService wineService;
    private final WineSearchService wineSearchService;
    private final WineRecommendationCalculator wineRecommendationCalculator;
    private final WineRepository wineRepository;
    private final FoodWineRecommendationService foodWineRecommendationService;

    @Value("${ai.server.internal-api-key}")
    private String internalApiKey;
    private final UserRepository userRepository;

    @GetMapping
    public ApiResponse<Page<WineSearchResponse>> searchWines(
            @AuthenticationPrincipal LoginUserCustom user,
            @ModelAttribute WineSearchCondition condition,
            // 기본 정렬을 지정하면 정렬을 고르지 않아도 Sort가 항상 채워져, 키워드 검색의 '유사도순' 분기가 실행되지 않는다.
            // 정렬 미지정 시 기본 순서는 WineRepositoryImpl이 정한다 (키워드 있음: 유사도순 / 없음: 최신순)
            @PageableDefault(size = 10) Pageable pageable
    ) {
        Page<WineSearchResponse> response = wineService.searchWines(condition, pageable);

        return ApiResponse.success("와인 목록을 조회했습니다.", response);
    }

    @GetMapping("/advanced-search")
    public ApiResponse<HybridSearchResponse> searchWinesAdvanced(
            @RequestParam(value = "q", defaultValue = "") String query
    ) {
        if (query.isBlank()) {
            return ApiResponse.success(
                    "검색어가 비어 있어 검색 결과를 빈 목록으로 반환합니다.",
                    HybridSearchResponse.builder()
                            .recommendations(Collections.emptyList())
                            .build()
            );
        }

        HybridSearchResponse response = wineSearchService.searchHybrid(query);
        return ApiResponse.success("통합 검색 결과를 조회했습니다.", response);
    }

    @GetMapping("/test-images")
    public ApiResponse<List<String>> getRandomImages() {
        List<String> images = wineRepository.findAll().stream()
                .map(Wine::getImageUrl)
                .collect(Collectors.toList());
        Collections.shuffle(images);
        return ApiResponse.success("테스트용 와인 이미지 목록을 조회했습니다.", images.stream().limit(100).toList());
    }

    @GetMapping("/{wine_id}")
    public ApiResponse<WineDetailResponse> getWineDetail(
            @AuthenticationPrincipal LoginUserCustom user,
            @PathVariable("wine_id") Long wineId
    ) {
        WineDetailResponse response = wineService.getWineDetail(user.getUser(), wineId);
        return ApiResponse.success("와인 상세 정보를 조회했습니다.", response);
    }

    @GetMapping("/quick")
    public ApiResponse<WineQuickRecommendResponse> getQuickWines(
            @AuthenticationPrincipal LoginUserCustom loginUser,
            @RequestParam(required = false) DrinkingSituation situation
    ) {
        if (situation != null && !SUPPORTED_SITUATIONS.contains(situation)) {
            throw new CustomException(ErrorCode.SITUATION_NOT_FOUND);
        }

        if (situation == null) {
            WineQuickRecommendResponse response = wineRecommendationCalculator.allQuickLists(loginUser.getUser());
            return ApiResponse.success("빠른 추천 와인 목록을 조회했습니다.", response);
        }

        List<WineRecommendationItem> wineList = wineRecommendationCalculator.quickList(loginUser.getUser(), situation);
        WineQuickRecommendResponse response = WineQuickRecommendResponse.of(
                List.of(),
                List.of(WineQuickRecommendResponse.SituationResult.of(situation, wineList))
        );

        return ApiResponse.success("상황별 빠른 추천 와인 목록을 조회했습니다.", response);
    }

    @PostMapping("/recommendations/food")
    public ApiResponse<FoodWineRecommendationResponse> recommendWineByFood(
            @AuthenticationPrincipal LoginUserCustom loginUser,
            @Valid @RequestBody FoodWineRecommendationRequest request,
            @RequestParam String queryVector
    ) {
        FoodWineRecommendationResponse response = foodWineRecommendationService.recommend(
                loginUser.getUser().getId(),
                request,
                queryVector
        );

        return ApiResponse.success("음식 기반 와인 추천 결과를 조회했습니다.", response);
    }

    @PostMapping("/recommendations/food/internal")
    public ApiResponse<FoodWineRecommendationResponse> recommendWineByFoodInternal(
            @RequestHeader("X-Internal-Api-Key") String internalApiKeyHeader,
            @RequestParam Long userId,
            @Valid @RequestBody InternalFoodWineRecommendationRequest request
    ) {
        if (!internalApiKey.equals(internalApiKeyHeader)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "invalid internal api key");
        }

        try {
            FoodWineRecommendationResponse response = foodWineRecommendationService.recommend(
                    userId,
                    request.toFoodRequest(),
                    request.queryVector()
            );

            return ApiResponse.success("음식 기반 와인 추천 결과를 조회했습니다.", response);
        } catch (CustomException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CustomException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }
    /**
     * [AI 하이브리드 RAG 연동 전용 API]
     * 파이썬 AI 모듈이 정성/맥락(Context) 기반으로 추출한 와인 ID 30개를
     * Java 백엔드의 베이지안 수학 정량식으로 최종 Re-ranking 하여 Top 1를 반환합니다.
     */
    @PostMapping("/recommend/rag")
    public ApiResponse<WineQuickRecommendResponse> recommendByRag(
            @RequestBody RagRecommendRequest request) {

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 1. 후보군 와인 ID 리스트 준비
        List<Long> candidateIds = request.getCandidateWineIds();

        // 2. 만약 AI 서버에서 벡터를 보내줬다면, 백엔드에서 직접 pgvector 검색 수행 (권장 구조)
        if (request.getQueryVector() != null && !request.getQueryVector().isBlank()) {
            List<Wine> similarWines = wineRepository.findSimilarWinesByVector(
                    request.getQueryVector(),
                    request.getWineType(),
                    30 // 상위 30개 추출
            );
            candidateIds = similarWines.stream()
                    .map(Wine::getId)
                    .collect(Collectors.toList());
        }

        if (candidateIds == null || candidateIds.isEmpty()) {
            return ApiResponse.success("추천할 수 있는 후보 와인이 없습니다.", 
                WineQuickRecommendResponse.of(List.of(), List.of()));
        }

        // 3. 친구 목록 조회 (없으면 빈 리스트)
        List<User> friends = (request.getFriendIds() == null || request.getFriendIds().isEmpty())
                ? List.of()
                : userRepository.findAllById(request.getFriendIds());

        // 4. 베이지안 수학 정량식으로 최종 Re-ranking
        WineQuickRecommendResponse response = wineRecommendationCalculator.ragReRank(
                user,
                friends,
                candidateIds
        );

        return ApiResponse.success("RAG 기반 와인 추천 및 재정렬 성공", response);
    }
}
