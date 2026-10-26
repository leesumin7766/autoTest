package com.autotest.test_management_service.application.productdescription;

import org.springframework.http.HttpStatus;

public class ProductDescriptionException extends com.autotest.test_management_service.exception.TestManagementException {
    private final HttpStatus status;
    private final String code;

    public ProductDescriptionException(HttpStatus status, String code, String message) {
        super(status,code,message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
