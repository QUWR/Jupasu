package com.a505.jupasu.domain.report.service;

import com.a505.jupasu.domain.preference.entity.Preference;
import com.a505.jupasu.domain.preference.repository.PreferenceRepository;
import com.a505.jupasu.domain.report.dto.response.TasteReportResponse;
import com.a505.jupasu.domain.report.entity.TasteReport;
import com.a505.jupasu.domain.report.repository.TasteReportRepository;
import com.a505.jupasu.domain.reviews.entity.Review;
import com.a505.jupasu.domain.reviews.repository.ReviewRepository;
import com.a505.jupasu.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 취향 리포트 생성 및 분석 비즈니스 로직을 담당하는 서비스
 */
@Service
@RequiredArgsConstructor
public class TasteReportService {
    private final ReviewRepository reviewRepository;
    private final TasteReportRepository tasteReportRepository;
    private final PreferenceRepository preferenceRepository;

    private static final double PREFERENCE_WEIGHT = 5.0;

    /**
     * 가장 최근에 생성된 유저의 취향 리포트를 반환하거나, 리포트가 만료되었으면 새로 생성하여 반환
     */
    @Transactional
    public TasteReportResponse getOrCreateReport(User user) {
        Preference preference = preferenceRepository.findByUserId(user.getId()).orElse(null);

        // 1. 유저의 취향 또는 리뷰가 업데이트되어 리포트 갱신이 필요한 경우
        if (preference != null && preference.isReportOutdated()) {
            TasteReportResponse response = generateReport(user);
            // 취향 엔티티의 최신 요약 업데이트 & outdated 플래그 초기화
            preference.updateReportSummary(response.getContent());
            return response;
        }

        // 2. 갱신이 필요 없으면 최신 캐시된 리포트를 찾거나, 없으면 최초 1회 생성
        return tasteReportRepository.findTopByUserOrderByCreatedAtDesc(user)
                .map(TasteReportResponse::from)
                .orElseGet(() -> {
                    TasteReportResponse response = generateReport(user);
                    if (preference != null) {
                        preference.updateReportSummary(response.getContent());
                    }
                    return response;
                });
    }

    /**
     * 유저의 온보딩 데이터와 리뷰 히스토리를 결합하여 가중 평균 리포트를 생성
     */
    @Transactional
    public TasteReportResponse generateReport(User user) {
        List<Review> reviews = reviewRepository.findAllByUserWithWine(user);
        Preference preference = preferenceRepository.findByUserId(user.getId()).orElse(null);

        if (reviews.isEmpty() && preference == null) {
            return null;
        }

        // 가중 평균 계산
        double totalWeight = 0;
        double sumSweet = 0, sumAcid = 0, sumBody = 0, sumTan = 0, sumAlc = 0;

        if (preference != null) {
            totalWeight += PREFERENCE_WEIGHT;
            sumSweet += PREFERENCE_WEIGHT * (preference.getSweetness() != null ? preference.getSweetness() : 2.5);
            sumAcid += PREFERENCE_WEIGHT * (preference.getAcidity() != null ? preference.getAcidity() : 2.5);
            sumBody += PREFERENCE_WEIGHT * (preference.getBody() != null ? preference.getBody() : 2.5);
            sumTan += PREFERENCE_WEIGHT * (preference.getTannin() != null ? preference.getTannin() : 2.5);
            sumAlc += PREFERENCE_WEIGHT * (preference.getAbv() != null ? preference.getAbv() : 10.0);
        }

        for (Review review : reviews) {
            double rating = review.getRating();
            var wine = review.getWine();

            sumSweet += rating * wine.getTasteProfile().getSweetness();
            sumAcid += rating * wine.getTasteProfile().getAcidity();
            sumBody += rating * wine.getTasteProfile().getBody();
            sumTan += rating * wine.getTasteProfile().getTannin();
            sumAlc += rating * Double.valueOf(wine.getAlcoholDegree()); // 도수 실제 값 (최대 20)

            totalWeight += rating;
        }

        // 2. 최종 점수 산출 (0.0 ~ 5.0)
        double avgSweetness = sumSweet / totalWeight;
        double avgAcidity = sumAcid / totalWeight;
        double avgBody = sumBody / totalWeight;
        double avgTannin = sumTan / totalWeight;
        double avgAlcohol = sumAlc / totalWeight;

        // 3. 임시 텍스트 생성 (AI 도입 전까지 사용할 템플릿)
        String title = user.getNickname() + "님은 '밸런스가 좋은' 와인 취향이에요!";
        String content = String.format("평균적으로 당도 %.1f, 산도 %.1f의 와인을 선호하시네요.", avgSweetness, avgAcidity);

        // 4. 저장 및 반환
        TasteReport report = TasteReport.builder()
                .user(user)
                .avgSweetness(avgSweetness).avgAcidity(avgAcidity)
                .avgBody(avgBody).avgTannin(avgTannin).avgAlcohol(avgAlcohol)
                .mainTitle(title).content(content)
                .createdAt(LocalDateTime.now())
                .build();

        tasteReportRepository.save(report);
        return TasteReportResponse.from(report);
    }

    /**
     * AI API 호출 시 전달할 유저 데이터 텍스트를 생성
     */
    private String createPromptContext(List<Review> reviews) {
        StringBuilder sb = new StringBuilder();
        sb.append("사용자의 와인 리뷰 히스토리:\n");
        for (Review review : reviews) {
            sb.append(String.format("- 와인: %s, 평점: %.1f, 맛(당/산/바/탄/알): %.1f/%.1f/%.1f/%.1f/%.1f\n",
                    review.getWine().getNameKr(), review.getRating(),
                    review.getWine().getTasteProfile().getSweetness(), review.getWine().getTasteProfile().getAcidity(),
                    review.getWine().getTasteProfile().getBody(), review.getWine().getTasteProfile().getTannin(),
                    review.getWine().getAlcoholDegree()));
        }
        return sb.toString();
    }

}
