package com.autotest.test_management_service.domain.submission;

import java.util.List;

public interface SubmittedDocumentRepository {
    SubmittedDocument save(SubmittedDocument document);

    List<SubmittedDocument> findBySubmissionId(SubmissionId submissionId);

    boolean existsBySubmissionIdAndRole(SubmissionId submissionId, SubmissionType role);
}