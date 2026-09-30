package com.autotest.test_management_service.domain.vo;

import jakarta.persistence.Embeddable;

import java.util.Objects;

@Embeddable
public record MemberId(Long value) {
    public MemberId {
        Objects.requireNonNull(value, "value");
    }
}