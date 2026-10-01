package com.autotest.test_management_service.presentation.controller;

import com.autotest.test_management_service.application.service.SubmissionService;
import com.autotest.test_management_service.domain.submission.ProductId;
import com.autotest.test_management_service.domain.submission.StoredPath;
import com.autotest.test_management_service.domain.submission.Submission;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.submission.SubmissionStatus;
import com.autotest.test_management_service.domain.vo.MemberId;
import com.autotest.test_management_service.domain.vo.SubmissionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubmissionController.class)
class SubmissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubmissionService submissionService;

    @Test
    void submitReturns200AndSubmissionId() throws Exception {
        SubmissionId mockSubmissionId = SubmissionId.generate();
        Submission parsedSubmission = submission(mockSubmissionId, SubmissionStatus.PARSED, null, "extracted text");
        when(submissionService.submit(any(), eq(new MemberId(1L)), eq(new ProductId(100L))))
                .thenReturn(parsedSubmission);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                "dummy pdf content".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/submissions")
                        .file(file)
                        .param("productId", "100")
                        .header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionId").value(mockSubmissionId.value().toString()))
                .andExpect(jsonPath("$.status").value("PARSED"))
                .andExpect(jsonPath("$.failureReason").doesNotExist());

        verify(submissionService).submit(any(), eq(new MemberId(1L)), eq(new ProductId(100L)));
    }

    @Test
    void submitReturnsFailedStatusAndReasonWhenExtractionFails() throws Exception {
        SubmissionId id = SubmissionId.generate();
        when(submissionService.submit(any(), eq(new MemberId(1L)), eq(new ProductId(100L))))
                .thenReturn(submission(id, SubmissionStatus.FAILED, "추출할 셀 내용이 없습니다", ""));
        MockMultipartFile file = new MockMultipartFile("file", "empty.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] {1});

        mockMvc.perform(multipart("/api/submissions")
                        .file(file)
                        .param("productId", "100")
                        .header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionId").value(id.value().toString()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("추출할 셀 내용이 없습니다"));
    }

    @Test
    void getReturnsSubmissionStatusAndFailureReason() throws Exception {
        SubmissionId id = SubmissionId.generate();
        when(submissionService.findById(id)).thenReturn(Optional.of(
                submission(id, SubmissionStatus.FAILED, "문서에서 텍스트를 추출할 수 없습니다", "")
        ));

        mockMvc.perform(get("/api/submissions/{id}", id.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionId").value(id.value().toString()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("문서에서 텍스트를 추출할 수 없습니다"));
    }

    @Test
    void getReturns404ForUnknownSubmission() throws Exception {
        UUID id = UUID.randomUUID();
        when(submissionService.findById(SubmissionId.of(id))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/submissions/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void unsupportedUploadFormatStillReturns400() throws Exception {
        doThrow(new IllegalArgumentException("허용되지 않은 파일 형식입니다. PDF, Excel, HWP, Word만 업로드 가능합니다."))
                .when(submissionService).submit(any(), eq(new MemberId(1L)), eq(new ProductId(100L)));
        MockMultipartFile file = new MockMultipartFile("file", "document.txt", "text/plain", "not allowed".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/submissions")
                        .file(file)
                        .param("productId", "100")
                        .header("X-Member-Id", "1"))
                .andExpect(status().isBadRequest());
    }

    private Submission submission(SubmissionId id, SubmissionStatus status, String failureReason, String extractedText) {
        return Submission.reconstitute(
                id,
                new MemberId(1L),
                new ProductId(100L),
                SubmissionType.PDF,
                new StoredPath("s3://autotest-submissions/document.pdf"),
                extractedText,
                status,
                failureReason,
                Instant.parse("2026-10-01T00:00:00Z")
        );
    }
}
