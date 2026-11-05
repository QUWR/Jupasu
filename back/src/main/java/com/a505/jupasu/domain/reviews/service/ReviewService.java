package com.a505.jupasu.domain.reviews.service;


import com.a505.jupasu.domain.preference.repository.PreferenceRepository;
import com.a505.jupasu.domain.reviews.dto.MyPageReviewResponse;
import com.a505.jupasu.domain.reviews.dto.ReviewCreateRequest;
import com.a505.jupasu.domain.reviews.dto.ReviewResponse;
import com.a505.jupasu.domain.reviews.dto.ReviewUpdateRequest;
import com.a505.jupasu.domain.reviews.entity.Review;
import com.a505.jupasu.domain.reviews.repository.ReviewRepository;
import com.a505.jupasu.domain.user.entity.User;
import com.a505.jupasu.domain.user.repository.UserRepository;
import com.a505.jupasu.domain.wine.entity.Wine;
import com.a505.jupasu.domain.wine.repository.WineRepository;
import com.a505.jupasu.global.exception.CustomException;
import com.a505.jupasu.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final WineRepository wineRepository;
    private final UserRepository userRepository;
    private final PreferenceRepository preferenceRepository;

    /**
     * 와인별 리뷰 조회
     */
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getWineReviews(Long wineId, Pageable pageable){
        if(!wineRepository.existsById(wineId)){
            throw new CustomException(ErrorCode.WINE_NOT_FOUND);
        }

        return reviewRepository.findAllByWineId(wineId, pageable)
                .map(ReviewResponse::from);
    }

    /**
     * 리뷰 등록
     * <p>
     * 1) 와인 평점 집계를 원자적 UPDATE로 먼저 반영한다. 갱신된 행이 0이면 와인이 없으므로 404.
     *    (getReferenceById는 존재를 확인하지 않아, 없는 와인이면 INSERT 때 FK 위반 → 500이 나던 문제)
     *    UPDATE가 와인 행을 잠그므로 같은 와인에 대한 동시 리뷰는 순서대로 반영되고 덮어써지지 않는다.
     * 2) 연관관계는 식별자만 가진 프록시로 연결한다 (User·Wine SELECT 없음).
     * 3) 동시에 같은 리뷰가 들어와 UNIQUE(user_id, wine_id)에 걸리면 409로 바꾼다.
     *    예외를 다시 던지므로 트랜잭션 전체(평점 반영 포함)가 롤백된다.
     */
    public Long createReview(Long userId, Long wineId, ReviewCreateRequest request) {
        if (reviewRepository.existsByUserIdAndWineId(userId, wineId)) {
            throw new CustomException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        int rating = request.getRating();
        if (wineRepository.applyUserRatingChange(wineId, 1, rating, rating, 0) == 0) {
            throw new CustomException(ErrorCode.WINE_NOT_FOUND);
        }

        User user = userRepository.getReferenceById(userId);
        Wine wine = wineRepository.getReferenceById(wineId);

        Review review = Review.builder()
                .user(user)
                .wine(wine)
                .rating(rating)
                // review.content는 DB에서 NOT NULL이므로 내용 없이 별점만 남기는 경우 빈 문자열로 저장
                .content(request.getContent() != null ? request.getContent() : "")
                .build();

        try {
            reviewRepository.save(review);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        // 리뷰 수에 따라 캐릭터 이미지가 바뀌는 로직이 엔티티에 있어 User는 엔티티로 갱신한다 (이 시점에 User SELECT 1회)
        user.increaseReviewCount();

        preferenceRepository.findByUserId(userId).ifPresent(preference ->
                preference.markReportAsOutdated()
        );

        return review.getId();
    }

    /**
     * 리뷰 수정
     */
    public void updateReview(Long userId, Long wineId, Long reviewId, ReviewUpdateRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_NOT_FOUND));

        if (!review.getWine().getId().equals(wineId)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        if (!review.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN_ACCESS);
        }

        int oldRating = review.getRating();
        int newRating = request.getRating();

        review.updateReview(request.getRating(), request.getContent());

        if (oldRating != newRating) {
            // 별점이 바뀐 만큼만 원자적으로 반영 (리뷰 수는 그대로)
            wineRepository.applyUserRatingChange(wineId, 0, newRating - oldRating, newRating, oldRating);
        }

        preferenceRepository.findByUserId(userId).ifPresent(preference ->
                preference.markReportAsOutdated()
        );
    }


    /**
     * 리뷰 삭제
     */
    public void deleteReview(Long userId, Long wineId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_NOT_FOUND));

        if (!review.getWine().getId().equals(wineId)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        if (!review.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN_ACCESS);
        }

        int oldRating = review.getRating();

        review.getUser().decreaseReviewCount();
        reviewRepository.delete(review);
        // 삭제된 리뷰의 별점을 집계에서 원자적으로 뺀다 (flushAutomatically로 위 변경이 먼저 반영됨)
        wineRepository.applyUserRatingChange(wineId, -1, -oldRating, 0, oldRating);

        preferenceRepository.findByUserId(userId).ifPresent(preference ->
                preference.markReportAsOutdated()
        );
    }


    /**
     * 현재 로그인한 사용자의 리뷰 목록을 조회하여 DTO로 반환
     * * @param user 현재 인증된 사용자 객체
     * @return 마이페이지용 리뷰 응답 DTO 리스트
     */
    @Transactional(readOnly = true)
    public Page<MyPageReviewResponse> getMyReviews(User user, Pageable pageable) {
        return reviewRepository.findAllByUserWithWine(user, pageable)
                .map(MyPageReviewResponse::from);
    }


}
