package com.autotest.test_management_service.presentation.controller;

import com.autotest.test_management_service.application.service.SubmissionService;
import com.autotest.test_management_service.domain.submission.ProductId;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.vo.MemberId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmissionId> submit(
            @RequestPart("file") MultipartFile file,
            @RequestParam("productId") Long productId,
            @RequestHeader("X-Member-Id") Long memberId
    ) {
        SubmissionId submissionId = submissionService.submit(
                file,
                new MemberId(memberId),
                new ProductId(productId)
        );
        return ResponseEntity.ok(submissionId);
    }
}
