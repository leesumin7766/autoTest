package com.autotest.test_management_service.presentation.controller;

import com.autotest.test_management_service.application.service.SubmissionService;
import com.autotest.test_management_service.domain.submission.ProductId;
import com.autotest.test_management_service.domain.submission.Submission;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.submission.SubmissionStatus;
import com.autotest.test_management_service.domain.submission.SubmittedDocument;
import com.autotest.test_management_service.domain.vo.MemberId;
import lombok.RequiredArgsConstructor;
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

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

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
            return ResponseEntity.ok(SubmissionResponse.from(submission, submissionService.findDocumentsBySubmissionId(submission.submissionId())));
            }

            @PostMapping(path = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
            public ResponseEntity<UploadedDocumentResponse> uploadAdditional(
                @PathVariable UUID id,
                @RequestPart("file") MultipartFile file,
                @RequestParam("role") com.autotest.test_management_service.domain.submission.SubmissionType role,
                @RequestHeader("X-Member-Id") Long memberId
            ) {
            SubmittedDocument document = submissionService.uploadAdditional(
                SubmissionId.of(id), file, role, new MemberId(memberId));
            return ResponseEntity.ok(UploadedDocumentResponse.from(document));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubmissionResponse> findById(@PathVariable UUID id) {
        return submissionService.findById(SubmissionId.of(id))
            .map(submission -> SubmissionResponse.from(
                submission, submissionService.findDocumentsBySubmissionId(submission.submissionId())))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record SubmissionResponse(
            UUID submissionId,
            SubmissionStatus status,
            String failureReason,
            List<UploadedDocumentResponse> documents
    ) {
        private static SubmissionResponse from(Submission submission, List<SubmittedDocument> documents) {
            return new SubmissionResponse(
                    submission.submissionId().value(),
                    submission.status(),
                    submission.failureReason(),
                    documents.stream().map(UploadedDocumentResponse::from).toList()
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
            String failureReason
    ) {
        private static UploadedDocumentResponse from(SubmittedDocument document) {
            return new UploadedDocumentResponse(
                    document.fileId(), document.role().name(), document.fileType().name(),
                    document.format().name(), document.originalFilename(), document.storedPath().value(),
                    document.extractedText(), document.status(), document.failureReason());
        }
    }
}
