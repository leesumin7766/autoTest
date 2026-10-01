package com.autotest.test_management_service.application.service;

import com.autotest.test_management_service.application.storage.FileStoragePort;
import com.autotest.test_management_service.domain.port.TestCaseRepository;
import com.autotest.test_management_service.domain.service.SubmissionDomainService;
import com.autotest.test_management_service.domain.submission.FileFormat;
import com.autotest.test_management_service.domain.submission.FileMetadata;
import com.autotest.test_management_service.domain.submission.FileParser;
import com.autotest.test_management_service.domain.submission.ParsedContent;
import com.autotest.test_management_service.domain.submission.ProductId;
import com.autotest.test_management_service.domain.submission.StoredPath;
import com.autotest.test_management_service.domain.submission.Submission;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.submission.SubmissionRepository;
import com.autotest.test_management_service.domain.submission.TestCase;
import com.autotest.test_management_service.domain.vo.MemberId;
import com.autotest.test_management_service.domain.vo.SubmissionType;
import com.autotest.test_management_service.infrastructure.parser.DocumentFileParserFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class SubmissionService {
    private final SubmissionRepository submissionRepository;
    private final FileStoragePort fileStoragePort;
    private final DocumentFileParserFactory documentFileParserFactory;
    private final SubmissionDomainService submissionDomainService;
    private final FileTypeResolver fileTypeResolver;
    private final TestCaseRepository testCaseRepository;

    @Transactional
    public SubmissionId submit(MultipartFile file, MemberId memberId, ProductId productId) {
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

        String extractedText;
        try (InputStream parserInput = file.getInputStream()) {
            FileMetadata metadata = new FileMetadata(originalFilename, file.getSize(), "dummy-checksum", contentType);
            ParsedContent parsedContent = parser.parse(metadata, parserInput);
            extractedText = parsedContent.extractedText();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to open submission file for parsing", exception);
        }

        List<TestCase> parsedTestCases = List.of();

        Submission submission = submissionDomainService.create(
                memberId,
                productId,
                type,
                storedPath,
                extractedText,
                parsedTestCases
        );
        Submission savedSubmission = submissionRepository.save(submission);

        return savedSubmission.submissionId();
    }
}