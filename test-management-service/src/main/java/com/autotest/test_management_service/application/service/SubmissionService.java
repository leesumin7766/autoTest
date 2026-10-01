package com.autotest.test_management_service.application.service;

import com.autotest.test_management_service.application.storage.FileStoragePort;
import com.autotest.test_management_service.domain.submission.FileFormat;
import com.autotest.test_management_service.domain.submission.FileMetadata;
import com.autotest.test_management_service.domain.submission.FileParser;
import com.autotest.test_management_service.domain.submission.ParsedContent;
import com.autotest.test_management_service.domain.submission.ProductId;
import com.autotest.test_management_service.domain.submission.StoredPath;
import com.autotest.test_management_service.domain.submission.Submission;
import com.autotest.test_management_service.domain.submission.SubmissionFile;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.submission.SubmissionStatus;
import com.autotest.test_management_service.domain.submission.SubmittedDocument;
import com.autotest.test_management_service.domain.vo.MemberId;
import com.autotest.test_management_service.domain.vo.SubmissionType;
import com.autotest.test_management_service.infrastructure.parser.DocumentFileParserFactory;
import com.autotest.test_management_service.infrastructure.parser.DocumentParsingException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class SubmissionService {
    private static final String EMPTY_EXCEL_REASON = "추출할 셀 내용이 없습니다";
    private static final String OCR_REQUIRED_REASON = "텍스트를 추출할 수 없음/OCR 필요";
    private static final String GENERIC_FAILURE_REASON = "문서에서 텍스트를 추출할 수 없습니다";
    private static final long MAX_FILE_SIZE_BYTES = 100L * 1024 * 1024;

    private final SubmissionPersistenceService submissionPersistenceService;
    private final FileStoragePort fileStoragePort;
    private final DocumentFileParserFactory documentFileParserFactory;
    private final FileTypeResolver fileTypeResolver;

    public Submission submit(MultipartFile file, MemberId memberId, ProductId productId) {
        return submit(file, com.autotest.test_management_service.domain.submission.SubmissionType.AGREEMENT, memberId, productId);
    }

    public Submission submit(
            MultipartFile file,
            com.autotest.test_management_service.domain.submission.SubmissionType role,
            MemberId memberId,
            ProductId productId
    ) {
        PreparedFile prepared = prepare(file);
        StoredPath storedPath = store(file, prepared);
        Submission uploadedSubmission = Submission.create(memberId, productId, prepared.fileType(), storedPath, "", List.of());
        try {
            submissionPersistenceService.saveUploaded(uploadedSubmission);
        } catch (RuntimeException persistenceFailure) {
            cleanupStoredFile(storedPath, persistenceFailure);
            throw persistenceFailure;
        }

        SubmittedDocument document;
        Submission result;
        try (InputStream parserInput = fileStoragePort.load(storedPath)) {
            ParsedContent parsedContent = prepared.parser().parse(prepared.metadata(), parserInput);
            String extractedText = parsedContent.extractedText();
            result = submissionPersistenceService.markAsParsed(uploadedSubmission.submissionId(), extractedText);
            document = document(uploadedSubmission.submissionId(), role, prepared, storedPath,
                    extractedText, SubmissionStatus.PARSED, null);
        } catch (Exception extractionFailure) {
            String reason = safeFailureReason(extractionFailure);
            result = submissionPersistenceService.markAsFailed(uploadedSubmission.submissionId(), reason);
            document = document(uploadedSubmission.submissionId(), role, prepared, storedPath,
                    "", SubmissionStatus.FAILED, reason);
        }
        submissionPersistenceService.saveDocument(document);
        return result;
    }

    public SubmittedDocument uploadAdditional(
            SubmissionId submissionId,
            MultipartFile file,
            com.autotest.test_management_service.domain.submission.SubmissionType role,
            MemberId memberId
    ) {
        Submission submission = submissionPersistenceService.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found"));
        if (!submission.memberId().equals(memberId)) {
            throw new IllegalArgumentException("Submission does not belong to the requested member");
        }
        if (submissionPersistenceService.hasDocumentForRole(submissionId, role)) {
            throw new IllegalArgumentException("A file already exists for submission role: " + role);
        }

        PreparedFile prepared = prepare(file);
        StoredPath storedPath = store(file, prepared);
        try {
            String extractedText;
            SubmissionStatus status;
            String failureReason;
            try (InputStream parserInput = fileStoragePort.load(storedPath)) {
                extractedText = prepared.parser().parse(prepared.metadata(), parserInput).extractedText();
                status = SubmissionStatus.PARSED;
                failureReason = null;
            } catch (Exception extractionFailure) {
                extractedText = "";
                status = SubmissionStatus.FAILED;
                failureReason = safeFailureReason(extractionFailure);
            }
            return submissionPersistenceService.saveDocument(document(
                    submissionId, role, prepared, storedPath, extractedText, status, failureReason));
        } catch (RuntimeException persistenceFailure) {
            cleanupStoredFile(storedPath, persistenceFailure);
            throw persistenceFailure;
        }
    }

    public List<SubmittedDocument> findDocumentsBySubmissionId(SubmissionId id) {
        return submissionPersistenceService.findDocumentsBySubmissionId(id);
    }

    private PreparedFile prepare(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Submission file must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File size must not exceed 100 MiB");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Submission file must have a filename");
        }

        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        SubmissionType type = fileTypeResolver.resolve(originalFilename, contentType);

        int extensionSeparator = originalFilename.lastIndexOf('.');
        String extension = originalFilename.substring(extensionSeparator + 1);
        FileFormat format = FileFormat.fromExtension(extension);
        FileParser parser = documentFileParserFactory.getParser(format);
        FileMetadata metadata = new FileMetadata(originalFilename, file.getSize(), "dummy-checksum", contentType);
        return new PreparedFile(originalFilename, contentType, type, format, parser, metadata);
    }

    private StoredPath store(MultipartFile file, PreparedFile prepared) {
        try (InputStream storageInput = file.getInputStream()) {
            return fileStoragePort.store(storageInput, prepared.originalFilename(), prepared.contentType());
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to open submission file for storage", exception);
        }
    }

    private SubmittedDocument document(
            SubmissionId submissionId,
            com.autotest.test_management_service.domain.submission.SubmissionType role,
            PreparedFile prepared,
            StoredPath storedPath,
            String extractedText,
            SubmissionStatus status,
            String failureReason
    ) {
        SubmissionFile submissionFile = SubmissionFile.create(role, prepared.metadata(), prepared.format())
            .assignStoredPath(storedPath);
        return new SubmittedDocument(
            submissionFile.id().value(), submissionId, submissionFile.submissionType(), prepared.fileType(), submissionFile.fileFormat(),
                prepared.originalFilename(), prepared.contentType(), storedPath,
                extractedText, status, failureReason, Instant.now());
    }

    private void cleanupStoredFile(StoredPath storedPath, RuntimeException persistenceFailure) {
        try {
            fileStoragePort.delete(storedPath);
        } catch (RuntimeException cleanupFailure) {
            persistenceFailure.addSuppressed(cleanupFailure);
        }
    }

    public java.util.Optional<Submission> findById(SubmissionId id) {
        return submissionPersistenceService.findById(id);
    }

    private String safeFailureReason(Exception exception) {
        if (exception instanceof DocumentParsingException) {
            if (EMPTY_EXCEL_REASON.equals(exception.getMessage())) {
                return EMPTY_EXCEL_REASON;
            }
            if (OCR_REQUIRED_REASON.equals(exception.getMessage())) {
                return OCR_REQUIRED_REASON;
            }
        }
        return GENERIC_FAILURE_REASON;
    }

    private record PreparedFile(
            String originalFilename,
            String contentType,
            SubmissionType fileType,
            FileFormat format,
            FileParser parser,
            FileMetadata metadata
    ) {
    }
}