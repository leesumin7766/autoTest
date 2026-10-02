package com.autotest.test_management_service.infrastructure.persistence;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.submission.SubmissionType;
import com.autotest.test_management_service.domain.submission.SubmittedDocument;
import com.autotest.test_management_service.domain.submission.SubmittedDocumentRepository;

@Repository
public class SubmittedDocumentRepositoryAdapter implements SubmittedDocumentRepository {
    private final SpringDataSubmittedDocumentRepository repository;

    public SubmittedDocumentRepositoryAdapter(SpringDataSubmittedDocumentRepository repository) {
        this.repository = repository;
    }

    @Override
    public SubmittedDocument save(SubmittedDocument document) {
        return repository.save(SubmittedDocumentEntity.fromDomain(document)).toDomain();
    }

    @Override
    public List<SubmittedDocument> findBySubmissionId(SubmissionId submissionId) {
        return repository.findAllBySubmissionIdOrderByUploadedAtAsc(submissionId.value()).stream()
                .map(SubmittedDocumentEntity::toDomain)
                .toList();
    }

    @Override
    public boolean existsBySubmissionIdAndRole(SubmissionId submissionId, SubmissionType role) {
        return repository.existsBySubmissionIdAndRole(submissionId.value(), role);
    }

    @Override
    public boolean replaceFailedDocument(java.util.UUID previousFileId, SubmittedDocument replacement) {
        int removed = repository.deleteFailedByIdAndRole(
                previousFileId,
                replacement.submissionId().value(),
                replacement.role(),
                com.autotest.test_management_service.domain.submission.SubmissionStatus.FAILED);
        if (removed != 1) {
            return false;
        }
        repository.flush();
        repository.save(SubmittedDocumentEntity.fromDomain(replacement));
        return true;
    }
}