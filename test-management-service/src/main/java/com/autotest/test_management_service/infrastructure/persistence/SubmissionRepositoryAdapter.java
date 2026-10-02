package com.autotest.test_management_service.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.autotest.test_management_service.domain.submission.Submission;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.submission.SubmissionRepository;

@Repository
public class SubmissionRepositoryAdapter implements SubmissionRepository {
    private final SpringDataSubmissionRepository repository;

    public SubmissionRepositoryAdapter(SpringDataSubmissionRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Submission> findById(SubmissionId id) {
        return repository.findById(id.value()).map(SubmissionEntity::toDomain);
    }

    @Override
    public Optional<Submission> findByIdForUpdate(SubmissionId id) {
        return repository.findByIdForUpdate(id.value()).map(SubmissionEntity::toDomain);
    }

    @Override
    public Submission save(Submission submission) {
        return repository.save(SubmissionEntity.fromDomain(submission)).toDomain();
    }
}