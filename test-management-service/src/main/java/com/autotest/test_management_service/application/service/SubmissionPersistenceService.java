package com.autotest.test_management_service.application.service;

import com.autotest.test_management_service.domain.submission.Submission;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.submission.SubmissionRepository;
import com.autotest.test_management_service.domain.submission.SubmissionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class SubmissionPersistenceService {
    private final SubmissionRepository submissionRepository;

    public SubmissionPersistenceService(SubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    @Transactional
    public Submission saveUploaded(Submission submission) {
        return submissionRepository.save(submission);
    }

    @Transactional
    public Submission markAsParsed(SubmissionId id, String extractedText) {
        Submission uploaded = requireUploaded(id);
        return submissionRepository.save(uploaded.markAsParsed(extractedText));
    }

    @Transactional
    public Submission markAsFailed(SubmissionId id, String failureReason) {
        Submission uploaded = requireUploaded(id);
        return submissionRepository.save(uploaded.markAsFailed(failureReason));
    }

    @Transactional(readOnly = true)
    public Optional<Submission> findById(SubmissionId id) {
        return submissionRepository.findById(id);
    }

    private Submission requireUploaded(SubmissionId id) {
        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Uploaded submission does not exist: " + id.value()));
        if (submission.status() != SubmissionStatus.UPLOADED) {
            throw new IllegalStateException("Submission is not in UPLOADED status: " + id.value());
        }
        return submission;
    }
}