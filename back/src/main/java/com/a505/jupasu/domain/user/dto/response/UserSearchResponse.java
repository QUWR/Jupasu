package com.a505.jupasu.domain.user.dto.response;

import com.a505.jupasu.domain.friend.entity.FriendStatus;
import com.a505.jupasu.domain.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 사용자 검색 결과 정보를 전달하기 위한 응답 DTO
 */
@Getter
@AllArgsConstructor
public class UserSearchResponse {
    private Long userId;
    private String nickname;
    private String character;
    private Integer reviewCount;
    private FriendStatus friendStatus;

    /**
     * User 엔티티를 UserSearchResponse DTO로 변환
     * * @param user 변환할 사용자 엔티티
     * @return 변환된 검색 결과 DTO
     */
    public static UserSearchResponse of(User user, FriendStatus status, Integer reviewCount) {
        return new UserSearchResponse(
                user.getId(),
                user.getNickname(),
                user.getCharacter(),
                reviewCount,
                status
        );
    }
}
