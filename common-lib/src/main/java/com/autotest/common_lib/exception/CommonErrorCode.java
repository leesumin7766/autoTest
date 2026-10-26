package com.autotest.common_lib.exception;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements BaseErrorCode {
    // 400
    INVALID_INPUT_VALUE("입력 내용을 확인해 주세요.", HttpStatus.BAD_REQUEST),
    // 401
    UNAUTHORIZED("인증 정보를 확인하고 다시 접속해 주세요.", HttpStatus.UNAUTHORIZED),
    // 403
    ACCESS_DENIED("이 작업을 수행할 권한이 없습니다.", HttpStatus.FORBIDDEN),
    // 404
    RESOURCE_NOT_FOUND("요청한 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    // 405
    METHOD_NOT_ALLOWED("지원하지 않는 요청입니다.", HttpStatus.METHOD_NOT_ALLOWED),
    // 406
    NOT_ACCEPTABLE("요청한 응답 형식을 제공할 수 없습니다.", HttpStatus.NOT_ACCEPTABLE),
    // 408
    CLIENT_TIMEOUT("요청 대기 시간이 초과되었습니다. 다시 시도해 주세요.", HttpStatus.REQUEST_TIMEOUT),
    // 409
    STATE_CONFLICT("현재 상태에서는 처리할 수 없습니다. 상태를 확인한 뒤 다시 시도해 주세요.", HttpStatus.CONFLICT),
    // 413
    FILE_TOO_LARGE("파일 크기가 업로드 허용 용량을 초과했습니다. 파일 크기를 줄여 주세요.", HttpStatus.PAYLOAD_TOO_LARGE),
    // 415
    UNSUPPORTED_MEDIA_TYPE("지원하지 않는 요청 형식입니다.", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    // 422
    INVALID_CONTENT("자료의 내용이나 크기를 확인해 주세요.", HttpStatus.UNPROCESSABLE_ENTITY),
    // 429
    TOO_MANY_REQUESTS("요청이 많습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.TOO_MANY_REQUESTS),
    // 500
    INTERNAL_SERVER_ERROR("요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.INTERNAL_SERVER_ERROR),
    // 502
    UPSTREAM_UNAVAILABLE("처리 서비스에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.BAD_GATEWAY),
    // 503
    SERVICE_UNAVAILABLE("현재 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.SERVICE_UNAVAILABLE),
    // 504
    REQUEST_TIMEOUT("응답 대기 시간이 초과되었습니다. 처리 상태를 확인한 뒤 다시 시도해 주세요.", HttpStatus.GATEWAY_TIMEOUT);

    private final String message;
    private final HttpStatus status;
    CommonErrorCode(String message, HttpStatus status) { this.message=message; this.status=status; }
    public String getCode() { return name(); }
    public String getMessage() { return message; }
    public HttpStatus getStatus() { return status; }
    public static CommonErrorCode forStatus(int status) {
        for(var code:values())if(code.status.value()==status)return code;
        return status>=500?INTERNAL_SERVER_ERROR:INVALID_INPUT_VALUE;
    }
}
