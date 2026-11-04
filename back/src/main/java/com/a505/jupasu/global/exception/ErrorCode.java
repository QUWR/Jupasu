package com.a505.jupasu.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 공통 예외
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 문제가 발생했습니다"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    PATH_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
    FORBIDDEN_ACCESS(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // 회원 관련 예외
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "아이디와 비밀번호를 확인해 주세요."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "아이디와 비밀번호를 확인해 주세요."),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "연속 로그인 실패로 계정이 잠겼습니다."),
    EXISTING_EMAIL(HttpStatus.CONFLICT, "이미 존재하는 이메일입니다"),
    EXISTING_NICKNAME(HttpStatus.CONFLICT, "이미 존재하는 닉네임입니다"),
    INVALID_NICKNAME_FORMAT(HttpStatus.BAD_REQUEST, "닉네임은 2~20자의 한글, 영어, 숫자만 가능합니다."),
    INVALID_PASSWORD_FORMAT(HttpStatus.BAD_REQUEST, "비밀번호는 최소 8자, 대문자·숫자·특수문자 각 1개 이상을 포함해야 합니다."),
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."),
    PASSWORD_CONFIRM_NOT_MATCHED(HttpStatus.BAD_REQUEST, "입력하신 새 비밀번호와 일치하지 않습니다."),
    INVALID_SEARCH_KEYWORD(HttpStatus.BAD_REQUEST, "올바른 검색어를 입력해주세요."),

    // 와인 및 도메인 관련 예외
    WINE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 와인입니다."),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 리뷰입니다."),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 작성한 리뷰입니다."),
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "이미 처리되었거나 데이터 제약 조건에 맞지 않는 요청입니다."),
    WINE_DATA_FILE_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "와인 데이터 파일을 찾을 수 없습니다."),
    WINE_DATA_IMPORT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "와인 데이터 적재 중 오류가 발생해 전체를 취소했습니다."),
    IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 업로드에 실패했습니다."),
    PREFERENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 취향 정보입니다."),

    // OTP / 이메일 인증 관련 예외
    OTP_EXPIRED_OR_INVALID(HttpStatus.BAD_REQUEST, "인증 코드가 유효하지 않거나 만료되었습니다."),
    OTP_INVALID(HttpStatus.BAD_REQUEST, "인증 코드가 올바르지 않습니다."),
    OTP_MAX_ATTEMPTS_EXCEEDED(HttpStatus.BAD_REQUEST, "인증 시도 횟수를 초과했습니다. 다시 회원가입을 시도해주세요."),
    SIGNUP_SESSION_EXPIRED(HttpStatus.BAD_REQUEST, "회원가입 세션이 만료되었습니다. 다시 시도해주세요."),
    SIGNUP_EMAIL_SEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "이메일 재전송은 60초 후에 가능합니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "이메일 인증을 먼저 완료해주세요."),
    PASSWORD_RESET_NOT_FOUND(HttpStatus.BAD_REQUEST, "이메일을 확인해주세요."),

    // 친구 관련 예외
    SELF_FRIEND_REQUEST(HttpStatus.BAD_REQUEST, "친구 요청은 타인에게만 가능합니다."),
    ALREADY_FRIEND_REQUESTED(HttpStatus.BAD_REQUEST, "이미 친구 요청을 보낸 유저 입니다."),
    FRIEND_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "친구 요청이 존재하지 않습니다."),
    INVALID_FRIEND_STATUS(HttpStatus.BAD_REQUEST, "잘못된 친구 상태 변경 요청입니다. 수락 또는 거절만 가능합니다."),
    FRIEND_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 친구가 존재하지 않습니다."),

    // 인증 관련 예외
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 만료되었습니다. 다시 로그인해주세요."),

    // 상황 관련 예외
    SITUATION_NOT_FOUND(HttpStatus.BAD_REQUEST, "만족하는 상황이 존재하지 않습니다."),

    // AI 관련 예외
    AI_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AI 서버 처리 중 오류가 발생했습니다."),
    AI_SERVER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 서버에 연결할 수 없습니다."),
    AI_PROCESSING_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI 서버 응답 시간이 초과되었습니다.");

    private final HttpStatus status;
    private final String message;
}
