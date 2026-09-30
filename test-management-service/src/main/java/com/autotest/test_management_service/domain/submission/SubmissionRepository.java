package com.autotest.test_management_service.domain.submission;

import java.util.Optional;

public interface SubmissionRepository {
    Optional<Submission> findById(SubmissionId id);

    Submission save(Submission submission);
}