package com.autotest.test_management_service.domain.service;

import org.springframework.stereotype.Component;

import com.autotest.test_management_service.domain.submission.ProductId;
import com.autotest.test_management_service.domain.submission.Submission;
import com.autotest.test_management_service.domain.submission.SubmissionFactory;
import com.autotest.test_management_service.domain.vo.MemberId;

@Component
public final class SubmissionDomainService {
    public Submission create(MemberId memberId, ProductId productId) {
        return SubmissionFactory.create(memberId, productId);
    }
}