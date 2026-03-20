package com.a505.jupasu.domain.wine.util;

import com.a505.jupasu.domain.preference.entity.DrinkingSituation;
import com.a505.jupasu.domain.preference.entity.Preference;
import com.a505.jupasu.domain.preference.repository.PreferenceRepository;
import com.a505.jupasu.domain.reviews.entity.Review;
import com.a505.jupasu.domain.reviews.repository.ReviewRepository;
import com.a505.jupasu.domain.user.entity.User;
import com.a505.jupasu.domain.wine.dto.WineQuickRecommendResponse;
import com.a505.jupasu.domain.wine.dto.WineRecommendationItem;
import com.a505.jupasu.domain.wine.entity.Wine;
import com.a505.jupasu.domain.wine.entity.vo.TasteProfile;
import com.a505.jupasu.domain.wine.repository.WineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
@RequiredArgsConstructor
public class WineRecommendationCalculator {

    private final WineRepository wineRepository;
    private final PreferenceRepository preferenceRepository;
    private final ReviewRepository reviewRepository;

    // ── 스케일 정보 ──────────────────────────────────────────────────────────────
    // W_i (TasteProfile): 0.0 ~ 1.0 (Vivino 정규화)
    // W'_i = W_i × 2 → 0.0 ~ 2.0
    // U_i^stated (Preference 1~10) → (val-1)/9 × 2 → 0.0 ~ 2.0 (동일 범위)
    // W_alc / U_alc: 실제 도수(%), 정규화 없음

    // σ_base: 취향 가우시안 기본 너비 (0~2 스케일 기준, 원 수식 3.5 / 5 = 0.7)
    private static final float SIGMA_BASE = 0.7f;
    // σ_a: 도수 가우시안 너비 (%)
    private static final float SIGMA_A    = 2.5f;
    // σ_s: 상황 Half-Gaussian 너비 (0~2 스케일, 원 수식 2.5 / 5 = 0.5)
    private static final float SIGMA_S    = 0.5f;
    // σ_dir: 가격 가우시안 너비 (원)
    private static final float SIGMA_DIR  = 15000f;
    // 유저 도수 선호 없을 때 폴백 기본값 (%)
    private static final float T_ALC_DEFAULT = 13.0f;

    // ── 베이지안 교정 상수 ────────────────────────────────────────────────────────
    // N_0: γ 포화 속도 상수 (리뷰 10개에서 γ ≈ 0.63)
    private static final float N_0   = 10f;
    // α: 시간 감쇠 속도 (1/일, 90일 후 가중치 ≈ 0.50)
    private static final float ALPHA = 0.008f;
    // δ: σ̂_t 교정 민감도
    private static final float DELTA = 0.5f;

    // ── S_sit 임계값 θ (W'_i 0~2 스케일) ────────────────────────────────────────
    // 원 수식 θ 기준 (0~10): 6 → 1.2,  4 → 0.8,  7 → 1.4
    private static final float THETA_BODY_ALONE   = 1.2f; // 혼술: body ↓ θ=6/5
    private static final float THETA_TANNIN_ALONE = 0.8f; // 혼술: tannin ↓ θ=4/5
    private static final float THETA_BODY_DATE    = 1.2f; // 기념일: body ↑
    private static final float THETA_TANNIN_DATE  = 1.2f; // 기념일: tannin ↑
    private static final float THETA_SWEET_PARTY  = 1.2f; // 파티: sweetness ↑ θ=6/5
    private static final float THETA_ACID_PARTY   = 1.4f; // 파티: acidity ↑ θ=7/5

    // ── 교정된 선호 프로필 (calibrate() 반환값) ───────────────────────────────────
    private record CalibratedProfile(
            float uHatSweet,   // Û_sweet  (NaN이면 해당 차원 미설정)
            float uHatAcid,    // Û_acid
            float uHatBody,    // Û_body
            float uHatTannin,  // Û_tannin
            float sigmaT       // σ̂_t (개인화 취향 폭)
    ) {}

