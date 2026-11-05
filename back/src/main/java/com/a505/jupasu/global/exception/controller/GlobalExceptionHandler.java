package com.a505.jupasu.global.exception.controller;

import com.a505.jupasu.global.exception.CustomException;
import com.a505.jupasu.global.exception.ErrorCode;
import com.a505.jupasu.global.exception.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * NullPointerException 발생 시 처리하는 핸들러
     */
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ErrorResponse> handleNullPointerException(NullPointerException e) {
        log.error("NullPointer Exception 발생: ", e);

        ErrorResponse response = ErrorResponse.builder()
                .code(ErrorCode.INTERNAL_SERVER_ERROR)
                .message("서버 내부에서 데이터 참조 오류(NullPointer)가 발생했습니다.")
                .build();

        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus()).body(response);
    }

    /**
     * CustomException 이외의 예상치 못한 모든 에러(Exception)를 처리하는 핸들러
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception e) {
        log.error("Internal Server Error 발생: ", e);

        ErrorResponse response = ErrorResponse.builder()
                .code(ErrorCode.INTERNAL_SERVER_ERROR)
                .message(ErrorCode.INTERNAL_SERVER_ERROR.getMessage())
                .build();

        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus()).body(response);
    }

    /**
     * 직접 정의한 CustomException을 처리하는 핸들러
     */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();

        String message = errorCode.getMessage();

        // detail이 있을 때만 뒤에 추가 ex) "인증 코드가 올바르지 않습니다. (남은 시도: 3회)"
        if (e.getDetail() != null) {
            message = message + " " + e.getDetail();
        }

        log.error("CustomException : {}", message);

        ErrorResponse response = ErrorResponse.builder()
                .code(errorCode)
                .message(message)
                .build();
        return ResponseEntity.status(errorCode.getStatus()).body(response);
    }

    /** @Valid 검증 실패 처리 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse(ErrorCode.INVALID_REQUEST.getMessage());

        log.warn("Validation 실패: {}", message);

        ErrorResponse response = ErrorResponse.builder()
                .code(ErrorCode.INVALID_REQUEST)
                .message(message)
                .build();
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getStatus()).body(response);
    }

    /**
     * 존재하지 않는 경로 요청 처리.
     * 전용 핸들러가 없으면 Exception 핸들러가 잡아 모든 잘못된 URL이 500으로 응답되던 문제를 막는다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        ErrorResponse response = ErrorResponse.builder()
                .code(ErrorCode.PATH_NOT_FOUND)
                .message(ErrorCode.PATH_NOT_FOUND.getMessage())
                .build();
        return ResponseEntity.status(ErrorCode.PATH_NOT_FOUND.getStatus()).body(response);
    }

    /**
     * 클라이언트가 요청을 잘못 보낸 경우(JSON 형식 오류, 필수 파라미터·헤더 누락, 타입 불일치) 처리.
     * 전용 핸들러가 없으면 Exception 핸들러가 잡아 500으로 응답되던 문제를 막는다.
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        log.warn("Bad request : {}", e.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .code(ErrorCode.INVALID_REQUEST)
                .message(ErrorCode.INVALID_REQUEST.getMessage())
                .build();
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getStatus()).body(response);
    }

    /** 지원하지 않는 HTTP 메서드 (예: GET만 있는 경로에 DELETE) → 405 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        ErrorResponse response = ErrorResponse.builder()
                .code(ErrorCode.METHOD_NOT_ALLOWED)
                .message(ErrorCode.METHOD_NOT_ALLOWED.getMessage())
                .build();
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getStatus()).body(response);
    }

    /**
     * DB 제약 조건 위반(UNIQUE 중복, FK 위반 등) 처리.
     * 서비스에서 미리 확인하지 못한 경쟁 상황의 마지막 방어선으로, 500 대신 409로 응답한다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("DataIntegrityViolation : {}", e.getMostSpecificCause().getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .code(ErrorCode.DATA_INTEGRITY_VIOLATION)
                .message(ErrorCode.DATA_INTEGRITY_VIOLATION.getMessage())
                .build();
        return ResponseEntity.status(ErrorCode.DATA_INTEGRITY_VIOLATION.getStatus()).body(response);
    }

    /**
     * AI 서버(WebClient는 AI 서버 호출에만 사용) 연결 실패 → 503.
     * 전용 핸들러가 없어 AI 서버가 꺼져 있을 때 모든 AI 기능이 일반 500으로 응답되던 문제를 막는다.
     */
    @ExceptionHandler(WebClientRequestException.class)
    public ResponseEntity<ErrorResponse> handleAiServerUnavailable(WebClientRequestException e) {
        log.error("AI 서버 연결 실패: {}", e.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .code(ErrorCode.AI_SERVER_UNAVAILABLE)
                .message(ErrorCode.AI_SERVER_UNAVAILABLE.getMessage())
                .build();
        return ResponseEntity.status(ErrorCode.AI_SERVER_UNAVAILABLE.getStatus()).body(response);
    }

    /** AI 서버가 오류 응답(4xx/5xx)을 준 경우 */
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ErrorResponse> handleAiServerError(WebClientResponseException e) {
        log.error("AI 서버 오류 응답: {} {}", e.getStatusCode(), e.getResponseBodyAsString());

        ErrorResponse response = ErrorResponse.builder()
                .code(ErrorCode.AI_SERVER_ERROR)
                .message(ErrorCode.AI_SERVER_ERROR.getMessage())
                .build();
        return ResponseEntity.status(ErrorCode.AI_SERVER_ERROR.getStatus()).body(response);
    }

    /** Redis 연결 장애 시 처리 (Fault Tolerance) */
    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<ErrorResponse> handleRedisFailure(RedisConnectionFailureException ex) {

        log.error("[CRITICAL] Redis cluster is unreachable. Authentication flow degraded: {}", ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .code(ErrorCode.INTERNAL_SERVER_ERROR)
                .message("인증 서버가 혼잡하여 현재 요청을 처리할 수 없습니다. 잠시 후 다시 시도해주세요.")
                .build();

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}