package com.autotest.test_management_service.domain.submission;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.autotest.test_management_service.domain.vo.MemberId;
import com.autotest.test_management_service.domain.event.SubmissionUploadedEvent;

public final class Submission {
    private static final long MAX_FILE_SIZE_BYTES = 100L * 1024 * 1024;

    private final SubmissionId submissionId;
    private final MemberId memberId;
    private final ProductId productId;
    private final com.autotest.test_management_service.domain.vo.SubmissionType submissionType;
    private final StoredPath storedPath;
    private final String extractedText;
    private final List<TestCase> testCases;
    private final List<SubmissionFile> files;
    private final SubmissionStatus status;
    private final Instant uploadedAt;
    private final List<Object> domainEvents;

    private Submission(
            SubmissionId submissionId,
            MemberId memberId,
            ProductId productId,
            com.autotest.test_management_service.domain.vo.SubmissionType submissionType,
            StoredPath storedPath,
            String extractedText,
            List<TestCase> testCases,
            List<SubmissionFile> files,
            SubmissionStatus status,
            Instant uploadedAt,
            List<Object> domainEvents
    ) {
        this.submissionId = Objects.requireNonNull(submissionId, "submissionId");
        this.memberId = Objects.requireNonNull(memberId, "memberId");
        this.productId = Objects.requireNonNull(productId, "productId");
        this.submissionType = Objects.requireNonNull(submissionType, "submissionType");
        this.storedPath = storedPath;
        this.extractedText = Objects.requireNonNull(extractedText, "extractedText");
        this.testCases = List.copyOf(testCases);
        this.files = List.copyOf(files);
        this.status = Objects.requireNonNull(status, "status");
        this.uploadedAt = uploadedAt;
        this.domainEvents = List.copyOf(domainEvents);
    }

        public static Submission create(
            MemberId memberId,
            ProductId productId,
            com.autotest.test_management_service.domain.vo.SubmissionType submissionType,
            StoredPath storedPath,
            String extractedText,
            List<TestCase> testCases
        ) {
        return new Submission(
                SubmissionId.generate(),
            memberId,
                productId,
            submissionType,
            storedPath,
            extractedText,
            testCases,
            List.of(),
            SubmissionStatus.UPLOADED,
            Instant.now(),
            List.of()
        );
        }

        public static Submission reconstitute(
            SubmissionId submissionId,
            MemberId memberId,
            ProductId productId,
            com.autotest.test_management_service.domain.vo.SubmissionType submissionType,
            StoredPath storedPath,
            String extractedText,
            SubmissionStatus status,
            Instant uploadedAt
        ) {
        return new Submission(
            submissionId,
            memberId,
            productId,
            submissionType,
            storedPath,
            extractedText,
            List.of(),
                List.of(),
            status,
            uploadedAt,
                List.of()
        );
    }

    static Submission draft(MemberId memberId, ProductId productId) {
        return new Submission(
                SubmissionId.generate(),
                memberId,
                productId,
                com.autotest.test_management_service.domain.vo.SubmissionType.UNKNOWN,
                null,
                "",
                List.of(),
                List.of(),
                SubmissionStatus.DRAFT,
                null,
                List.of()
        );
    }

    public Submission addFile(SubmissionType type, FileMetadata metadata, FileFormat format) {
        requireDraft();
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(metadata, "metadata");
        if (metadata.size() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File size must not exceed 100 MiB");
        }
        if (files.stream().anyMatch(file -> file.submissionType() == type)) {
            throw new IllegalArgumentException("A file already exists for submission type: " + type);
        }

        SubmissionFile file = SubmissionFile.create(type, metadata, format);
        List<SubmissionFile> updatedFiles = new ArrayList<>(files);
        updatedFiles.add(file);
        return copy(updatedFiles, status, uploadedAt, domainEvents);
    }

