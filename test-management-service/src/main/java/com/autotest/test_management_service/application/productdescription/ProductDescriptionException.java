package com.autotest.test_management_service.application.productdescription;

import org.springframework.http.HttpStatus;

public class ProductDescriptionException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ProductDescriptionException(HttpStatus status, String code, String message) {
        super(message);
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
