package com.autotest.test_management_service.infrastructure.persistence;

import com.autotest.test_management_service.domain.submission.SubmissionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataSubmittedDocumentRepository extends JpaRepository<SubmittedDocumentEntity, UUID> {
    List<SubmittedDocumentEntity> findAllBySubmissionIdOrderByUploadedAtAsc(UUID submissionId);

    boolean existsBySubmissionIdAndRole(UUID submissionId, SubmissionType role);
}