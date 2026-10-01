package com.autotest.test_management_service.infrastructure.persistence;

import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.submission.SubmittedDocument;
import com.autotest.test_management_service.domain.submission.SubmittedDocumentRepository;
import com.autotest.test_management_service.domain.submission.SubmissionType;
import org.springframework.stereotype.Repository;

import java.util.List;

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
}