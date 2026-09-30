package com.autotest.test_management_service.domain.service;

import com.autotest.test_management_service.domain.submission.ProductId;
import com.autotest.test_management_service.domain.submission.StoredPath;
import com.autotest.test_management_service.domain.submission.Submission;
import com.autotest.test_management_service.domain.submission.SubmissionFactory;
import com.autotest.test_management_service.domain.submission.TestCase;
import com.autotest.test_management_service.domain.vo.MemberId;
import com.autotest.test_management_service.domain.vo.SubmissionType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public final class SubmissionDomainService {
    public Submission create(
            MemberId memberId,
            ProductId productId,
            SubmissionType submissionType,
            StoredPath storedPath,
            String extractedText,
            List<TestCase> testCases
    ) {
        return SubmissionFactory.create(memberId, productId, submissionType, storedPath, extractedText, testCases);
    }
}