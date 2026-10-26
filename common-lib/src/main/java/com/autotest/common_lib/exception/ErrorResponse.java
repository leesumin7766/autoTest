package com.autotest.common_lib.exception;

public record ErrorResponse(String code, String message, String traceId) {
    public static ErrorResponse of(BaseErrorCode code,String traceId) {
        return new ErrorResponse(code.getCode(),code.getMessage(),traceId);
    }
}
