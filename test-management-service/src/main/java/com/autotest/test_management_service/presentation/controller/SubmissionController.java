package com.autotest.test_management_service.presentation.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.autotest.test_management_service.application.service.AiDocumentDeliveryService;
import com.autotest.test_management_service.application.service.SubmissionService;
import com.autotest.test_management_service.domain.submission.ProductId;
import com.autotest.test_management_service.domain.submission.Submission;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.submission.SubmissionStatus;
import com.autotest.test_management_service.domain.submission.SubmittedDocument;
import com.autotest.test_management_service.domain.vo.MemberId;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;
    private final AiDocumentDeliveryService aiDocumentDeliveryService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmissionResponse> submit(
            @RequestPart("file") MultipartFile file,
            @RequestParam("productId") Long productId,
                @RequestParam(value = "role", required = false) com.autotest.test_management_service.domain.submission.SubmissionType role,
            @RequestHeader("X-Member-Id") Long memberId
    ) {
        Submission submission = submissionService.submit(
                file,
                role == null ? com.autotest.test_management_service.domain.submission.SubmissionType.AGREEMENT : role,
                new MemberId(memberId),
                new ProductId(productId)
        );
        AiDocumentDeliveryService.DeliveryResult delivery = aiDocumentDeliveryService.deliverIfReady(submission.submissionId());
        return ResponseEntity.ok(SubmissionResponse.from(
            submission,
            submissionService.findDocumentsBySubmissionId(submission.submissionId()),
            delivery));
        }

        @PostMapping(path = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<UploadedDocumentResponse> uploadAdditional(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestParam("role") com.autotest.test_management_service.domain.submission.SubmissionType role,
            @RequestHeader("X-Member-Id") Long memberId
        ) {
        SubmissionId submissionId = SubmissionId.of(id);
        SubmittedDocument document = submissionService.uploadAdditional(
            submissionId, file, role, new MemberId(memberId));
        return ResponseEntity.ok(UploadedDocumentResponse.from(
            document, aiDocumentDeliveryService.deliverIfReady(submissionId)));
    }

    @PostMapping(path = "/{id}/files/{role}/replace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadedDocumentResponse> replaceFailedDocument(
            @PathVariable UUID id,
            @PathVariable com.autotest.test_management_service.domain.submission.SubmissionType role,
            @RequestPart("file") MultipartFile file,
            @RequestHeader("X-Member-Id") Long memberId
    ) {
        SubmissionId submissionId = SubmissionId.of(id);
        Submission submission = submissionService.findById(submissionId).orElse(null);
        if (submission == null) {
            return ResponseEntity.notFound().build();
        }
        if (!submission.memberId().equals(new MemberId(memberId))) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
        AiDocumentDeliveryService.DeliveryResult delivery = aiDocumentDeliveryService.getStatus(id);
        boolean closed = "PENDING".equals(delivery.status()) || "DELIVERED".equals(delivery.status())
                || (delivery.attempts() > 0 && !"BLOCKED".equals(delivery.status())
                    && !"NOT_READY".equals(delivery.status()));
        if (closed) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).build();
        }
        SubmittedDocument document = submissionService.replaceFailedDocument(
                submissionId, file, role, new MemberId(memberId));
        return ResponseEntity.ok(UploadedDocumentResponse.from(
                document, aiDocumentDeliveryService.deliverIfReady(submissionId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubmissionResponse> findById(@PathVariable UUID id) {
        return submissionService.findById(SubmissionId.of(id))
            .map(submission -> SubmissionResponse.from(
                submission,
                submissionService.findDocumentsBySubmissionId(submission.submissionId()),
                aiDocumentDeliveryService.getStatus(id)))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/ai-delivery/retry")
    public ResponseEntity<AiDocumentDeliveryService.DeliveryResult> retryAiDelivery(
            @PathVariable UUID id,
            @RequestHeader("X-Member-Id") Long memberId
    ) {
        Submission submission = submissionService.findById(SubmissionId.of(id)).orElse(null);
        if (submission == null) {
            return ResponseEntity.notFound().build();
        }
        if (!submission.memberId().equals(new MemberId(memberId))) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(aiDocumentDeliveryService.deliverIfReady(SubmissionId.of(id)));
    }

    public record SubmissionResponse(
            UUID submissionId,
            SubmissionStatus status,
            String failureReason,
                List<UploadedDocumentResponse> documents,
                AiDocumentDeliveryService.DeliveryResult aiDelivery
    ) {
            private static SubmissionResponse from(
                Submission submission,
                List<SubmittedDocument> documents,
                AiDocumentDeliveryService.DeliveryResult aiDelivery
            ) {
                String failureReason = documents.stream()
                    .filter(document -> document.status() == SubmissionStatus.FAILED)
                    .map(SubmittedDocument::failureReason)
                    .filter(reason -> reason != null && !reason.isBlank())
                    .findFirst()
                    .orElse(null);
            return new SubmissionResponse(
                    submission.submissionId().value(),
                    submission.status(),
                    failureReason,
                    documents.stream().map(document -> UploadedDocumentResponse.from(document, aiDelivery)).toList(),
                    aiDelivery
            );
        }
    }

    public record UploadedDocumentResponse(
            UUID fileId,
            String role,
            String fileType,
            String format,
            String originalFilename,
            String storedPath,
            String extractedText,
            SubmissionStatus status,
                String failureReason,
                AiDocumentDeliveryService.DeliveryResult aiDelivery
    ) {
            private static UploadedDocumentResponse from(
                SubmittedDocument document,
                AiDocumentDeliveryService.DeliveryResult aiDelivery
            ) {
            return new UploadedDocumentResponse(
                    document.fileId(), document.role().name(), document.fileType().name(),
                    document.format().name(), document.originalFilename(), document.storedPath().value(),
                    document.extractedText(), document.status(), document.failureReason(), aiDelivery);
        }
    }
}