    // ── 구현된 추천 상황 리스트 ───────────────────────────────────────────────────
    private final DrinkingSituation[] situationList = {
            DrinkingSituation.ALONE,
            DrinkingSituation.DATE,
            DrinkingSituation.PARTY
    };

    // ── 퀵 추천 목록 ──────────────────────────────────────────────────────────────

    /**
     * 상황 기반 퀵 추천 목록 반환
     */
    public List<WineRecommendationItem> quickList(User user, DrinkingSituation situation) {
        Preference pref = preferenceRepository.findByUserId(user.getId()).orElse(null);
        List<Review> reviews = reviewRepository.findAllByUserWithWine(user);
        CalibratedProfile calibrated = calibrate(pref, reviews);

        PriorityQueue<WineScore> minHeap = new PriorityQueue<>(Comparator.comparingInt(WineScore::match));
        List<Wine> wineList = wineRepository.findAll();

        for (Wine wine : wineList) {
            int match = score(wine, situation, pref, calibrated);
            if (match == Integer.MIN_VALUE) continue; // 추천 제외 와인
            addToHeap(minHeap, new WineScore(match, wine), 5);
        }

        return heapToList(minHeap);
    }

    /**
     * 상황 없이 취향 기반 추천도 계산
     */
    public int getMatch(User user, Wine wine) {
        Preference pref = preferenceRepository.findByUserId(user.getId()).orElse(null);
        List<Review> reviews = reviewRepository.findAllByUserWithWine(user);
        CalibratedProfile calibrated = calibrate(pref, reviews);
        return score(wine, null, pref, calibrated);
    }

    /**
     * 모든 상황별 + 상황 무관 종합 추천 목록 반환
     */
    public WineQuickRecommendResponse allQuickLists(User user) {
        Preference pref = preferenceRepository.findByUserId(user.getId()).orElse(null);
        List<Review> reviews = reviewRepository.findAllByUserWithWine(user);
        CalibratedProfile calibrated = calibrate(pref, reviews);

        PriorityQueue<WineScore> generalHeap = new PriorityQueue<>(Comparator.comparingInt(WineScore::match));
        Map<DrinkingSituation, PriorityQueue<WineScore>> situationHeaps = new EnumMap<>(DrinkingSituation.class);
        for (DrinkingSituation sit : situationList) {
            situationHeaps.put(sit, new PriorityQueue<>(Comparator.comparingInt(WineScore::match)));
        }

        List<Wine> wineList = wineRepository.findAll();

        for (Wine wine : wineList) {
            // 1. 상황 무관 점수 계산
            int generalMatch = score(wine, null, pref, calibrated);
            if (generalMatch != Integer.MIN_VALUE) {
                addToHeap(generalHeap, new WineScore(generalMatch, wine), 5);
            }

            // 2. 각 상황별 점수 계산
            for (DrinkingSituation sit : situationList) {
                int sitMatch = score(wine, sit, pref, calibrated);
                if (sitMatch != Integer.MIN_VALUE) {
                    addToHeap(situationHeaps.get(sit), new WineScore(sitMatch, wine), 5);
                }
            }
        }

        List<WineRecommendationItem> generalList = heapToList(generalHeap);
        List<WineQuickRecommendResponse.SituationResult> bySituation = new ArrayList<>();
        for (DrinkingSituation sit : situationList) {
            bySituation.add(WineQuickRecommendResponse.SituationResult.of(sit, heapToList(situationHeaps.get(sit))));
        }

        return WineQuickRecommendResponse.of(generalList, bySituation);
    }

    private void addToHeap(PriorityQueue<WineScore> heap, WineScore wineScore, int limit) {
        if (heap.size() < limit) {
            heap.offer(wineScore);
        } else if (wineScore.match() > heap.peek().match()) {
            heap.poll();
            heap.offer(wineScore);
        }
    }

    private List<WineRecommendationItem> heapToList(PriorityQueue<WineScore> heap) {
        return heap.stream()
                .sorted(Comparator.comparingInt(WineScore::match).reversed())
                .map(s -> WineRecommendationItem.of(s.wine(), s.match()))
                .toList();
    }

