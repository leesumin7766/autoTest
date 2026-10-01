package com.autotest.test_management_service.application.service;

import com.autotest.test_management_service.application.storage.FileStoragePort;
import com.autotest.test_management_service.domain.submission.FileFormat;
import com.autotest.test_management_service.domain.submission.FileMetadata;
import com.autotest.test_management_service.domain.submission.FileParser;
import com.autotest.test_management_service.domain.submission.ParsedContent;
import com.autotest.test_management_service.domain.submission.ProductId;
import com.autotest.test_management_service.domain.submission.StoredPath;
import com.autotest.test_management_service.domain.submission.Submission;
import com.autotest.test_management_service.domain.submission.SubmissionId;
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
import java.util.List;

@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class SubmissionService {
    private static final String EMPTY_EXCEL_REASON = "추출할 셀 내용이 없습니다";
    private static final String OCR_REQUIRED_REASON = "텍스트를 추출할 수 없음/OCR 필요";
    private static final String GENERIC_FAILURE_REASON = "문서에서 텍스트를 추출할 수 없습니다";

    private final SubmissionPersistenceService submissionPersistenceService;
    private final FileStoragePort fileStoragePort;
    private final DocumentFileParserFactory documentFileParserFactory;
    private final FileTypeResolver fileTypeResolver;

    public Submission submit(MultipartFile file, MemberId memberId, ProductId productId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Submission file must not be empty");
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

        StoredPath storedPath;
        try (InputStream storageInput = file.getInputStream()) {
            storedPath = fileStoragePort.store(storageInput, originalFilename, contentType);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to open submission file for storage", exception);
        }

        Submission uploadedSubmission = Submission.create(memberId, productId, type, storedPath, "", List.of());
        try {
            submissionPersistenceService.saveUploaded(uploadedSubmission);
        } catch (RuntimeException persistenceFailure) {
            try {
                fileStoragePort.delete(storedPath);
            } catch (RuntimeException cleanupFailure) {
                persistenceFailure.addSuppressed(cleanupFailure);
            }
            throw persistenceFailure;
        }

        String extractedText;
        try (InputStream parserInput = fileStoragePort.load(storedPath)) {
            FileMetadata metadata = new FileMetadata(originalFilename, file.getSize(), "dummy-checksum", contentType);
            ParsedContent parsedContent = parser.parse(metadata, parserInput);
            extractedText = parsedContent.extractedText();
        } catch (Exception extractionFailure) {
            return submissionPersistenceService.markAsFailed(
                    uploadedSubmission.submissionId(),
                    safeFailureReason(extractionFailure)
            );
        }

        return submissionPersistenceService.markAsParsed(uploadedSubmission.submissionId(), extractedText);
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
}