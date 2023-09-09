package com.github.supercodingfinalprojectbackend.exception.errorcode;

import com.github.supercodingfinalprojectbackend.exception.ApiException;
import org.springframework.http.HttpStatus;

public enum ApiErrorCode implements ErrorCode {
    FAIL_TO_SAVE(HttpStatus.INTERNAL_SERVER_ERROR, "서버 측의 문제로 데이터 저장에 실패했습니다."),
    INVALID_PATH_VARIABLE(HttpStatus.BAD_REQUEST, "path variable이 잘못되었습니다."),
    NOT_AUTHENTICATED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_CONNECTION_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 연결 요청입니다"),
    EXPIRED_JWT(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    INVALID_SIGNATURE_JWT(HttpStatus.UNAUTHORIZED, "토큰의 형식을 만족하지 않습니다."),
    EMPTY_JWT(HttpStatus.UNAUTHORIZED, "토큰이 존재하지 않습니다."),
    UNRELIABLE_JWT(HttpStatus.UNAUTHORIZED, "신뢰할 수 없는 토큰입니다."),
    NOT_FOUND_USER_INFO(HttpStatus.UNAUTHORIZED, "유저 정보를 찾지 못했습니다."),
    FAIL_TO_RECEIVE_KAKAO_TOKEN(HttpStatus.UNAUTHORIZED, "카카오 인증에 실패했습니다."),
    ALREADY_LOGGED_OUT(HttpStatus.OK, "이미 로그아웃 되었습니다."),
    IS_NOT_LOGGED_IN_KAKAO(HttpStatus.BAD_REQUEST, "카카오로 로그인하지 않았습니다."),
    NOT_FOUND_USER(HttpStatus.NOT_FOUND, "존재하지 않는 유저입니다."),
    NOT_FOUND_MENTOR(HttpStatus.NOT_FOUND, "존재하지 않는 멘토입니다."),
    IS_ALREADY_MENTOR(HttpStatus.OK, "이미 멘토로 로그인 중입니다."),
    NOT_FOUND_MENTEE(HttpStatus.NOT_FOUND, "존재하지 않는 멘티입니다."),
    IS_ALREADY_MENTEE(HttpStatus.OK, "이미 멘티로 로그인 중입니다.")
    ;

    ApiErrorCode(HttpStatus status, String message) {
        this.message = message;
        this.status = status.value();
    }
    private final String message;
    private final int status;
    @Override
    public ApiException exception() { return new ApiException(this); }
    @Override
    public String getMessage() { return message; }
    @Override
    public int getStatus() { return status; }
}
