package com.autotest.test_management_service.application.service;

import com.autotest.test_management_service.application.storage.FileStoragePort;
import com.autotest.test_management_service.domain.service.SubmissionDomainService;
import com.autotest.test_management_service.domain.submission.ProductId;
import com.autotest.test_management_service.domain.submission.StoredPath;
import com.autotest.test_management_service.domain.submission.Submission;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.submission.SubmissionRepository;
import com.autotest.test_management_service.domain.vo.MemberId;
import com.autotest.test_management_service.domain.vo.SubmissionType;
import com.autotest.test_management_service.infrastructure.parser.SimpleTextFileParser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubmissionServiceTest {
    private static final MemberId MEMBER_ID = new MemberId(17L);
    private static final ProductId PRODUCT_ID = new ProductId(23L);

    @Test
    void storesS3PathParsesAndPersistsSubmission() throws Exception {
        StoredPath s3Path = new StoredPath("s3://autotest-submissions/uuid-main.py");
        SubmissionId submissionId = submitWithStoredPath(s3Path);

        assertTrue(submissionId.value() != null);
    }

    @Test
    void persistsSubmissionWhenStorageReturnsLocalFallbackPath() throws Exception {
        StoredPath localPath = new StoredPath("local:/tmp/autotest-submissions/uuid-main.py");
        SubmissionId submissionId = submitWithStoredPath(localPath);

        assertTrue(submissionId.value() != null);
    }

    private SubmissionId submitWithStoredPath(StoredPath storedPath) throws Exception {
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        FileStoragePort fileStoragePort = mock(FileStoragePort.class);
        byte[] source = "print('hello')".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "main.py", "text/x-python", source);

        when(fileStoragePort.store(any(InputStream.class), eq("main.py"), eq("text/x-python")))
                .thenReturn(storedPath);
        when(submissionRepository.save(any(Submission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SubmissionService service = new SubmissionService(
                submissionRepository,
                fileStoragePort,
                new FileParserFactory(java.util.List.of(new SimpleTextFileParser())),
                new SubmissionDomainService(),
                new FileTypeResolver()
        );

        SubmissionId submissionId = service.submit(file, MEMBER_ID, PRODUCT_ID);

        var submissionCaptor = org.mockito.ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(submissionCaptor.capture());
        verify(fileStoragePort).store(any(InputStream.class), eq("main.py"), eq("text/x-python"));

        Submission persistedSubmission = submissionCaptor.getValue();
        assertEquals(MEMBER_ID, persistedSubmission.memberId());
        assertEquals(PRODUCT_ID, persistedSubmission.productId());
        assertEquals(SubmissionType.PYTHON, persistedSubmission.submissionType());
        assertEquals(storedPath, persistedSubmission.storedPath());
        assertEquals("print('hello')", persistedSubmission.extractedText());
        assertTrue(persistedSubmission.testCases().isEmpty());
        assertEquals(persistedSubmission.submissionId(), submissionId);
        return submissionId;
    }
}