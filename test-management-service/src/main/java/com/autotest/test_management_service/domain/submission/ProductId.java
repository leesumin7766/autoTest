package com.autotest.test_management_service.domain.submission;

import java.util.Objects;

public record ProductId(Long value) {
    public ProductId {
        Objects.requireNonNull(value, "value");
    }
}