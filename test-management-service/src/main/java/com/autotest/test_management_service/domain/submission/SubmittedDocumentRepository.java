package com.autotest.test_management_service.domain.submission;

import java.util.List;
import java.util.UUID;

public interface SubmittedDocumentRepository {
    SubmittedDocument save(SubmittedDocument document);

    List<SubmittedDocument> findBySubmissionId(SubmissionId submissionId);

    boolean existsBySubmissionIdAndRole(SubmissionId submissionId, SubmissionType role);

    boolean replaceFailedDocument(UUID previousFileId, SubmittedDocument replacement);
}