    /**
     * 상황을 고려한 추천도 계산 (단일 와인용 — 외부 호출 지점)
     */
    public int getMatch(User user, Wine wine, DrinkingSituation situation) {
        Preference pref = preferenceRepository.findByUserId(user.getId()).orElse(null);
        List<Review> reviews = reviewRepository.findAllByUserWithWine(user);
        CalibratedProfile calibrated = calibrate(pref, reviews);
        return score(wine, situation, pref, calibrated);
    }

    // ── calibrate: U_i^revealed · γ · Û_i · σ̂_t ────────────────────────────────

    /**
     * 유저 리뷰로부터 교정된 선호 프로필을 계산한다.
     *
     * <p>Step 1 — 실증 선호 벡터 U_i^revealed
     * <pre>
     *   U_i^revealed = Σ(r̃_j · τ_j · W'_ij · 𝟙_ij) / Σ(|r̃_j| · τ_j · 𝟙_ij)
     *   r̃_j = (r_j − 3) / 2          ∈ [−1, +1]
     *   τ_j  = exp(−α · Δt_j)         (시간 감쇠, α=0.008/일)
     *   𝟙_ij = 와인 j의 차원 i 유효 여부 (isReal*)
     * </pre>
     * 차원 i에 유효한 리뷰가 없으면(Σ𝟙_ij=0) → U_i^stated로 폴백.
     *
     * <p>Step 2 — 신뢰도 가중치 γ
     * <pre>
     *   γ = 1 − exp(−N_rated / N_0)   (N_0 = 10)
     * </pre>
     *
     * <p>Step 3 — 교정된 취향값 Û_i
     * <pre>
     *   Û_i = (1 − γ) · U_i^stated + γ · U_i^revealed
     * </pre>
     *
     * <p>Step 4 — 개인화 취향 폭 σ̂_t  (R⁺: 4점 이상 평가 와인 기준)
     * <pre>
     *   μ_i    = Σ(j∈R⁺) W'_ij · 𝟙_ij / Σ 𝟙_ij
     *   Var_i  = Σ(j∈R⁺) (W'_ij − μ_i)² · 𝟙_ij / Σ 𝟙_ij
     *   Var    = (1 / N_valid_rated) · Σ Var_i
     *   σ̂_t   = σ_base · √(1 + δ · Var / σ_base²)
     * </pre>
     * 모든 차원이 NULL이면 σ̂_t = σ_base 폴백.
     */
    private CalibratedProfile calibrate(Preference pref, List<Review> reviews) {

        // U_i^stated (0~2 스케일)
        float uStatedSweet  = toScale(pref != null ? pref.getSweetness() : null);
        float uStatedAcid   = toScale(pref != null ? pref.getAcidity()   : null);
        float uStatedBody   = toScale(pref != null ? pref.getBody()      : null);
        float uStatedTannin = toScale(pref != null ? pref.getTannin()    : null);

        int nRated = reviews.size();

        // 콜드 스타트: 리뷰 없음 → γ=0, σ̂_t=σ_base
        if (nRated == 0) {
            return new CalibratedProfile(uStatedSweet, uStatedAcid, uStatedBody, uStatedTannin, SIGMA_BASE);
        }

        // ── Step 1: U_i^revealed 누산 ────────────────────────────────────────────
        // num_i: Σ r̃_j · τ_j · W'_ij · 𝟙_ij
        // den_i: Σ |r̃_j| · τ_j · 𝟙_ij
        double numSweet = 0, denSweet = 0;
        double numAcid  = 0, denAcid  = 0;
        double numBody  = 0, denBody  = 0;
        double numTannin = 0, denTannin = 0;

        // Step 4: σ̂_t 분산 누산 (R⁺: rating ≥ 4)
        // E[X²]와 E[X]를 각각 누적해 Var = E[X²] - E[X]² 로 계산
        double sumN_s = 0, sumX_s = 0, sumX2_s = 0;
        double sumN_a = 0, sumX_a = 0, sumX2_a = 0;
        double sumN_b = 0, sumX_b = 0, sumX2_b = 0;
        double sumN_t = 0, sumX_t = 0, sumX2_t = 0;

        LocalDateTime now = LocalDateTime.now();

        for (Review review : reviews) {
            Wine wine = review.getWine();
            TasteProfile tp = wine.getTasteProfile();
            if (tp == null) continue;

            float r      = review.getRating();
            float rTilde = (r - 3f) / 2f;                                          // [-1, +1]
            long deltaDays = ChronoUnit.DAYS.between(review.getCreatedAt(), now);
            double tau   = Math.exp(-ALPHA * deltaDays);                            // 시간 감쇠

            double absRTildeTau = Math.abs(rTilde) * tau;
            double rTildeTau    = rTilde * tau;

            // 𝟙_ij: 와인 j의 각 차원 유효 여부
            boolean vs = Boolean.TRUE.equals(tp.getIsRealSweetness());
            boolean va = Boolean.TRUE.equals(tp.getIsRealAcidity());
            boolean vb = Boolean.TRUE.equals(tp.getIsRealBody());
            boolean vt = Boolean.TRUE.equals(tp.getIsRealTannin());

            // W'_ij = W_ij × 2
            double wS = vs ? tp.getSweetness() * 2f : 0f;
            double wA = va ? tp.getAcidity()   * 2f : 0f;
            double wB = vb ? tp.getBody()       * 2f : 0f;
            double wT = vt ? tp.getTannin()     * 2f : 0f;

            // U_i^revealed 누산 (전체 리뷰 사용)
            if (vs) { numSweet  += rTildeTau * wS; denSweet  += absRTildeTau; }
            if (va) { numAcid   += rTildeTau * wA; denAcid   += absRTildeTau; }
            if (vb) { numBody   += rTildeTau * wB; denBody   += absRTildeTau; }
            if (vt) { numTannin += rTildeTau * wT; denTannin += absRTildeTau; }

            // σ̂_t 분산 누산 (R⁺만: rating ≥ 4)
            if (r >= 4f) {
                if (vs) { sumN_s++; sumX_s += wS; sumX2_s += wS * wS; }
                if (va) { sumN_a++; sumX_a += wA; sumX2_a += wA * wA; }
                if (vb) { sumN_b++; sumX_b += wB; sumX2_b += wB * wB; }
                if (vt) { sumN_t++; sumX_t += wT; sumX2_t += wT * wT; }
            }
        }

        // ── Step 1 결과: U_i^revealed ────────────────────────────────────────────
        // 분모가 0 (해당 차원의 유효 리뷰 없음) → U_i^stated로 폴백
        float uRevSweet  = (denSweet  > 0) ? (float)(numSweet  / denSweet)  : uStatedSweet;
        float uRevAcid   = (denAcid   > 0) ? (float)(numAcid   / denAcid)   : uStatedAcid;
        float uRevBody   = (denBody   > 0) ? (float)(numBody   / denBody)   : uStatedBody;
        float uRevTannin = (denTannin > 0) ? (float)(numTannin / denTannin) : uStatedTannin;

        // ── Step 2: γ ─────────────────────────────────────────────────────────────
        float gamma = 1f - (float) Math.exp(-nRated / N_0);

        // ── Step 3: Û_i = (1−γ)·U_i^stated + γ·U_i^revealed ─────────────────────
        float uHatSweet  = blendPreference(uStatedSweet,  uRevSweet,  gamma, denSweet  > 0);
        float uHatAcid   = blendPreference(uStatedAcid,   uRevAcid,   gamma, denAcid   > 0);
        float uHatBody   = blendPreference(uStatedBody,   uRevBody,   gamma, denBody   > 0);
        float uHatTannin = blendPreference(uStatedTannin, uRevTannin, gamma, denTannin > 0);

        // ── Step 4: σ̂_t ─────────────────────────────────────────────────────────
        // Var_i = E[X²] − E[X]²  (분모=0이면 해당 차원 제외)
        int nValidRated = 0;
        double varSum   = 0.0;
        double varS = variance(sumN_s, sumX_s, sumX2_s);
        double varA = variance(sumN_a, sumX_a, sumX2_a);
        double varB = variance(sumN_b, sumX_b, sumX2_b);
        double varT = variance(sumN_t, sumX_t, sumX2_t);
        if (varS >= 0) { nValidRated++; varSum += varS; }
        if (varA >= 0) { nValidRated++; varSum += varA; }
        if (varB >= 0) { nValidRated++; varSum += varB; }
        if (varT >= 0) { nValidRated++; varSum += varT; }

        float sigmaT;
        if (nValidRated == 0) {
            sigmaT = SIGMA_BASE; // R⁺ 와인 전체 NULL → 기본값 폴백
        } else {
            double varMean = varSum / nValidRated;
            sigmaT = (float)(SIGMA_BASE * Math.sqrt(1.0 + DELTA * varMean / (SIGMA_BASE * SIGMA_BASE)));
        }

        return new CalibratedProfile(uHatSweet, uHatAcid, uHatBody, uHatTannin, sigmaT);
    }

