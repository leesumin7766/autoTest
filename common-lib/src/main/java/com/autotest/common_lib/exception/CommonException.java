package com.autotest.common_lib.exception;

import java.util.Objects;

public class CommonException extends RuntimeException {
    private final BaseErrorCode errorCode;
    public CommonException(BaseErrorCode errorCode) { this(errorCode,null); }
    public CommonException(BaseErrorCode errorCode, Throwable cause) {
        super(Objects.requireNonNull(errorCode).getMessage(),cause);this.errorCode=errorCode;
    }
    public BaseErrorCode getErrorCode() { return errorCode; }
}
