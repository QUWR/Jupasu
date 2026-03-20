package com.a505.jupasu.domain.reviews.controller;


import com.a505.jupasu.domain.reviews.dto.ReviewCreateRequest;
import com.a505.jupasu.domain.reviews.dto.ReviewResponse;
import com.a505.jupasu.domain.reviews.dto.ReviewUpdateRequest;
import com.a505.jupasu.domain.reviews.service.ReviewService;
import com.a505.jupasu.global.common.ApiResponse;
import jakarta.validation.Valid;
import com.a505.jupasu.global.exception.CustomException;
import com.a505.jupasu.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import com.a505.jupasu.global.security.auth.LoginUserCustom;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    private Long getUserId(LoginUserCustom loginUser) {
        if (loginUser == null) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        return loginUser.getUser().getId();
    }

    @GetMapping("/{wine_id}")
    public ApiResponse<Page<ReviewResponse>> getWineReviews(
            //TODO: 로그인한 사용자 확인 (Authentication 추가)
            @PathVariable("wine_id") Long wineId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.success("와인 리뷰 조회 성공", reviewService.getWineReviews(wineId, pageable));
    }

    @PostMapping("/{wine_id}/review")
    public ApiResponse<Long> createReview(
            @AuthenticationPrincipal LoginUserCustom loginUser,
            @PathVariable("wine_id") Long wineId,
            @Valid @RequestBody ReviewCreateRequest request
    ) {
        return ApiResponse.success("리뷰 생성 성공", reviewService.createReview(getUserId(loginUser), wineId, request));
    }

    @PatchMapping("/{wine_id}/review/{review_id}")
    public ApiResponse<Void> updateReview(
            @AuthenticationPrincipal LoginUserCustom loginUser,
            @PathVariable("wine_id") Long wineId,
            @PathVariable("review_id") Long reviewId,
            @Valid @RequestBody ReviewUpdateRequest request
    ) {
        reviewService.updateReview(getUserId(loginUser), wineId, reviewId, request);
        return ApiResponse.success("리뷰 수정 성공");
    }

    @DeleteMapping("/{wine_id}/review/{review_id}")
    public ApiResponse<Void> deleteReview(
            @AuthenticationPrincipal LoginUserCustom loginUser,
            @PathVariable("wine_id") Long wineId,
            @PathVariable("review_id") Long reviewId
    ) {
        reviewService.deleteReview(getUserId(loginUser), wineId, reviewId);
        return ApiResponse.success("리뷰 삭제 성공");
    }
}