    // ── score: 최종 추천 점수 계산 (calibrate 결과를 입력으로 받음) ────────────────

    /**
     * NULL 인식 베이지안 교정 모델 기반 추천도 계산
     *
     * @return 0~100 정수 추천 점수. Integer.MIN_VALUE 는 추천 목록 제외를 의미.
     */
    private int score(Wine wine, DrinkingSituation situation, Preference pref, CalibratedProfile cal) {
        TasteProfile tp = wine.getTasteProfile();

        // ── ① 유효성 지시 함수 𝟙_i ──────────────────────────────────────────────
        // isReal* = false → 값 없음 → 𝟙 = 0, 해당 차원을 계산에서 제외
        boolean validSweet  = tp != null && Boolean.TRUE.equals(tp.getIsRealSweetness());
        boolean validAcid   = tp != null && Boolean.TRUE.equals(tp.getIsRealAcidity());
        boolean validBody   = tp != null && Boolean.TRUE.equals(tp.getIsRealBody());
        boolean validTannin = tp != null && Boolean.TRUE.equals(tp.getIsRealTannin());
        boolean validAlc    = Boolean.TRUE.equals(wine.getIsRealAlcoholDegree());
        int nValid = (validSweet ? 1 : 0) + (validAcid ? 1 : 0)
                   + (validBody ? 1 : 0) + (validTannin ? 1 : 0) + (validAlc ? 1 : 0);

        // ── ② 와인 특성 정규화 W'_i = W_i × 2 (유효 차원만) ─────────────────────
        float wSweet  = validSweet  ? tp.getSweetness() * 2f : 0f;
        float wAcid   = validAcid   ? tp.getAcidity()   * 2f : 0f;
        float wBody   = validBody   ? tp.getBody()       * 2f : 0f;
        float wTannin = validTannin ? tp.getTannin()     * 2f : 0f;
        float wAlc    = wine.getAlcoholDegree(); // 실제 도수(%), 정규화 불필요

        // ── ③ 유저 도수 선호: Preference.Abv (실제 도수 %), 없으면 T_alc 폴백 ────
        float uAlc = (pref != null && pref.getAbv() != null) ? pref.getAbv() : T_ALC_DEFAULT;

        // ── ④ S_pref: 취향·도수 통합 점수 ───────────────────────────────────────
        // S_pref = (1/N_valid) × Σ exp(−(Û_i − W'_i)² / 2σ̂_t²)
        float sPref;
        if (nValid == 0) {
            sPref = 0f;
        } else {
            float sigmaT = cal.sigmaT();
            float sum = 0f;
            if (validSweet  && !Float.isNaN(cal.uHatSweet()))  sum += gaussian(cal.uHatSweet(),  wSweet,  sigmaT);
            if (validAcid   && !Float.isNaN(cal.uHatAcid()))   sum += gaussian(cal.uHatAcid(),   wAcid,   sigmaT);
            if (validBody   && !Float.isNaN(cal.uHatBody()))   sum += gaussian(cal.uHatBody(),   wBody,   sigmaT);
            if (validTannin && !Float.isNaN(cal.uHatTannin())) sum += gaussian(cal.uHatTannin(), wTannin, sigmaT);
            if (validAlc)                                       sum += gaussian(uAlc,             wAlc,    SIGMA_A);
            
            // 데이터 충실도 페널티 (정보가 많을수록 신뢰도 상승)
            // nValid가 5(전체)일 때는 페널티 없음, 1개일 때는 0.6배로 감점
            float completenessFactor = 0.6f + 0.4f * (nValid / 5.0f);
            sPref = (sum / nValid) * completenessFactor;
        }

        // ── ⑤ S_sit: 상황 적합도 점수 ───────────────────────────────────────────
        float sSit = calcSSit(situation, wSweet, wAcid, wBody, wTannin,
                validSweet, validAcid, validBody, validTannin);

        // ── ⑥ S_price: 가격 점수 ────────────────────────────────────────────────
        boolean validPrice = wine.getPriceAndRating() != null
                && Boolean.TRUE.equals(wine.getPriceAndRating().getIsRealPrice());
        float sPrice;
        if (!validPrice) {
            sPrice = 0.5f;
        } else if (pref == null
                || pref.getPreferredPriceMin() == null
                || pref.getPreferredPriceMax() == null) {
            sPrice = 0.5f;
        } else {
            float tPrice = (pref.getPreferredPriceMin() + pref.getPreferredPriceMax()) / 2f;
            sPrice = gaussian(wine.getPriceAndRating().getPrice(), tPrice, SIGMA_DIR);
        }

        // ── ⑦ 데이터 부재 와인 제외 ───────────────────────────────────────────
        // 맛/도수 정보(isReal)가 하나도 없는 와인은 가격과 상관없이 추천에서 제외
        if (nValid == 0) return Integer.MIN_VALUE;

        // ── ⑧ 상황별 가중치 적용 ────────────────────────────────────────────────
        float wPref, wPriceW, wSitW;
        if (situation == null) {
            wPref = 0.70f; wPriceW = 0.30f; wSitW = 0.00f;
        } else {
            switch (situation) {
                case ALONE -> { wPref = 0.50f; wPriceW = 0.30f; wSitW = 0.20f; }
                case DATE  -> { wPref = 0.40f; wPriceW = 0.20f; wSitW = 0.40f; }
                case PARTY -> { wPref = 0.30f; wPriceW = 0.20f; wSitW = 0.50f; }
                // TODO: GIFT, HOUSEWARMING, FAMILY 상황별 가중치 정의 예정
                default    -> { wPref = 0.50f; wPriceW = 0.30f; wSitW = 0.20f; }
            }
        }

        // TODO: B_w (와인별 평균 편향 보정) 구현 예정
        //   B_w = β × (1/|Users_w|) × Σ(r̃_uw − Ŝ_uw)  (리뷰 5개 이상 와인에만 적용, β=0.15)
        float bW = 0f;

        float total = wPref * sPref + wPriceW * sPrice + wSitW * sSit + bW;
        return Math.round(total * 100);
    }

