package com.a505.jupasu.domain.wine.controller;

import com.a505.jupasu.domain.wine.service.WineDataInitService;
import com.a505.jupasu.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/admin/wines")
@RequiredArgsConstructor
public class WineDataInitController {

    private final WineDataInitService wineDataInitService;

    @PostMapping("/init-data")
    public ApiResponse<WineDataInitService.ImportResult> initWineData() {
        log.info("수동 데이터 초기화 시작...");
        long startTime = System.currentTimeMillis();

        // 실패하면 서비스가 예외를 던져 전체 롤백되고, 전역 예외 처리기가 오류로 응답한다
        WineDataInitService.ImportResult result = wineDataInitService.importWineDataFromJson();

        long endTime = System.currentTimeMillis();
        log.info("초기화 완료! 소요 시간: {}ms", (endTime - startTime));
        return ApiResponse.success("와인 데이터 파싱 및 DB 저장 성공", result);
    }
}
