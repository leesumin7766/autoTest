package com.autotest.test_management_service.domain.port;

import com.autotest.test_management_service.domain.submission.TestCase;

import java.util.List;

public interface TestCaseRepository {
    void saveAll(List<TestCase> testCases);
}