    // ── S_sit 상황별 계산 ────────────────────────────────────────────────────────

    private float calcSSit(DrinkingSituation situation,
                           float wSweet, float wAcid, float wBody, float wTannin,
                           boolean validSweet, boolean validAcid, boolean validBody, boolean validTannin) {
        if (situation == null) return 0f;
        return switch (situation) {
            case ALONE -> {
                // 혼술: 가벼운 와인 — body↓, tannin↓ (유효 차원의 기하 평균)
                List<Float> scores = new ArrayList<>();
                if (validBody)   scores.add(halfGaussian(wBody,   THETA_BODY_ALONE,   false, SIGMA_S));
                if (validTannin) scores.add(halfGaussian(wTannin, THETA_TANNIN_ALONE, false, SIGMA_S));
                yield geometricMean(scores);
            }
            case DATE -> {
                // 기념일: 풀바디 고급 와인 — body↑, tannin↑ (유효 차원의 기하 평균)
                List<Float> scores = new ArrayList<>();
                if (validBody)   scores.add(halfGaussian(wBody,   THETA_BODY_DATE,   true, SIGMA_S));
                if (validTannin) scores.add(halfGaussian(wTannin, THETA_TANNIN_DATE, true, SIGMA_S));
                yield geometricMean(scores);
            }
            case PARTY -> {
                // 파티: 화사한 와인 — sweetness↑ OR acidity↑ (OR 합집합)
                // S_sit = 1 − Π(1 − H_k)
                List<Float> scores = new ArrayList<>();
                if (validSweet) scores.add(halfGaussian(wSweet, THETA_SWEET_PARTY, true, SIGMA_S));
                if (validAcid)  scores.add(halfGaussian(wAcid,  THETA_ACID_PARTY,  true, SIGMA_S));
                if (scores.isEmpty()) yield 0.5f;
                float product = 1f;
                for (float s : scores) product *= (1f - s);
                yield 1f - product;
            }
            // TODO: GIFT, HOUSEWARMING, FAMILY 상황별 S_sit 공식 정의 예정
            default -> 0.5f;
        };
    }

