package com.autotest.test_management_service.domain.submission;

import java.util.Objects;
import java.util.Optional;

public final class SubmissionFile {
    private final SubmissionFileId id;
    private final FileMetadata fileMetadata;
    private final FileFormat fileFormat;
    private final SubmissionType submissionType;
    private final StoredPath storedPath;

    private SubmissionFile(
            SubmissionFileId id,
            FileMetadata fileMetadata,
            FileFormat fileFormat,
            SubmissionType submissionType,
            StoredPath storedPath
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.fileMetadata = Objects.requireNonNull(fileMetadata, "fileMetadata");
        this.fileFormat = Objects.requireNonNull(fileFormat, "fileFormat");
        this.submissionType = Objects.requireNonNull(submissionType, "submissionType");
        this.storedPath = storedPath;
    }

    public static SubmissionFile create(
            SubmissionType type,
            FileMetadata metadata,
            FileFormat format
    ) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(format, "format");

        FileFormat detectedFormat = detectFormat(metadata.originalName());
        if (detectedFormat != format) {
            throw new IllegalArgumentException("File format does not match the filename extension");
        }

        return new SubmissionFile(SubmissionFileId.generate(), metadata, format, type, null);
    }

    public static SubmissionFile create(SubmissionType type, FileMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        return create(type, metadata, detectFormat(metadata.originalName()));
    }

    public SubmissionFile assignStoredPath(StoredPath path) {
        Objects.requireNonNull(path, "path");
        if (storedPath != null) {
            throw new IllegalStateException("Stored path has already been assigned");
        }
        return new SubmissionFile(id, fileMetadata, fileFormat, submissionType, path);
    }

    public SubmissionFileId id() {
        return id;
    }

    public FileMetadata fileMetadata() {
        return fileMetadata;
    }

    public FileFormat fileFormat() {
        return fileFormat;
    }

    public SubmissionType submissionType() {
        return submissionType;
    }

    public Optional<StoredPath> storedPath() {
        return Optional.ofNullable(storedPath);
    }

    private static FileFormat detectFormat(String originalName) {
        int extensionSeparator = originalName.lastIndexOf('.');
        if (extensionSeparator < 0 || extensionSeparator == originalName.length() - 1) {
            throw new IllegalArgumentException("Filename must have a supported extension: " + originalName);
        }
        return FileFormat.fromExtension(originalName.substring(extensionSeparator + 1));
    }
}