    public Submission assignStoredPath(SubmissionFileId fileId, StoredPath path) {
        Objects.requireNonNull(fileId, "fileId");
        Objects.requireNonNull(path, "path");

        List<SubmissionFile> updatedFiles = new ArrayList<>(files);
        int fileIndex = findFileIndex(fileId);
        updatedFiles.set(fileIndex, files.get(fileIndex).assignStoredPath(path));
        return copy(updatedFiles, status, uploadedAt, domainEvents);
    }

    public Submission removeFile(SubmissionFileId fileId) {
        requireDraft();
        Objects.requireNonNull(fileId, "fileId");

        List<SubmissionFile> updatedFiles = new ArrayList<>(files);
        updatedFiles.remove(findFileIndex(fileId));
        return copy(updatedFiles, status, uploadedAt, domainEvents);
    }

    public Submission removeFileByType(SubmissionType type) {
        requireDraft();
        Objects.requireNonNull(type, "type");

        List<SubmissionFile> updatedFiles = new ArrayList<>(files);
        boolean removed = updatedFiles.removeIf(file -> file.submissionType() == type);
        if (!removed) {
            throw new IllegalArgumentException("No file exists for submission type: " + type);
        }
        return copy(updatedFiles, status, uploadedAt, domainEvents);
    }

    public Submission markAsUploaded() {
        requireStatus(SubmissionStatus.DRAFT);
        if (files.isEmpty()) {
            throw new IllegalStateException("Cannot upload a submission without files");
        }
        if (files.stream().anyMatch(file -> file.storedPath().isEmpty())) {
            throw new IllegalStateException("All files must be stored before uploading the submission");
        }

        Instant uploadTime = Instant.now();
        SubmissionUploadedEvent event = new SubmissionUploadedEvent(submissionId, productId, uploadTime);
        return registerEvent(event, SubmissionStatus.UPLOADED, uploadTime);
    }

    public Submission markAsParsed() {
        requireStatus(SubmissionStatus.UPLOADED);
        return copy(files, SubmissionStatus.PARSED, uploadedAt, domainEvents);
    }

    public List<SubmissionFile> getFiles() {
        return List.copyOf(files);
    }

    public SubmissionId submissionId() {
        return submissionId;
    }

    public ProductId productId() {
        return productId;
    }

    public MemberId memberId() {
        return memberId;
    }

    public com.autotest.test_management_service.domain.vo.SubmissionType submissionType() {
        return submissionType;
    }

    public StoredPath storedPath() {
        return storedPath;
    }

    public String extractedText() {
        return extractedText;
    }

    public List<TestCase> testCases() {
        return List.copyOf(testCases);
    }

    public SubmissionStatus status() {
        return status;
    }

    public Optional<Instant> uploadedAt() {
        return Optional.ofNullable(uploadedAt);
    }

    public List<Object> domainEvents() {
        return List.copyOf(domainEvents);
    }

    private Submission registerEvent(Object event, SubmissionStatus nextStatus, Instant uploadTime) {
        List<Object> updatedEvents = new ArrayList<>(domainEvents);
        updatedEvents.add(Objects.requireNonNull(event, "event"));
        return copy(files, nextStatus, uploadTime, updatedEvents);
    }

    private Submission copy(
            List<SubmissionFile> updatedFiles,
            SubmissionStatus updatedStatus,
            Instant updatedUploadedAt,
            List<Object> updatedEvents
    ) {
        return new Submission(
            submissionId,
            memberId,
            productId,
            submissionType,
            storedPath,
            extractedText,
            testCases,
            updatedFiles,
            updatedStatus,
            updatedUploadedAt,
            updatedEvents
        );
    }

    private int findFileIndex(SubmissionFileId fileId) {
        for (int index = 0; index < files.size(); index++) {
            if (files.get(index).id().equals(fileId)) {
                return index;
            }
        }
        throw new IllegalArgumentException("No file exists with id: " + fileId.value());
    }

    private void requireDraft() {
        requireStatus(SubmissionStatus.DRAFT);
    }

    private void requireStatus(SubmissionStatus requiredStatus) {
        if (status != requiredStatus) {
            throw new IllegalStateException("Expected status " + requiredStatus + " but was " + status);
        }
    }
}