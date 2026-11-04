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
import com.a505.jupasu.domain.wine.repository.WineFoodPairingRepository;
import com.a505.jupasu.domain.wine.repository.WineRepository;
import com.a505.jupasu.domain.wine.service.WineQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional
public class WineRecommendationCalculator {

    private final WineQueryService wineQueryService;
    private final PreferenceRepository preferenceRepository;
    private final ReviewRepository reviewRepository;
    private final WineFoodPairingRepository wineFoodPairingRepository;

    // ── Sigmoid 변환 ─────────────────────────────────────────────────────────────
    /**
     * 원점수(0~100)를 Sigmoid 변환으로 표시용 매칭률로 변환한다.
     *
     * <pre>
     *   display = round(100 / (1 + exp(−k · (s − μ))))
     *   s = rawScore / 100
     *   μ = 0.35  (50% 중립점: 원점수 35 이하는 50% 미만으로 표시)
     *   k = 6     (경사도: μ 기준 ±0.1마다 약 12%p 변화; 7에서 낮춰 분포를 약간 펼침)
     * </pre>
     *
     * 추천 시스템에서 실제로 반환되는 top-5 와인의 원점수는 약 55~85 범위이며,
     * 이 구간이 77~95%로 매핑되어 사용자에게 신뢰감 있는 점수를 제공한다.
     * 상대적 순위(ranking)는 sigmoid의 단조증가 특성으로 원점수와 동일하게 보존된다.
     *
     * <ul>
     *   <li>원점수 55 → 약 77%</li>
     *   <li>원점수 65 → 약 86%</li>
     *   <li>원점수 75 → 약 92%</li>
     *   <li>원점수 85 → 약 95%</li>
     * </ul>
     */
    private static int sigmoid(int rawScore) {
        double s = rawScore / 100.0;
        return (int) Math.round(100.0 / (1.0 + Math.exp(-6.0 * (s - 0.35))));
    }
    private final WineRepository wineRepository;

    // ── 스케일 정보 ──────────────────────────────────────────────────────────────
    // W_i (TasteProfile): 0.0 ~ 5.0 (Vivino 원본 스케일)
    // W'_i = W_i × 0.4 → 0.0 ~ 2.0
    // U_i^stated (Preference 1~5) → (val-1)/4 × 2 → 0.0 ~ 2.0 (동일 범위)
    // W_alc / U_alc: 실제 도수(%), 정규화 없음

    // σ_base: 취향 가우시안 기본 너비 (0~2 스케일 기준, 원 수식 3.5 / 5 = 0.7)
    private static final float SIGMA_BASE = 0.7f;
    // σ_a: 도수 가우시안 너비 (%)
    private static final float SIGMA_A    = 2.5f;
    // σ_s: 상황 Half-Gaussian 너비 (0~2 스케일, 원 수식 2.5 / 5 = 0.5)
    private static final float SIGMA_S    = 0.5f;
    // σ_dir: 가격 가우시안 너비 — 상황 없을 때 대칭 폴백용 (원)
    private static final float SIGMA_DIR  = 15000f;
    // 유저 도수 선호 없을 때 폴백 기본값 (%)
    private static final float T_ALC_DEFAULT = 13.0f;

    // ── 베이지안 교정 상수 ────────────────────────────────────────────────────────
    // N_0: γ 포화 속도 상수 (리뷰 10개에서 γ ≈ 0.63)
    private static final float N_0   = 10f;
    // α: 시간 감쇠 속도 (1/일, 90일 후 가중치 ≈ 0.50)
    private static final float ALPHA = 0.008f;
    // δ: σ̂_i 교정 민감도 (차원별)
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
            float uHatSweet,    // Û_sweet   (NaN이면 해당 차원 미설정)
            float uHatAcid,     // Û_acid
            float uHatBody,     // Û_body
            float uHatTannin,   // Û_tannin
            float sigmaSweet,   // σ̂_sweet  (차원별 개인화 취향 폭)
            float sigmaAcid,    // σ̂_acid
            float sigmaBody,    // σ̂_body
            float sigmaTannin   // σ̂_tannin
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
        List<Wine> wineList = wineQueryService.getAllWines();

        for (Wine wine : wineList) {
            int match = score(wine, situation, pref, calibrated);
            if (match == Integer.MIN_VALUE) continue; // 추천 제외 와인
            addToHeap(minHeap, new WineScore(match, wine), 5);
        }

