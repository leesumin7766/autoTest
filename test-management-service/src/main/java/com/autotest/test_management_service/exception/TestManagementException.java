package com.autotest.test_management_service.exception;
import com.autotest.common_lib.exception.*;
import org.springframework.http.HttpStatus;

public class TestManagementException extends CommonException {
    public TestManagementException(TestManagementErrorCode code) { super(code); }
    public TestManagementException(TestManagementErrorCode code,Throwable cause) { super(code,cause); }
    protected TestManagementException(HttpStatus status,String code,String internalDetail) {
        super(resolve(status,code),new IllegalStateException(internalDetail));
    }
    private static BaseErrorCode resolve(HttpStatus status,String code) {
        try { var known=TestManagementErrorCode.valueOf(code);if(known.getStatus()==status)return known; }
        catch(IllegalArgumentException ignored) { /* Legacy code: expose only a safe status message. */ }
        String safe=code!=null&&code.matches("[A-Z][A-Z0-9_]{0,99}")?code:CommonErrorCode.forStatus(status.value()).getCode();
        return new LegacyCode(safe,CommonErrorCode.forStatus(status.value()).getMessage(),status);
    }
    private record LegacyCode(String getCode,String getMessage,HttpStatus getStatus) implements BaseErrorCode {}
}
