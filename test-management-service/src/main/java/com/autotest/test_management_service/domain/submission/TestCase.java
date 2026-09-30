package com.autotest.test_management_service.domain.submission;

import java.util.Objects;

public record TestCase(TestCaseId id, SubmissionId submissionId, String input, String expectedOutput) {
    public TestCase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(submissionId, "submissionId");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(expectedOutput, "expectedOutput");
    }
}