package com.autotest.test_management_service.presentation.controller;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autotest.test_management_service.application.service.AiDocumentDeliveryService;
import com.autotest.test_management_service.application.service.SubmissionService;
import com.autotest.test_management_service.domain.submission.FileFormat;
import com.autotest.test_management_service.domain.submission.ProductId;
import com.autotest.test_management_service.domain.submission.StoredPath;
import com.autotest.test_management_service.domain.submission.Submission;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.submission.SubmissionStatus;
import com.autotest.test_management_service.domain.submission.SubmittedDocument;
import com.autotest.test_management_service.domain.vo.MemberId;
import com.autotest.test_management_service.domain.vo.SubmissionType;

@WebMvcTest(SubmissionController.class)
class SubmissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

        @MockitoBean
    private SubmissionService submissionService;

                @MockitoBean
        private AiDocumentDeliveryService aiDocumentDeliveryService;

    @Test
    void submitReturns200AndSubmissionId() throws Exception {
        SubmissionId mockSubmissionId = SubmissionId.generate();
        Submission parsedSubmission = submission(mockSubmissionId, SubmissionStatus.PARSED);
        when(submissionService.submit(any(), any(com.autotest.test_management_service.domain.submission.SubmissionType.class), eq(new MemberId(1L)), eq(new ProductId(100L))))
                .thenReturn(parsedSubmission);
        when(submissionService.findDocumentsBySubmissionId(mockSubmissionId)).thenReturn(List.of(
                document(mockSubmissionId, SubmissionStatus.PARSED, null)));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                "dummy pdf content".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/submissions")
                        .file(file)
                        .param("productId", "100")
                        .param("role", "AGREEMENT")
                        .header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionId").value(mockSubmissionId.value().toString()))
                .andExpect(jsonPath("$.status").value("PARSED"))
                .andExpect(jsonPath("$.failureReason").doesNotExist());

        verify(submissionService).submit(any(), eq(com.autotest.test_management_service.domain.submission.SubmissionType.AGREEMENT), eq(new MemberId(1L)), eq(new ProductId(100L)));
    }

    @Test
    void submitReturnsFailedStatusAndReasonWhenExtractionFails() throws Exception {
        SubmissionId id = SubmissionId.generate();
        when(submissionService.submit(any(), any(com.autotest.test_management_service.domain.submission.SubmissionType.class), eq(new MemberId(1L)), eq(new ProductId(100L))))
                .thenReturn(submission(id, SubmissionStatus.FAILED));
        when(submissionService.findDocumentsBySubmissionId(id)).thenReturn(List.of(
                document(id, SubmissionStatus.FAILED, "추출할 셀 내용이 없습니다")));
        MockMultipartFile file = new MockMultipartFile("file", "empty.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] {1});

        mockMvc.perform(multipart("/api/submissions")
                        .file(file)
                        .param("productId", "100")
                        .param("role", "AGREEMENT")
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
                submission(id, SubmissionStatus.FAILED)
        ));
        when(submissionService.findDocumentsBySubmissionId(id)).thenReturn(List.of(
                document(id, SubmissionStatus.FAILED, "문서에서 텍스트를 추출할 수 없습니다")));

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
    void retryAiDeliveryRequiresSubmissionOwner() throws Exception {
        SubmissionId id = SubmissionId.generate();
        when(submissionService.findById(id)).thenReturn(Optional.of(
                submission(id, SubmissionStatus.PARSED)));
        when(aiDocumentDeliveryService.deliverIfReady(id)).thenReturn(
                new AiDocumentDeliveryService.DeliveryResult("FAILED", 1, "AI service delivery failed", null));

        mockMvc.perform(post("/api/submissions/{id}/ai-delivery/retry", id.value())
                        .header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        mockMvc.perform(post("/api/submissions/{id}/ai-delivery/retry", id.value())
                        .header("X-Member-Id", "2"))
                .andExpect(status().isForbidden());
    }

    @Test
    void failedDocumentReplacementIsRejectedAfterAiDeliveryWasAttempted() throws Exception {
        SubmissionId id = SubmissionId.generate();
        when(submissionService.findById(id)).thenReturn(Optional.of(
                submission(id, SubmissionStatus.PARSED)));
        when(aiDocumentDeliveryService.getStatus(id.value())).thenReturn(
                new AiDocumentDeliveryService.DeliveryResult("FAILED", 1, "AI service delivery failed", null));
        MockMultipartFile file = new MockMultipartFile(
                "file", "replacement.pdf", "application/pdf", "pdf".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/submissions/{id}/files/{role}/replace", id.value(), "AGREEMENT")
                        .file(file)
                        .header("X-Member-Id", "1"))
                .andExpect(status().isConflict());

        org.mockito.Mockito.verify(submissionService, org.mockito.Mockito.never())
                .replaceFailedDocument(any(), any(), any(), any());
    }

    @Test
    void replacementIsAllowedForBlockedOrResetSubmissionsButNotPendingOrDelivered() throws Exception {
        SubmissionId id = SubmissionId.generate();
        when(submissionService.findById(id)).thenReturn(Optional.of(
                submission(id, SubmissionStatus.PARSED)));
        when(submissionService.replaceFailedDocument(any(), any(), any(), any())).thenReturn(
                document(id, SubmissionStatus.PARSED, null));
        MockMultipartFile file = new MockMultipartFile(
                "file", "replacement.pdf", "application/pdf", "pdf".getBytes(StandardCharsets.UTF_8));

        record Case(String status, int attempts, int expected) { }
        for (Case replacementCase : List.of(
                new Case("BLOCKED", 1, 200), new Case("NOT_READY", 1, 200), new Case("NOT_READY", 0, 200),
                new Case("PENDING", 1, 409), new Case("DELIVERED", 1, 409), new Case("FAILED", 1, 409))) {
            when(aiDocumentDeliveryService.getStatus(id.value())).thenReturn(
                    new AiDocumentDeliveryService.DeliveryResult(replacementCase.status(), replacementCase.attempts(), null, null));

            mockMvc.perform(multipart("/api/submissions/{id}/files/{role}/replace", id.value(), "AGREEMENT")
                            .file(file)
                            .header("X-Member-Id", "1"))
                    .andExpect(status().is(replacementCase.expected()));
        }
    }

    @Test
    void unsupportedUploadFormatStillReturns400() throws Exception {
        doThrow(new IllegalArgumentException("허용되지 않은 파일 형식입니다. PDF, Excel, HWP, Word만 업로드 가능합니다."))
                .when(submissionService).submit(any(), any(com.autotest.test_management_service.domain.submission.SubmissionType.class), eq(new MemberId(1L)), eq(new ProductId(100L)));
        MockMultipartFile file = new MockMultipartFile("file", "document.txt", "text/plain", "not allowed".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/submissions")
                        .file(file)
                        .param("productId", "100")
                        .param("role", "AGREEMENT")
                        .header("X-Member-Id", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string("허용되지 않은 파일 형식입니다. PDF, Excel, HWP, Word만 업로드 가능합니다."));
    }

        private Submission submission(SubmissionId id, SubmissionStatus status) {
        return Submission.reconstitute(
                id,
                new MemberId(1L),
                new ProductId(100L),
                status,
                Instant.parse("2026-10-01T00:00:00Z")
        );
    }

        private SubmittedDocument document(SubmissionId id, SubmissionStatus status, String failureReason) {
                return new SubmittedDocument(UUID.randomUUID(), id,
                                com.autotest.test_management_service.domain.submission.SubmissionType.AGREEMENT,
                                SubmissionType.PDF, FileFormat.PDF, "document.pdf", "application/pdf",
                                new StoredPath("s3://autotest-docs/document.pdf"),
                                status == SubmissionStatus.PARSED ? "extracted text" : "", status, failureReason, Instant.now());
        }
}
