package com.autotest.test_management_service.presentation.controller;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionService;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionService.StatusView;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/submissions/{submissionId}/product-description")
@RequiredArgsConstructor
public class ProductDescriptionController {
    private final ProductDescriptionService service;

    @PostMapping
    public ResponseEntity<StatusView> start(@PathVariable UUID submissionId,
                                            @RequestHeader("X-Member-Id") Long memberId) {
        var result = service.start(submissionId, memberId);
        StatusView body = service.status(submissionId, memberId);
        return ResponseEntity.status(result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK).body(body);
    }

    @GetMapping
    public StatusView status(@PathVariable UUID submissionId, @RequestHeader("X-Member-Id") Long memberId) {
        return service.status(submissionId, memberId);
    }

    @PostMapping("/{jobId}/cancel")
    public StatusView cancel(@PathVariable UUID submissionId, @PathVariable UUID jobId,
                             @RequestHeader("X-Member-Id") Long memberId) {
        return service.cancel(submissionId, jobId, memberId);
    }

    @PostMapping("/{jobId}/rerender")
    public ResponseEntity<StatusView> rerender(@PathVariable UUID submissionId, @PathVariable UUID jobId,
                                               @RequestHeader("X-Member-Id") Long memberId) {
        service.rerender(submissionId, jobId, memberId);
        return ResponseEntity.accepted().body(service.status(submissionId, memberId));
    }

    @GetMapping("/{jobId}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID submissionId, @PathVariable UUID jobId,
                                           @RequestHeader("X-Member-Id") Long memberId) {
        var download = service.download(submissionId, jobId, memberId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.content());
    }

    @ExceptionHandler(ProductDescriptionException.class)
    public ResponseEntity<Map<String, String>> handle(ProductDescriptionException exception) {
        return ResponseEntity.status(exception.status())
                .body(Map.of("code", exception.code(), "message", exception.getMessage()));
    }
}
