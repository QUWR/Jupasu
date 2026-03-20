package com.a505.jupasu.domain.friend.dto.response;

import com.a505.jupasu.domain.friend.entity.Friend;
import com.a505.jupasu.domain.user.entity.User;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 나에게 온 친구 요청 목록을 위한 응답 DTO
 */
@Getter
public class FriendPendingResponse {
    private Long requestId;
    private Long requesterId;
    private String nickname;
    private String character;
    private Integer reviewCount;
    private LocalDateTime createdAt;

    public FriendPendingResponse(Friend friend, Integer reviewCount) {
        User requester = friend.getRequester();

        this.requestId = friend.getRequestId();
        this.requesterId = requester.getId();
        this.nickname = requester.getNickname();
        this.character = requester.getCharacter();
        this.reviewCount = reviewCount;
        this.createdAt = friend.getCreatedAt();
    }
}