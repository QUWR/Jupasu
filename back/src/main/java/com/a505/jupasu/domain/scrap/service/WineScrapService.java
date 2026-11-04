package com.a505.jupasu.domain.scrap.service;


import com.a505.jupasu.domain.scrap.dto.ScrapListResponse;
import com.a505.jupasu.domain.scrap.dto.ScrapToggleResponse;
import com.a505.jupasu.domain.scrap.entity.WineScrap;
import com.a505.jupasu.domain.scrap.repository.WineScrapRepository;
import com.a505.jupasu.domain.user.entity.User;
import com.a505.jupasu.domain.user.repository.UserRepository;
import com.a505.jupasu.domain.wine.entity.Wine;
import com.a505.jupasu.domain.wine.repository.WineRepository;
import com.a505.jupasu.global.exception.CustomException;
import com.a505.jupasu.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class WineScrapService {

    private final WineScrapRepository wineScrapRepository;
    private final WineRepository wineRepository;
    private final UserRepository userRepository;

    /**
     * 찜 토글.
     * userId는 JWT 필터에서 이미 검증된 값이라 프록시로 연결하고, wineId는 경로 값이라 존재 여부만 확인한다.
     * 동시에 두 번 찜 요청이 들어오면 UNIQUE(user_id, wine_id)에 걸려 409로 응답한다 (중복 저장 방지).
     */
    public ScrapToggleResponse toggleScrap(Long userId, Long wineId) {
        if (!wineRepository.existsById(wineId)) {
            throw new CustomException(ErrorCode.WINE_NOT_FOUND);
        }
        User user = userRepository.getReferenceById(userId);
        Wine wine = wineRepository.getReferenceById(wineId);

        Optional<WineScrap> existingScrap = wineScrapRepository.findByUserIdAndWineId(userId, wineId);

        if (existingScrap.isPresent()) {
            wineScrapRepository.delete(existingScrap.get());
            return ScrapToggleResponse.builder()
                    .isScrapped(false)
                    .build();
        } else {
            WineScrap newScrap = WineScrap.builder()
                    .user(user)
                    .wine(wine)
                    .build();
            wineScrapRepository.save(newScrap);

            return ScrapToggleResponse.builder()
                    .isScrapped(true)
                    .build();
        }
    }

    /**
     * 사용자의 스크랩(찜) 목록을 조회하여 DTO 리스트로 반환
     * @param user 현재 로그인한 사용자 엔티티
     * @return 마이페이지 UI 요구사항에 맞춘 스크랩 와인 정보 목록
     */
    @Transactional(readOnly = true)
    public Page<ScrapListResponse> getMyScrapList(User user, Pageable pageable) {
        return wineScrapRepository.findAllByUserWithWine(user, pageable)
                .map(ScrapListResponse::from);
    }
}
