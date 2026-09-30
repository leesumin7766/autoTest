package com.autotest.test_management_service.domain.submission;

import java.util.Objects;
import java.util.UUID;

public record TestCaseId(UUID value) {
    public TestCaseId {
        Objects.requireNonNull(value, "value");
    }

    public static TestCaseId generate() {
        return new TestCaseId(UUID.randomUUID());
    }
}