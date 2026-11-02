package com.autotest.test_management_service.application.service;

import com.autotest.test_management_service.domain.submission.*;
import com.autotest.test_management_service.domain.vo.MemberId;
import com.autotest.test_management_service.application.storage.FileStoragePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManualReviewServiceTest {
    @Test void anotherMembersSubmissionCannotReadReviewHistory() {
        var id = UUID.randomUUID();
        var repository = mock(SubmissionRepository.class);
        var submission = mock(Submission.class);
        when(repository.findById(SubmissionId.of(id))).thenReturn(Optional.of(submission));
        when(submission.memberId()).thenReturn(new MemberId(2L));
        var jdbc = mock(JdbcTemplate.class);
        var service = new ManualReviewService(jdbc, new ObjectMapper(), repository,
            mock(SubmissionPersistenceService.class), mock(FileStoragePort.class),
            mock(PlatformTransactionManager.class), "http://localhost:8007", "test-token");
        assertThrows(com.autotest.common_lib.exception.CommonException.class, () -> service.get(id, 1, null));
        verifyNoInteractions(jdbc);
        service.close();
    }

    @Test void onlyParsedPdfManualsCanStartAndBytesAreNotSilentlyTruncated() {
        assertThrows(RuntimeException.class, () -> ManualReviewService.validateManual(null));
        for (var status : new SubmissionStatus[]{SubmissionStatus.FAILED, SubmissionStatus.UPLOADED}) {
            var doc = mock(SubmittedDocument.class);
            when(doc.status()).thenReturn(status);
            assertThrows(RuntimeException.class, () -> ManualReviewService.validateManual(doc));
        }
        var doc = mock(SubmittedDocument.class);
        when(doc.status()).thenReturn(SubmissionStatus.PARSED);
        when(doc.format()).thenReturn(FileFormat.DOCX);
        assertThrows(RuntimeException.class, () -> ManualReviewService.validateManual(doc));
        when(doc.format()).thenReturn(FileFormat.PDF);
        assertDoesNotThrow(() -> ManualReviewService.validateManual(doc));
    }
}