        return heapToList(minHeap, situation);
    }

    /**
     * 상황 없이 취향 기반 추천도 계산 (상세 페이지용 — 데이터 불완전 와인도 점수 산출)
     */
    public int getMatch(User user, Wine wine) {
        return getMatch(user, wine, preferenceRepository.findByUserId(user.getId()).orElse(null));
    }

    /**
     * 이미 조회한 취향(Preference)을 받아 적중률을 계산한다.
     * 와인 상세 조회에서 취향을 한 번만 조회하도록 추가한 오버로드 (계산 로직은 위 메서드와 동일)
     */
    public int getMatch(User user, Wine wine, Preference pref) {
        List<Review> reviews = reviewRepository.findAllByUserWithWine(user);
        CalibratedProfile calibrated = calibrate(pref, reviews);
        return sigmoid(scoreForDetail(wine, pref, calibrated));
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

        List<Wine> wineList = wineQueryService.getAllWines();

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

        List<WineRecommendationItem> generalList = heapToList(generalHeap, null);
        List<WineQuickRecommendResponse.SituationResult> bySituation = new ArrayList<>();
        for (DrinkingSituation sit : situationList) {
            bySituation.add(WineQuickRecommendResponse.SituationResult.of(sit, heapToList(situationHeaps.get(sit), sit)));
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

    private List<WineRecommendationItem> heapToList(PriorityQueue<WineScore> heap, DrinkingSituation situation) {
        List<WineScore> sortedScores = heap.stream()
                .sorted(Comparator.comparingInt(WineScore::match).reversed())
                .toList();

        if (sortedScores.isEmpty()) {
            return List.of();
        }

        List<Long> wineIds = sortedScores.stream()
                .map(s -> s.wine().getId())
                .toList();

        // IN 쿼리로 페어링 푸드 한 번에 조회
        List<Object[]> foodResults = wineFoodPairingRepository.findFoodNamesByWineIds(wineIds);
        Map<Long, List<String>> foodMap = foodResults.stream()
                .collect(Collectors.groupingBy(
                        r -> (Long) r[0],
                        Collectors.mapping(r -> (String) r[1], Collectors.toList())
                ));

        return sortedScores.stream()
                .map(s -> WineRecommendationItem.of(
                        s.wine(),
                        sigmoid(s.match()),
                        situation,
                        foodMap.getOrDefault(s.wine().getId(), List.of())
                ))
                .toList();
    }

    /**
     * 상황을 고려한 추천도 계산 (단일 와인용 — 외부 호출 지점)
     */
    public int getMatch(User user, Wine wine, DrinkingSituation situation) {
        Preference pref = preferenceRepository.findByUserId(user.getId()).orElse(null);
        List<Review> reviews = reviewRepository.findAllByUserWithWine(user);
        CalibratedProfile calibrated = calibrate(pref, reviews);
        return sigmoid(score(wine, situation, pref, calibrated));
    }

    // ── calibrate: U_i^revealed · γ · Û_i · σ̂_i ────────────────────────────────

    /**
     * 유저 리뷰로부터 교정된 선호 프로필을 계산한다.
     *
     * <p>Step 1 — 실증 선호 벡터 U_i^revealed  (긍정 리뷰만 사용, r̃ > 0)
     * <pre>
     *   U_i^revealed = Σ_{j: r̃_j>0}(r̃_j · τ_j · W'_ij · 𝟙_ij) / Σ_{j: r̃_j>0}(r̃_j · τ_j · 𝟙_ij)
     *   r̃_j = (r_j − 3) / 2          ∈ (0, +1]  (r̃ > 0, 즉 rating ≥ 4만 포함)
     *   τ_j  = exp(−α · Δt_j)         (시간 감쇠, α=0.008/일)
     *   𝟙_ij = 와인 j의 차원 i 유효 여부 (isReal*)
     * </pre>
     * 부정 신호(r̃ ≤ 0) 제외 이유: 낮은 속성값을 가진 와인에 낮은 별점을 줬을 때
     * "낮은 속성 = 기피"로 잘못 추론되는 방향 모호성을 막기 위함.
     * 기피 효과는 Gaussian 거리 감쇠로 암묵적으로 처리됨.
     * 차원 i에 긍정 리뷰가 없으면(분모=0) → U_i^stated로 폴백.
     *
     * <p>Step 2 — 신뢰도 가중치 γ
     * <pre>
     *   γ = 1 − exp(−N_rated / N_0)   (N_0 = 10, N_rated = 전체 리뷰 수)
     * </pre>
     *
     * <p>Step 3 — 교정된 취향값 Û_i
     * <pre>
     *   Û_i = (1 − γ) · U_i^stated + γ · U_i^revealed
     * </pre>
     *
     * <p>Step 4 — 차원별 개인화 취향 폭 σ̂_i  (R⁺: 긍정 리뷰 와인 기준)
     * <pre>
     *   μ_i   = Σ(j∈R⁺) W'_ij · 𝟙_ij / Σ 𝟙_ij
     *   Var_i = Σ(j∈R⁺) (W'_ij − μ_i)² · 𝟙_ij / Σ 𝟙_ij
     *   σ̂_i  = σ_base · √(1 + δ · Var_i / σ_base²)   (차원별 독립 계산)
     * </pre>
     * 해당 차원의 긍정 리뷰가 없으면 σ̂_i = σ_base 폴백.
     */
    private CalibratedProfile calibrate(Preference pref, List<Review> reviews) {

        // U_i^stated (0~2 스케일)
        float uStatedSweet  = toScale(pref != null ? pref.getSweetness() : null);
        float uStatedAcid   = toScale(pref != null ? pref.getAcidity()   : null);
        float uStatedBody   = toScale(pref != null ? pref.getBody()      : null);
        float uStatedTannin = toScale(pref != null ? pref.getTannin()    : null);

        int nRated = reviews.size();

        // 콜드 스타트: 리뷰 없음 → γ=0, σ̂_i=σ_base (모든 차원)
        if (nRated == 0) {
            return new CalibratedProfile(uStatedSweet, uStatedAcid, uStatedBody, uStatedTannin,
                                         SIGMA_BASE, SIGMA_BASE, SIGMA_BASE, SIGMA_BASE);
        }

        // ── Step 1: U_i^revealed 누산 (긍정 리뷰만, r̃ > 0) ────────────────────
        // num_i: Σ_{r̃>0} r̃_j · τ_j · W'_ij · 𝟙_ij
        // den_i: Σ_{r̃>0} r̃_j · τ_j · 𝟙_ij  (r̃>0이므로 |r̃|=r̃)
        double numSweet = 0, denSweet = 0;
        double numAcid  = 0, denAcid  = 0;
        double numBody  = 0, denBody  = 0;
        double numTannin = 0, denTannin = 0;

        // Step 4: σ̂_i 분산 누산 (동일한 긍정 리뷰 집합 R+ 사용)
        // E[X²]와 E[X]를 각각 누적해 Var_i = E[X²] - E[X]² 로 계산
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

            double rTildeTau = rTilde * tau;

            // 𝟙_ij: 와인 j의 각 차원 유효 여부
            boolean vs = Boolean.TRUE.equals(tp.getIsRealSweetness());
            boolean va = Boolean.TRUE.equals(tp.getIsRealAcidity());
            boolean vb = Boolean.TRUE.equals(tp.getIsRealBody());
            boolean vt = Boolean.TRUE.equals(tp.getIsRealTannin());

            // W'_ij = W_ij × 0.4  (0~5 스케일 → 0~2 정규화)
            double wS = vs ? tp.getSweetness() * 0.4f : 0f;
            double wA = va ? tp.getAcidity()   * 0.4f : 0f;
            double wB = vb ? tp.getBody()       * 0.4f : 0f;
            double wT = vt ? tp.getTannin()     * 0.4f : 0f;

            // U_i^revealed 누산 + σ̂_i 분산 누산 (긍정 리뷰만: r̃ > 0, 즉 rating ≥ 4)
            if (rTilde > 0f) {
                if (vs) { numSweet  += rTildeTau * wS; denSweet  += rTildeTau; }
                if (va) { numAcid   += rTildeTau * wA; denAcid   += rTildeTau; }
                if (vb) { numBody   += rTildeTau * wB; denBody   += rTildeTau; }
                if (vt) { numTannin += rTildeTau * wT; denTannin += rTildeTau; }

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

        // ── Step 4: σ̂_i — 차원별 개인화 취향 폭 ────────────────────────────────
        // 차원마다 독립 분산으로 취향 엄밀도를 개인화.
        // 긍정 리뷰가 없는 차원은 SIGMA_BASE로 폴백.
        float sigmaSweet  = computeSigma(sumN_s, sumX_s, sumX2_s);
        float sigmaAcid   = computeSigma(sumN_a, sumX_a, sumX2_a);
        float sigmaBody   = computeSigma(sumN_b, sumX_b, sumX2_b);
        float sigmaTannin = computeSigma(sumN_t, sumX_t, sumX2_t);

        return new CalibratedProfile(uHatSweet, uHatAcid, uHatBody, uHatTannin,
                                     sigmaSweet, sigmaAcid, sigmaBody, sigmaTannin);
    }

    // ── score: 최종 추천 점수 계산 (calibrate 결과를 입력으로 받음) ────────────────

    /**
     * NULL 인식 베이지안 교정 모델 기반 추천도 계산 (기존 엄격 모드 래퍼)
     * 모든 기존 호출(quickList, allQuickLists, getMatch 등)은 이쪽을 통해 relaxValidation=false 유지.
     *
     * @return 0~100 정수 추천 점수. Integer.MIN_VALUE 는 추천 목록 제외를 의미.
     */
    private int score(Wine wine, DrinkingSituation situation, Preference pref, CalibratedProfile cal) {
        return score(wine, situation, pref, cal, false);
    }

    /**
     * relaxValidation=true 이면 isReal* 플래그를 무시하고 존재하는 데이터를 모두 사용.
     * RAG 전용 경로(ragReRank)에서만 true 로 호출.
     */
    private int score(Wine wine, DrinkingSituation situation, Preference pref, CalibratedProfile cal, boolean relaxValidation) {
        TasteProfile tp = wine.getTasteProfile();

        // ── ① 유효성 지시 함수 𝟙_i ──────────────────────────────────────────────
        // relaxValidation=false(기본): isReal*=true 인 차원만 사용 (기존 동작 유지)
        // relaxValidation=true(RAG전용): isReal 무시, 값이 존재하면 유효로 처리
        boolean validSweet  = tp != null && tp.getSweetness()     != null && tp.getSweetness()     > 0
                              && (relaxValidation || Boolean.TRUE.equals(tp.getIsRealSweetness()));
        boolean validAcid   = tp != null && tp.getAcidity()       != null && tp.getAcidity()       > 0
                              && (relaxValidation || Boolean.TRUE.equals(tp.getIsRealAcidity()));
        boolean validBody   = tp != null && tp.getBody()          != null && tp.getBody()           > 0
                              && (relaxValidation || Boolean.TRUE.equals(tp.getIsRealBody()));
        boolean validTannin = tp != null && tp.getTannin()        != null && tp.getTannin()         > 0
                              && (relaxValidation || Boolean.TRUE.equals(tp.getIsRealTannin()));
        boolean validAlc    = wine.getAlcoholDegree()             != null && wine.getAlcoholDegree() > 0
                              && (relaxValidation || Boolean.TRUE.equals(wine.getIsRealAlcoholDegree()));
        int nValid = (validSweet ? 1 : 0) + (validAcid ? 1 : 0)
                   + (validBody ? 1 : 0) + (validTannin ? 1 : 0) + (validAlc ? 1 : 0);

        // ── ② 와인 특성 정규화 W'_i = W_i × 0.4 (0~5 스케일 → 0~2, 유효 차원만) ──
        float wSweet  = validSweet  ? tp.getSweetness() * 0.4f : 0f;
        float wAcid   = validAcid   ? tp.getAcidity()   * 0.4f : 0f;
        float wBody   = validBody   ? tp.getBody()       * 0.4f : 0f;
        float wTannin = validTannin ? tp.getTannin()     * 0.4f : 0f;
        float wAlc    = wine.getAlcoholDegree(); // 실제 도수(%), 정규화 불필요

        // ── ③ 유저 도수 선호: Preference.Abv (실제 도수 %), 없으면 T_alc 폴백 ────
        float uAlc = (pref != null && pref.getAbv() != null) ? pref.getAbv() : T_ALC_DEFAULT;

        // ── ④ S_pref: 취향·도수 통합 점수 ───────────────────────────────────────
        // S_pref = (1/N_sum) × Σ exp(−(Û_i − W'_i)² / 2σ̂_i²)  (차원별 σ̂_i 사용)
        // N_sum: 실제 Gaussian 합산에 참여한 차원 수 (uHat NaN 차원 제외)
        // N_valid: 와인 데이터 충실도 계산 기준 (completeness factor용)
        float sPref;
        if (nValid == 0) {
            sPref = 0f;
        } else {
            int nSum = 0;
            float sum = 0f;
            if (validSweet  && !Float.isNaN(cal.uHatSweet()))  { sum += gaussian(cal.uHatSweet(),  wSweet,  cal.sigmaSweet());  nSum++; }
            if (validAcid   && !Float.isNaN(cal.uHatAcid()))   { sum += gaussian(cal.uHatAcid(),   wAcid,   cal.sigmaAcid());   nSum++; }
            if (validBody   && !Float.isNaN(cal.uHatBody()))   { sum += gaussian(cal.uHatBody(),   wBody,   cal.sigmaBody());   nSum++; }
            if (validTannin && !Float.isNaN(cal.uHatTannin())) { sum += gaussian(cal.uHatTannin(), wTannin, cal.sigmaTannin()); nSum++; }
            if (validAlc)                                       { sum += gaussian(uAlc,             wAlc,    SIGMA_A);           nSum++; }

            // 데이터 충실도 페널티: 와인 데이터 기준(nValid), 평균 분모는 실제 합산 수(nSum)
            float completenessFactor = 0.6f + 0.4f * (nValid / 5.0f);
            sPref = nSum > 0 ? (sum / nSum) * completenessFactor : 0f;
        }

        // ── ⑤ S_sit: 상황 적합도 점수 ───────────────────────────────────────────
        float sSit = calcSSit(situation, wSweet, wAcid, wBody, wTannin,
                validSweet, validAcid, validBody, validTannin);

        // ── ⑥ S_price: 상황별 비대칭 가격 점수 ─────────────────────────────────
        boolean validPrice = wine.getPriceAndRating() != null
                && Boolean.TRUE.equals(wine.getPriceAndRating().getIsRealPrice())
                && wine.getPriceAndRating().getPrice() > 0;
        float sPrice = calcSPrice(wine, pref, situation, validPrice);

        // ── ⑦ 데이터 부재 와인 제외 ───────────────────────────────────────────
        // 엄격 모드(기존): 5가지 모두 진짜 데이터여야만 추천
        // RAG 완화 모드: isReal 여부와 무관하게 포함 (pgvector가 이미 선별한 결과)
        if (!relaxValidation && !(validSweet && validAcid && validBody && validTannin && validPrice)) {
            return Integer.MIN_VALUE;
        }

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

    /**
     * 상세 페이지 전용 추천도 계산 — 데이터 불완전 와인 제외 없이 점수 산출.
     *
     * <p>추천 목록용 {@link #score}와 달리 핵심 데이터 부재 시에도 {@code Integer.MIN_VALUE}를
     * 반환하지 않는다. 누락 차원은 Gaussian 합산에서 제외되고,
     * {@code completenessFactor}(0.6 ~ 1.0)가 데이터 충실도 페널티를 적용한다.
     * 가격 데이터가 없으면 {@code sPrice = 0.5}(중립값)으로 처리한다.
     *
     * <p>상황(situation)은 항상 {@code null}로 고정한다.
     * 가중치: S_pref 0.70 / S_price 0.30
     */
    private int scoreForDetail(Wine wine, Preference pref, CalibratedProfile cal) {
        TasteProfile tp = wine.getTasteProfile();

        boolean validSweet  = tp != null && Boolean.TRUE.equals(tp.getIsRealSweetness()) && tp.getSweetness() > 0;
        boolean validAcid   = tp != null && Boolean.TRUE.equals(tp.getIsRealAcidity())   && tp.getAcidity() > 0;
        boolean validBody   = tp != null && Boolean.TRUE.equals(tp.getIsRealBody())      && tp.getBody() > 0;
        boolean validTannin = tp != null && Boolean.TRUE.equals(tp.getIsRealTannin())    && tp.getTannin() > 0;
        boolean validAlc    = Boolean.TRUE.equals(wine.getIsRealAlcoholDegree())
                           && wine.getAlcoholDegree() != null && wine.getAlcoholDegree() > 0;
        int nValid = (validSweet ? 1 : 0) + (validAcid ? 1 : 0)
                   + (validBody ? 1 : 0) + (validTannin ? 1 : 0) + (validAlc ? 1 : 0);

        float wSweet  = validSweet  ? tp.getSweetness() * 0.4f : 0f;
        float wAcid   = validAcid   ? tp.getAcidity()   * 0.4f : 0f;
        float wBody   = validBody   ? tp.getBody()       * 0.4f : 0f;
        float wTannin = validTannin ? tp.getTannin()     * 0.4f : 0f;
        float wAlc    = wine.getAlcoholDegree();

        float uAlc = (pref != null && pref.getAbv() != null) ? pref.getAbv() : T_ALC_DEFAULT;

        float sPref;
        if (nValid == 0) {
            sPref = 0f;
        } else {
            int nSum = 0;
            float sum = 0f;
            if (validSweet  && !Float.isNaN(cal.uHatSweet()))  { sum += gaussian(cal.uHatSweet(),  wSweet,  cal.sigmaSweet());  nSum++; }
            if (validAcid   && !Float.isNaN(cal.uHatAcid()))   { sum += gaussian(cal.uHatAcid(),   wAcid,   cal.sigmaAcid());   nSum++; }
            if (validBody   && !Float.isNaN(cal.uHatBody()))   { sum += gaussian(cal.uHatBody(),   wBody,   cal.sigmaBody());   nSum++; }
            if (validTannin && !Float.isNaN(cal.uHatTannin())) { sum += gaussian(cal.uHatTannin(), wTannin, cal.sigmaTannin()); nSum++; }
            if (validAlc)                                       { sum += gaussian(uAlc,             wAlc,    SIGMA_A);           nSum++; }

            float completenessFactor = 0.6f + 0.4f * (nValid / 5.0f);
            sPref = nSum > 0 ? (sum / nSum) * completenessFactor : 0f;
        }

        boolean validPrice = wine.getPriceAndRating() != null
                && Boolean.TRUE.equals(wine.getPriceAndRating().getIsRealPrice())
                && wine.getPriceAndRating().getPrice() > 0;
        float sPrice = calcSPrice(wine, pref, null, validPrice);

        float total = 0.70f * sPref + 0.30f * sPrice;
        return Math.round(total * 100);
    }

    // ── S_price 상황별 비대칭 가격 점수 ──────────────────────────────────────────

    /**
     * 상황별 비대칭 Gaussian으로 가격 점수를 계산한다.
     *
     * <ul>
     *   <li>혼술: 목표가=uMin, 예산 초과(σ_high=0.2×uMin)에 강한 페널티 / 저렴할수록(σ_low=0.8×uMin) 관대</li>
     *   <li>기념일: 목표가=1.1×uMax, 예산 이하(σ_low=0.25×uMax)는 적당히 허용</li>
     *   <li>파티: 목표가=(uMin+uMax)/2, 양방향 비대칭 적용</li>
     *   <li>상황 없음(null): 대칭 Gaussian, SIGMA_DIR 사용</li>
     * </ul>
     *
     * @return [0,1] 가격 적합도 점수, 또는 0.5 (데이터 없음·선호 미입력)
     */
    private float calcSPrice(Wine wine, Preference pref, DrinkingSituation situation, boolean validPrice) {
        if (!validPrice
                || pref == null
                || pref.getPreferredPriceMin() == null
                || pref.getPreferredPriceMax() == null) {
            return 0.5f;
        }

        float winePrice = wine.getPriceAndRating().getPrice();
        float uMin      = pref.getPreferredPriceMin();
        float uMax      = pref.getPreferredPriceMax();

        if (situation == null) {
            return gaussian(winePrice, (uMin + uMax) / 2f, SIGMA_DIR);
        }

        float tPrice, sigmaLow, sigmaHigh;
        switch (situation) {
            case ALONE -> {
                tPrice    = uMin;
                sigmaLow  = Math.max(1f, 0.8f * uMin);
                sigmaHigh = Math.max(1f, 0.2f * uMin);
            }
            case DATE -> {
                tPrice    = 1.1f * uMax;
                sigmaLow  = Math.max(1f, 0.25f * uMax);
                sigmaHigh = Math.max(1f, 0.2f  * tPrice);
            }
            case PARTY -> {
                tPrice    = (uMin + uMax) / 2f;
                sigmaLow  = Math.max(1f, 0.4f * tPrice);
                sigmaHigh = Math.max(1f, 0.3f * tPrice);
            }
            default -> { return gaussian(winePrice, (uMin + uMax) / 2f, SIGMA_DIR); }
        }

        float sigma = (winePrice <= tPrice) ? sigmaLow : sigmaHigh;
        return gaussian(winePrice, tPrice, sigma);
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
     * Preference (1~5) → W'_i 동일 스케일 (0~2)로 변환.
     * null이면 NaN 반환 (해당 차원 미설정, S_pref 계산 시 제외).
     */
    private float toScale(Integer prefValue) {
        return (prefValue != null) ? (prefValue - 1) / 4f * 2f : Float.NaN;
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

    /**
     * 차원별 개인화 취향 폭: σ̂_i = σ_base · √(1 + δ · Var_i / σ_base²).
     * 긍정 리뷰 없는 차원(n < 1)은 SIGMA_BASE 폴백.
     */
    private float computeSigma(double n, double sumX, double sumX2) {
        double var = variance(n, sumX, sumX2);
        if (var < 0) return SIGMA_BASE;
        return (float)(SIGMA_BASE * Math.sqrt(1.0 + DELTA * var / (SIGMA_BASE * SIGMA_BASE)));
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

/**
 * AI 엔진이 1차 필터링한 후보 와인들 내에서 재정렬을 수행하거나,
 * 후보가 없는 경우(예: 친구만 언급) 전체 DB에서 취향 기반으로 추천을 수행합니다.
 */
public WineQuickRecommendResponse ragReRank(User mainUser, List<User> friends, List<Long> candidateIds) {
    // 1. 본인 취향 프로필
    Preference mainPref = preferenceRepository.findByUserId(mainUser.getId()).orElse(null);
    List<Review> mainReviews = reviewRepository.findAllByUserWithWine(mainUser);
    CalibratedProfile mainProfile = calibrate(mainPref, mainReviews);

    // 2. 모든 구성원의 취향 수집 (본인 + 친구들)
    List<Preference> allPreferences = new ArrayList<>();
    if (mainPref != null) allPreferences.add(mainPref);
    for (User friend : friends) {
        preferenceRepository.findByUserId(friend.getId()).ifPresent(allPreferences::add);
    }

    // 3. 리스크 회피 로직이 적용된 가상 프로필(Group Context) 생성
    Map<String, Object> groupCtx = calculateGroupContext(allPreferences);
    Map<String, Double> targetMap = (Map<String, Double>) groupCtx.get("target_profile");

    // 가상 프로필 수치 적용 (0.4f 곱하여 0~2 스케일로 변환)
    CalibratedProfile blended = new CalibratedProfile(
        targetMap.getOrDefault("sweet", 3.0).floatValue() * 0.4f,
        targetMap.getOrDefault("acid", 3.0).floatValue() * 0.4f,
        targetMap.getOrDefault("body", 3.0).floatValue() * 0.4f,
        targetMap.getOrDefault("tannin", 3.0).floatValue() * 0.4f,
        SIGMA_BASE, SIGMA_BASE, SIGMA_BASE, SIGMA_BASE
    );

    // 4. 추천 대상 와인 목록 확보
    List<Wine> targetWines;
    if (candidateIds != null && !candidateIds.isEmpty()) {
        targetWines = wineRepository.findAllByIdIn(candidateIds);
    } else {
        // 후보가 없으면 전체 와인 중 상위 일부를 샘플링하거나 전체 조회 (성능 고려 필요 시 샘플링)
        targetWines = wineQueryService.getAllWines();
    }

    PriorityQueue<WineScore> minHeap = new PriorityQueue<>(Comparator.comparingInt(WineScore::match));
    for (Wine wine : targetWines) {
        // relaxValidation=true: 데이터 누락 와인도 점수 계산
        int match = score(wine, null, mainPref, blended, true);
        if (match == Integer.MIN_VALUE) continue;
        addToHeap(minHeap, new WineScore(match, wine), 3); // 상위 3개까지 수집
    }

    List<WineRecommendationItem> result = heapToList(minHeap, null);
    
    // [최종 안전장치] 만약 결과가 여전히 비어있다면, 전체 DB에서 무작위 상위 와인 1개라도 강제 반환
    if (result.isEmpty() && targetWines != null && !targetWines.isEmpty()) {
        result = List.of(WineRecommendationItem.of(targetWines.get(0), 85, (DrinkingSituation) null, List.of()));
    }
    
    return WineQuickRecommendResponse.of(result, List.of());
}
/** 여러 취향 프로필의 단순 평균을 구한다 (NaN 무시) */
private CalibratedProfile blendProfiles(List<CalibratedProfile> profiles) {
    if (profiles.size() == 1) return profiles.get(0);
    float[] sums = new float[8];
    int[]   cnts = new int[8];
    for (CalibratedProfile p : profiles) {
        float[] vals = {p.uHatSweet(), p.uHatAcid(), p.uHatBody(), p.uHatTannin(),
                        p.sigmaSweet(), p.sigmaAcid(), p.sigmaBody(), p.sigmaTannin()};
        for (int i = 0; i < 8; i++) {
            if (!Float.isNaN(vals[i])) { sums[i] += vals[i]; cnts[i]++; }
        }
    }
    return new CalibratedProfile(
        cnts[0] > 0 ? sums[0]/cnts[0] : Float.NaN,
        cnts[1] > 0 ? sums[1]/cnts[1] : Float.NaN,
        cnts[2] > 0 ? sums[2]/cnts[2] : Float.NaN,
        cnts[3] > 0 ? sums[3]/cnts[3] : Float.NaN,
        cnts[4] > 0 ? sums[4]/cnts[4] : SIGMA_BASE,
        cnts[5] > 0 ? sums[5]/cnts[5] : SIGMA_BASE,
        cnts[6] > 0 ? sums[6]/cnts[6] : SIGMA_BASE,
        cnts[7] > 0 ? sums[7]/cnts[7] : SIGMA_BASE
    );
}

/**
 * [그룹 취향 결합 로직]
 * 여러 명의 취향 데이터를 분석하여 리스크를 회피하고 공통점을 추출한 그룹 컨텍스트를 생성합니다.
 */
public Map<String, Object> calculateGroupContext(List<Preference> preferences) {
    if (preferences == null || preferences.isEmpty()) return Map.of();

    List<String> constraints = new ArrayList<>();
    Map<String, Double> targetProfile = new HashMap<>();

    // 1. 각 속성별 통계 추출 및 제약 사항 도출
    analyzeAttribute("sweetness", preferences, constraints, targetProfile, "sweet", "당도");
    analyzeAttribute("acidity", preferences, constraints, targetProfile, "acid", "산도");
    analyzeAttribute("body", preferences, constraints, targetProfile, "body", "바디감");
    analyzeAttribute("tannin", preferences, constraints, targetProfile, "tannin", "탄닌");
    Map<String, Object> result = new HashMap<>();
    result.put("target_profile", targetProfile);
    result.put("constraints", constraints);

    // 2. 간단한 요약 텍스트 생성
    String summary = generateGroupSummary(constraints);
    result.put("summary", summary);

    return result;
}

private void analyzeAttribute(String fieldName, List<Preference> prefs, List<String> constraints, 
                             Map<String, Double> target, String key, String label) {
    List<Integer> values = prefs.stream()
            .map(p -> {
                if (fieldName.equals("sweetness")) return p.getSweetness();
                if (fieldName.equals("acidity")) return p.getAcidity();
                if (fieldName.equals("body")) return p.getBody();
                if (fieldName.equals("tannin")) return p.getTannin();
                return null;
            })
            .filter(Objects::nonNull)
            .toList();

    if (values.isEmpty()) return;

    int min = Collections.min(values);
    int max = Collections.max(values);
    double avg = values.stream().mapToInt(Integer::intValue).average().orElse(3.0);

    // 리스크 회피 로직: 한 명이라도 1점(극불호)이면 목표 수치를 낮추고 제약 추가
    if (min <= 1) {
        constraints.add("avoid_high_" + fieldName);
        avg = Math.min(avg, 2.5); // 평균치를 강제로 낮춤 (불호 반영)
    }

    // 취향 충돌 감지
    if (max - min >= 3) {
        constraints.add(fieldName + "_preference_conflict");
    }

    // 공통 선호 감지
    if (avg >= 4.0) {
        constraints.add("unanimous_high_" + fieldName);
    }

    target.put(key, avg);
}

private String generateGroupSummary(List<String> constraints) {
    if (constraints.isEmpty()) return "멤버들의 취향이 대체로 조화롭습니다.";

    StringBuilder sb = new StringBuilder();
    if (constraints.stream().anyMatch(c -> c.startsWith("avoid_"))) {
        sb.append("특정 맛에 민감한 멤버가 있어 리스크를 최소화했습니다. ");
    }
    if (constraints.stream().anyMatch(c -> c.endsWith("_conflict"))) {
        sb.append("멤버 간 취향 차이가 있는 부분은 중간 지점을 고려했습니다.");
    }
    return sb.toString().trim();
}

record WineScore(int match, Wine wine) {}
}

