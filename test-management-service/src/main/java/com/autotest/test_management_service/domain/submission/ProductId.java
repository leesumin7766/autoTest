package com.autotest.test_management_service.domain.submission;

import jakarta.persistence.Embeddable;

import java.util.Objects;

@Embeddable
public record ProductId(Long value) {
    public ProductId {
        Objects.requireNonNull(value, "value");
    }
}