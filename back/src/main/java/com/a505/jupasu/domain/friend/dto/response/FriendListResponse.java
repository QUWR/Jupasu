package com.a505.jupasu.domain.friend.dto.response;

import com.a505.jupasu.domain.friend.entity.Friend;
import com.a505.jupasu.domain.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 친구 목록 조회를 위한 응답 DTO
 */
@Getter
@AllArgsConstructor
public class FriendListResponse {
    private Long requestId;
    private Long friendId;
    private String nickname;
    private String character;
    private Integer reviewCount;
    private LocalDateTime friendSince;

    /**
     * 엔티티를 DTO로 변환하는 생성자
     *
     * @param friend 친구 관계 엔티티
     * @param me     현재 로그인한 사용자 엔티티 (상대방 판별용)
     */
    public FriendListResponse(Friend friend, User me, Integer reviewCount) {
        User other = friend.getOtherUser(me);

        this.requestId = friend.getRequestId();
        this.friendId = friend.getOtherUser(me).getId();
        this.nickname = other.getNickname();
        this.character = other.getCharacter();
        this.reviewCount = reviewCount;
        this.friendSince = friend.getCreatedAt();
    }
}
