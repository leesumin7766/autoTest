package com.autotest.auth_service;

import com.autotest.common_lib.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

enum AuthError implements BaseErrorCode {
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED,"로그인이 필요합니다."),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN,"관리자 권한이 필요합니다."),
    AUTH_CSRF_REJECTED(HttpStatus.FORBIDDEN,"허용되지 않은 웹 요청입니다."),
    AUTH_USER_EXISTS(HttpStatus.CONFLICT,"이미 등록된 사용자입니다."),
    AUTH_INVALID_INPUT(HttpStatus.BAD_REQUEST,"입력 내용을 확인해 주세요."),
    AUTH_USER_NOT_FOUND(HttpStatus.NOT_FOUND,"사용자를 찾을 수 없습니다.");
    private final HttpStatus status;private final String message;
    AuthError(HttpStatus status,String message) { this.status=status;this.message=message; }
    public String getCode() { return name(); }
    public String getMessage() { return message; }
    public HttpStatus getStatus() { return status; }
}
