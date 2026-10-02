package com.autotest.test_management_service.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.autotest.test_management_service.domain.submission.SubmissionStatus;
import com.autotest.test_management_service.domain.submission.SubmissionType;

interface SpringDataSubmittedDocumentRepository extends JpaRepository<SubmittedDocumentEntity, UUID> {
    List<SubmittedDocumentEntity> findAllBySubmissionIdOrderByUploadedAtAsc(UUID submissionId);

    boolean existsBySubmissionIdAndRole(UUID submissionId, SubmissionType role);

        @Modifying(flushAutomatically = true)
        @Query("delete from SubmittedDocumentEntity document where document.id = :fileId " +
            "and document.submissionId = :submissionId and document.role = :role and document.status = :status")
        int deleteFailedByIdAndRole(
            @Param("fileId") UUID fileId,
            @Param("submissionId") UUID submissionId,
            @Param("role") com.autotest.test_management_service.domain.submission.SubmissionType role,
            @Param("status") SubmissionStatus status
        );
}