    // ── 수식 헬퍼 ────────────────────────────────────────────────────────────────

    /**
     * Preference (1~10) → W'_i 동일 스케일 (0~2)로 변환.
     * null이면 NaN 반환 (해당 차원 미설정, S_pref 계산 시 제외).
     */
    private float toScale(Integer prefValue) {
        return (prefValue != null) ? (prefValue - 1) / 9f * 2f : Float.NaN;
    }

    /**
     * Û_i = (1−γ)·U_i^stated + γ·U_i^revealed 혼합.
     *
     * <ul>
     *   <li>stated·revealed 둘 다 유효 → γ 비율로 혼합</li>
     *   <li>stated만 유효 (리뷰 없음) → stated 그대로</li>
     *   <li>revealed만 유효 (설문 미입력) → revealed 그대로</li>
     *   <li>둘 다 없음 → NaN (차원 제외)</li>
     * </ul>
     */
    private float blendPreference(float stated, float revealed, float gamma, boolean hasRevealed) {
        boolean hasStated = !Float.isNaN(stated);
        if (!hasStated && !hasRevealed) return Float.NaN;
        if (!hasStated) return revealed;
        if (!hasRevealed) return stated;
        return (1f - gamma) * stated + gamma * revealed;
    }

    /**
     * 차원별 분산: Var = E[X²] − E[X]²  (단순 평균, 가중치 미적용 버전)
     *
     * @return 분산값 (≥ 0), 데이터 없으면 −1 (제외 신호)
     */
    private double variance(double n, double sumX, double sumX2) {
        if (n < 1) return -1.0;
        double mean = sumX / n;
        return sumX2 / n - mean * mean;
    }

    /** 가우시안 유사도: exp(−(x − mean)² / 2σ²) */
    private float gaussian(float x, float mean, float sigma) {
        float diff = x - mean;
        return (float) Math.exp(-diff * diff / (2 * sigma * sigma));
    }

    /** 방향성 Half-Gaussian: 목표 방향 충족 시 1.0, 미달 시 Gaussian 감쇠 */
    private float halfGaussian(float x, float theta, boolean up, float sigma) {
        if (up ? x >= theta : x <= theta) return 1.0f;
        float diff = x - theta;
        return (float) Math.exp(-diff * diff / (2 * sigma * sigma));
    }

    /** 기하 평균: 빈 리스트이면 0.5 (판단 불가 중립값) 반환 */
    private float geometricMean(List<Float> scores) {
        if (scores.isEmpty()) return 0.5f;
        double product = 1.0;
        for (float s : scores) product *= s;
        return (float) Math.pow(product, 1.0 / scores.size());
    }

    record WineScore(int match, Wine wine) {}
}
