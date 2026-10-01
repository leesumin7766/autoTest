package com.autotest.test_management_service.domain.submission;

import java.util.Objects;

public record TestCase(TestCaseId id, SubmissionId submissionId, String input, String expectedOutput) {
    public TestCase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(expectedOutput, "expectedOutput");
    }

    /**
     * Creates a TestCase without a submissionId — used by parsers.
     * The submissionId is set later by the service layer via {@link #withSubmissionId}.
     */
    public static TestCase createParsed(String input, String expectedOutput) {
        return new TestCase(TestCaseId.generate(), null, input, expectedOutput);
    }

    /**
     * Returns a copy of this TestCase with the given submissionId.
     */
    public TestCase withSubmissionId(SubmissionId submissionId) {
        Objects.requireNonNull(submissionId, "submissionId");
        return new TestCase(this.id, submissionId, this.input, this.expectedOutput);
    }
}