package com.a505.jupasu.domain.friend.service;

import com.a505.jupasu.domain.friend.dto.request.FriendResponseRequest;
import com.a505.jupasu.domain.friend.dto.response.FriendListResponse;
import com.a505.jupasu.domain.friend.dto.response.FriendPendingResponse;
import com.a505.jupasu.domain.friend.entity.Friend;
import com.a505.jupasu.domain.friend.entity.FriendStatus;
import com.a505.jupasu.domain.friend.repository.FriendRepository;
import com.a505.jupasu.domain.reviews.repository.ReviewRepository;
import com.a505.jupasu.domain.user.entity.User;
import com.a505.jupasu.domain.user.repository.UserRepository;
import com.a505.jupasu.global.exception.CustomException;
import com.a505.jupasu.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 친구와 관련된 비즈니스 로직을 처리하는 서비스 클래스
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FriendService {

    private final FriendRepository friendRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;

    /**
     * ID를 통해 상대방을 찾아 친구 요청 전송
     *
     * @param requester        친구 요청을 보내는 사용자(본인) 엔티티
     * @param receiverId 친구 요청을 받을 상대방의 ID
     * @throws CustomException 대상 유저를 찾을 수 없는 경우 (USER_NOT_FOUND) 발생
     * @throws CustomException 본인에게 친구 신청을 하는 경우 (SELF_FRIEND_REQUEST) 발생
     * @throws CustomException 이미 친구 신청이 진행 중이거나 친구인 경우 (ALREADY_FRIEND_REQUESTED) 발생
     */
    @Transactional
    public void inviteFriend(User requester, Long receiverId) {
        // 수신자 존재 여부 확인
        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 자기 자신에게 신청하는지 확인
        if (requester.equals(receiver)) {
            throw new CustomException(ErrorCode.SELF_FRIEND_REQUEST);
        }

        // 이미 요청을 보낸 유저인지 확인
        if (friendRepository.findByRequesterAndReceiver(requester, receiver).isPresent()) {
            throw new CustomException(ErrorCode.ALREADY_FRIEND_REQUESTED);
        }

        Friend friend = Friend.builder()
                .requester(requester)
                .receiver(receiver)
                .status(FriendStatus.PENDING)
                .build();

        friendRepository.save(friend);
    }

    /**
     * 친구 요청에 대해 수락 또는 거절 응답을 처리
     *
     * @param loginUser 현재 로그인한 사용자
     * @param request   친구 요청 ID와 응답 상태를 담은 DTO
     * @throws CustomException 해당 친구 요청을 찾을 수 없는 경우 발생
     * @throws CustomException 수신자가 본인이 아닌 경우(권한 없음) 발생
     * @throws CustomException ACCEPTED, REJECTED 외의 다른 값이 들어올 경우 발생
     */
    @Transactional
    public void respondToFriendRequest(User loginUser, FriendResponseRequest request) {
        // 친구 요청 데이터 조회
        Friend friend = friendRepository.findById(request.getRequestId())
                .orElseThrow(() -> new CustomException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));

        // 로그인 유저와 요청을 받은 사람 비교
        if (!friend.getReceiver().getId().equals(loginUser.getId())) {
            throw new CustomException(ErrorCode.FORBIDDEN_ACCESS);
        }

        if (request.getStatus() == FriendStatus.ACCEPTED) {
            friend.accept();
        } else if (request.getStatus() == FriendStatus.REJECTED) {
            friendRepository.delete(friend);
        } else {
            throw new CustomException(ErrorCode.INVALID_FRIEND_STATUS);
        }
    }

    /**
     * 친구 관계에서 본인을 제외한 상대방 유저 객체를 반환
     * * @param me 현재 로그인한 사용자 엔티티
     * @return 관계에 참여한 다른 사용자(상대방) 엔티티
     */
    @Transactional(readOnly = true)
    public List<FriendListResponse> getFriendList(User loginUser) {
        List<Friend> acceptedFriends = friendRepository.findAllByRequesterAndStatusOrReceiverAndStatus(
                loginUser, FriendStatus.ACCEPTED, loginUser, FriendStatus.ACCEPTED
        );

        List<Long> friendIds = acceptedFriends.stream()
                .map(f -> f.getOtherUser(loginUser).getId())
                .collect(Collectors.toList());
    
        java.util.Map<Long, Long> counts = new java.util.HashMap<>();
        if (!friendIds.isEmpty()) {
            reviewRepository.countReviewsByUserIds(friendIds).forEach(obj -> {
                counts.put(((Number) obj[0]).longValue(), ((Number) obj[1]).longValue());
            });
        }
    
        return acceptedFriends.stream()
                .map(friend -> {
                    User other = friend.getOtherUser(loginUser);
                    Integer reviewCount = counts.getOrDefault(other.getId(), 0L).intValue();
                    return new FriendListResponse(friend, loginUser, reviewCount);
                })
                .collect(Collectors.toList());
    }

    /**
     * 나에게 도착한 친구 신청 대기(PENDING) 목록을 조회
     *
     * @param loginUser 현재 로그인한 사용자(수신자) 엔티티
     * @return 나에게 신청을 보낸 유저들의 정보와 신청 시각이 담긴 DTO 리스트
     */
    @Transactional(readOnly = true)
    public List<FriendPendingResponse> getPendingFriendList(User loginUser) {
        List<Friend> pendingRequests = friendRepository.findAllByReceiverAndStatus(loginUser, FriendStatus.PENDING);
    
        List<Long> requesterIds = pendingRequests.stream()
                .map(f -> f.getRequester().getId())
                .collect(Collectors.toList());
    
        java.util.Map<Long, Long> counts = new java.util.HashMap<>();
        if (!requesterIds.isEmpty()) {
            reviewRepository.countReviewsByUserIds(requesterIds).forEach(obj -> {
                counts.put(((Number) obj[0]).longValue(), ((Number) obj[1]).longValue());
            });
        }
    
        return pendingRequests.stream()
                .map(f -> {
                    Integer count = counts.getOrDefault(f.getRequester().getId(), 0L).intValue();
                    return new FriendPendingResponse(f, count);
                })
                .collect(Collectors.toList());
    }

    /**
     * 기존의 친구 관계를 끊거나, 보낸 친구 신청을 취소
     *
     * @param loginUser 현재 로그인한 사용자 엔티티
     * @param requestId  삭제할 친구 관계의 고유 식별자(PK)
     * @throws CustomException 해당 친구 관계가 존재하지 않을 경우 발생 (FRIEND_REQUEST_NOT_FOUND)
     * @throws CustomException 삭제 권한이 없는 경우(관계 당사자가 아님) 발생 (ACCESS_DENIED)
     */
    @Transactional
    public void deleteFriend(User loginUser, Long requestId) {
        Friend friend = friendRepository.findById(requestId)
                .orElseThrow(() -> new CustomException(ErrorCode.FRIEND_NOT_FOUND));

        if (!friend.getReceiver().getId().equals(loginUser.getId()) &&
            !friend.getRequester().getId().equals(loginUser.getId())) {
            throw new CustomException(ErrorCode.FORBIDDEN_ACCESS);
        }

        friendRepository.delete(friend);
    }
}
