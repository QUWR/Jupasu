package com.a505.jupasu.domain.user.service;

import com.a505.jupasu.domain.friend.entity.Friend;
import com.a505.jupasu.domain.friend.entity.FriendStatus;
import com.a505.jupasu.domain.friend.repository.FriendRepository;
import com.a505.jupasu.domain.scrap.repository.WineScrapRepository;
import com.a505.jupasu.domain.reviews.repository.ReviewRepository;

import com.a505.jupasu.domain.user.dto.request.UserUpdateRequest;
import com.a505.jupasu.domain.user.dto.request.WithdrawRequest;
import com.a505.jupasu.domain.user.dto.response.UserMyPageResponse;
import com.a505.jupasu.domain.user.dto.response.UserSearchResponse;
import com.a505.jupasu.domain.user.entity.User;
import com.a505.jupasu.domain.user.repository.UserRepository;
import com.a505.jupasu.domain.auth.util.AuthUtils;
import com.a505.jupasu.global.exception.CustomException;
import com.a505.jupasu.global.exception.ErrorCode;
import com.a505.jupasu.global.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.a505.jupasu.domain.auth.util.AuthRedisConstants.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * User 도메인의 비즈니스 로직을 처리하는 서비스 클래스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final FriendRepository friendRepository;
    private final ReviewRepository reviewRepository;
    private final WineScrapRepository wineScrapRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RedisService redisService;
    private final AuthUtils authUtils;

    /**
     * 현재 로그인한 사용자의 마이페이지 정보를 조회
     *
     * @param loginUser 인증된 현재 사용자
     * @return 마이페이지 프로필 및 통계 응답 DTO
     */
    public UserMyPageResponse getMyPageInfo(User loginUser) {
        long wishlistCount = wineScrapRepository.countByUser(loginUser);
        long friendCount = friendRepository.countByUserAndStatus(loginUser, FriendStatus.ACCEPTED);
        long reviewCount = reviewRepository.countByUser(loginUser);
        return UserMyPageResponse.from(loginUser, wishlistCount, friendCount, reviewCount);
    }

    /**
     * 사용자의 마이페이지 프로필 정보를 수정
     *
     * @param loginUser 인증된 현재 사용자 엔티티
     * @param request 수정할 닉네임, 캐릭터, 비밀번호 정보를 담은 DTO
     * @throws CustomException 사용자를 찾을 수 없거나 중복된 닉네임인 경우 발생
     */
    @Transactional
    public void updateMyPageInfo(User loginUser, UserUpdateRequest request) {
        User user = userRepository.findById(loginUser.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 닉네임이 변경된 경우에만 중복 검증 및 업데이트 수행
        if (request.getNickname() != null && !request.getNickname().equals(user.getNickname())) {
            validateNickname(request.getNickname());
            user.updateNickname(request.getNickname());
        }

        // 캐릭터 정보 업데이트
        if(request.getCharacter() != null && !request.getCharacter().equals(user.getCharacter())) {
            user.updateCharacter(request.getCharacter());
        }

        // 비밀번호 업데이트
        if(request.getNewPassword() != null) {
            updatePassword(user, request.getCurrentPassword(), request.getNewPassword(), request.getConfirmNewPassword());
        }
    }

    /**
     * 사용자의 현재 비밀번호를 확인하고, 새 비밀번호의 유효성 및 일치 여부를 검증한 뒤 암호화하여 업데이트
     *
     * @param user           비밀번호를 변경할 대상 사용자 엔티티
     * @param currentPw      본인 확인을 위해 입력받은 현재 비밀번호 (평문)
     * @param newPw          새로 설정할 비밀번호 (평문, 대문자/숫자/특수문자 포함 8자 이상)
     * @param confirmNewPw   입력한 새 비밀번호가 정확한지 확인하기 위한 재입력 값
     * @throws CustomException 현재 비밀번호가 데이터베이스에 저장된 해시값과 일치하지 않는 경우 (INVALID_CURRENT_PASSWORD) 발생
     * @throws CustomException 새 비밀번호가 정해진 보안 규칙을 준수하지 않는 경우 (INVALID_PASSWORD_FORMAT) 발생
     * @throws CustomException 새 비밀번호와 재입력한 비밀번호가 서로 일치하지 않는 경우 (PASSWORD_CONFIRM_NOT_MATCHED) 발생
     */
    private void updatePassword(User user, String currentPw, String newPw, String confirmNewPw) {
        // 현재 비밀번호 일치 확인
        if (currentPw == null || !passwordEncoder.matches(currentPw, user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }

        // 새 비밀번호 유효성 검증
        validatePassword(newPw);

        // 새 비밀번호와 재입력 확인
        if (!newPw.equals(confirmNewPw)) {
            throw new CustomException(ErrorCode.PASSWORD_CONFIRM_NOT_MATCHED);
        }

        // 새 비밀번호 저장
        String encodedPassword = passwordEncoder.encode(newPw);
        user.updatePassword(encodedPassword);
    }

    /**
     * 닉네임 중복 및 유효성을 검사하는 공통 메서드
     * @param nickname 검사할 닉네임
     * @throws CustomException 닉네임 형식이 올바르지 않은 경우 (INVALID_NICKNAME_FORMAT) 발생
     * @throws CustomException 중복된 경우 DUPLICATE_NICKNAME 발생
     */
    private void validateNickname(String nickname) {
        String nicknameRegex = "^[가-힣a-zA-Z0-9]{2,20}$";

        // 닉네임 유효성 검사
        if (nickname == null || !nickname.matches(nicknameRegex)) {
            throw new CustomException(ErrorCode.INVALID_NICKNAME_FORMAT);
        }

        // 닉네임 중복 검사
        if (userRepository.existsByNickname(nickname)) {
            throw new CustomException(ErrorCode.EXISTING_NICKNAME);
        }
    }

    /**
     * 비밀번호의 유효성(형식)을 검증
     *
     * @param password 검증할 새 비밀번호 (최소 8자, 대문자, 숫자, 특수문자 각 1개 이상 포함)
     * @throws CustomException 비밀번호 형식이 올바르지 않은 경우 (INVALID_PASSWORD_FORMAT) 발생
     */
    private void validatePassword(String password) {
        String passwordRegex = "^(?=.*[A-Z])(?=.*[0-9])(?=.*[\\p{Punct}]).{8,}$";

        if(password == null || !password.matches(passwordRegex)) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD_FORMAT);
        }
    }

    /**
     * 닉네임으로 유저를 검색하고, 현재 로그인한 유저와의 친구 상태를 포함하여 반환
     * * @param nickname    검색 키워드
     * @param currentUser 현재 로그인한 유저 엔티티
     * @return 친구 상태가 포함된 검색 결과 리스트
     */
    public List<UserSearchResponse> searchUsers(String nickname, User currentUser) {
        List<User> searchedUsers = userRepository.findByNicknameContainingAndIdNot(nickname, currentUser.getId());

        List<Friend> myRelations = friendRepository.findAllByRequesterOrReceiver(currentUser);
    
        List<Long> searchedUserIds = searchedUsers.stream()
                .map(User::getId)
                .collect(Collectors.toList());
    
        java.util.Map<Long, Long> counts = new java.util.HashMap<>();
        if (!searchedUserIds.isEmpty()) {
            reviewRepository.countReviewsByUserIds(searchedUserIds).forEach(obj -> {
                counts.put(((Number) obj[0]).longValue(), ((Number) obj[1]).longValue());
            });
        }
    
        return searchedUsers.stream()
                .map(user -> {
                    FriendStatus status = determineStatus(user, myRelations);
                    Integer reviewCount = counts.getOrDefault(user.getId(), 0L).intValue();
                    return UserSearchResponse.of(user, status, reviewCount);
                })
                .collect(Collectors.toList());
    }

    /**
     * 특정 유저와 나의 관계를 정의
     */
    private FriendStatus determineStatus(User targetUser, List<Friend> myRelations) {
        return myRelations.stream()
                .filter(f -> f.getRequester().equals(targetUser) || f.getReceiver().equals(targetUser))
                .findFirst()
                .map(Friend::getStatus) // PENDING 또는 ACCEPTED
                .orElse(FriendStatus.NONE); // 관계 없음
    }

    /**
     * 회원 탈퇴
     * 유저와 연관된 모든 데이터(친구, 리뷰, 스크랩)를 삭제하고
     * 보안 토큰 - RT 삭제, AT 블랙리스트 등록
     *
     * @param loginUser   인증된 현재 사용자
     * @param accessToken 현재 사용 중인 Access Token (블랙리스트 등록용)
     * @param request     비밀번호 확인 정보를 담은 DTO
     */
    @Transactional
    public void withdraw(User loginUser, String accessToken, WithdrawRequest request) {
        User user = userRepository.findById(loginUser.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }

        // 1. 연관 데이터 삭제
        wineScrapRepository.deleteByUser(user);
        friendRepository.deleteByRequesterOrReceiver(user, user);
        reviewRepository.deleteByUser(user);

        // 2. Redis 토큰 폐기
        // Refresh Token 삭제
        redisService.delete(RT_PREFIX + user.getEmail());

        // Access Token 블랙리스트 등록
        AuthUtils.TokenInfo tokenInfo = authUtils.parseTokenForLogout(accessToken);
        if (!tokenInfo.isExpired() && tokenInfo.jti() != null && tokenInfo.remainingTime() > 0) {
            redisService.set(BLACKLIST_PREFIX + tokenInfo.jti(), "withdraw", tokenInfo.remainingTime() / 1000);
        }

        // 3. User 삭제
        userRepository.delete(user);

        log.info("회원 탈퇴 완료: id={}, email={}", user.getId(), user.getEmail());
    }
}
