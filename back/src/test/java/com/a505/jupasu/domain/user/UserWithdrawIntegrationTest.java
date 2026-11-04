package com.a505.jupasu.domain.user;

import com.a505.jupasu.domain.reviews.dto.ReviewCreateRequest;
import com.a505.jupasu.domain.reviews.service.ReviewService;
import com.a505.jupasu.domain.user.dto.request.WithdrawRequest;
import com.a505.jupasu.domain.user.entity.User;
import com.a505.jupasu.domain.user.service.UserService;
import com.a505.jupasu.global.security.jwt.JwtTokenProvider;
import com.a505.jupasu.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserWithdrawIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private UserService userService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Test
    @DisplayName("회원 탈퇴 시 그 사용자가 남긴 별점이 와인 평점 집계에서도 빠진다")
    void withdraw_removesRatingsFromWineSummary() {
        long wineA = insertTestWine("탈퇴평점와인A", 30000);
        long wineB = insertTestWine("탈퇴평점와인B", 30000);
        User stayingUser = createUser();
        reviewService.createReview(stayingUser.getId(), wineA, new ReviewCreateRequest(2, "남는 리뷰"));

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User leavingUser = userRepository.save(User.builder()
                .email("leave-" + suffix + "@example.com")
                .passwordHash(passwordEncoder.encode("Password1!"))
                .nickname("leave" + suffix)
                .character("cat")
                .build());
        reviewService.createReview(leavingUser.getId(), wineA, new ReviewCreateRequest(5, "탈퇴할 사람 리뷰"));
        reviewService.createReview(leavingUser.getId(), wineB, new ReviewCreateRequest(4, "탈퇴할 사람 리뷰"));

        WithdrawRequest request = new WithdrawRequest();
        ReflectionTestUtils.setField(request, "password", "Password1!");
        User loginUser = userRepository.findById(leavingUser.getId()).orElseThrow();
        userService.withdraw(loginUser, jwtTokenProvider.createAccessToken(loginUser.getEmail()), request);

        Map<String, Object> a = jdbcTemplate.queryForMap(
                "SELECT user_review_count, user_rating_sum, user_star5_count, display_review_count, display_average_rating FROM wine WHERE id = ?", wineA);
        assertThat(((Number) a.get("user_review_count")).intValue()).isEqualTo(1);       // 남은 사용자 1명
        assertThat(((Number) a.get("user_rating_sum")).intValue()).isEqualTo(2);
        assertThat(((Number) a.get("user_star5_count")).intValue()).isZero();
        assertThat(((Number) a.get("display_review_count")).intValue()).isEqualTo(1001);
        assertThat(((Number) a.get("display_average_rating")).doubleValue())
                .isCloseTo((4.0 * 1000 + 2) / 1001.0, org.assertj.core.data.Offset.offset(1e-9));

        Map<String, Object> b = jdbcTemplate.queryForMap(
                "SELECT user_review_count, user_rating_sum, display_average_rating FROM wine WHERE id = ?", wineB);
        assertThat(((Number) b.get("user_review_count")).intValue()).isZero();
        assertThat(((Number) b.get("user_rating_sum")).intValue()).isZero();
        assertThat(((Number) b.get("display_average_rating")).doubleValue()).isCloseTo(4.0, org.assertj.core.data.Offset.offset(1e-9));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE user_id = ?", Integer.class, leavingUser.getId())).isZero();
    }
}
