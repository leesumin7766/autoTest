package com.autotest.test_management_service.application.service;

import com.autotest.test_management_service.application.port.FileParser;
import com.autotest.test_management_service.application.storage.FileStoragePort;
import com.autotest.test_management_service.domain.port.TestCaseRepository;
import com.autotest.test_management_service.domain.service.SubmissionDomainService;
import com.autotest.test_management_service.domain.submission.ProductId;
import com.autotest.test_management_service.domain.submission.StoredPath;
import com.autotest.test_management_service.domain.submission.Submission;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.submission.SubmissionRepository;
import com.autotest.test_management_service.domain.submission.TestCase;
import com.autotest.test_management_service.domain.vo.MemberId;
import com.autotest.test_management_service.domain.vo.SubmissionType;
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
    private final FileParserFactory fileParserFactory;
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

        SubmissionType type = fileTypeResolver.resolve(originalFilename);
        FileParser parser = fileParserFactory.getParser(type);
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();

        StoredPath storedPath;
        try (InputStream storageInput = file.getInputStream()) {
            storedPath = fileStoragePort.store(storageInput, originalFilename, contentType);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to open submission file for storage", exception);
        }

        String extractedText;
        List<TestCase> parsedTestCases;
        try (InputStream parserInput = file.getInputStream()) {
            extractedText = parser.extractText(parserInput);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to open submission file for text extraction", exception);
        }
        try (InputStream parserInput = file.getInputStream()) {
            parsedTestCases = parser.parse(parserInput, originalFilename);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to open submission file for parsing", exception);
        }

        Submission submission = submissionDomainService.create(
                memberId,
                productId,
                type,
                storedPath,
                extractedText,
                parsedTestCases
        );
        Submission savedSubmission = submissionRepository.save(submission);
        SubmissionId submissionId = savedSubmission.submissionId();

        // Fill in submissionId on parsed test cases and persist them
        List<TestCase> testCasesWithId = parsedTestCases.stream()
                .map(tc -> tc.withSubmissionId(submissionId))
                .toList();
        if (!testCasesWithId.isEmpty()) {
            testCaseRepository.saveAll(testCasesWithId);
        }

        return submissionId;
    }
}