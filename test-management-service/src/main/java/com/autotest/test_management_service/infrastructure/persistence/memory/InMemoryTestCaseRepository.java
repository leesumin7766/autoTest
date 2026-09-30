package com.autotest.test_management_service.infrastructure.persistence.memory;

import com.autotest.test_management_service.domain.port.TestCaseRepository;
import com.autotest.test_management_service.domain.submission.TestCase;
import com.autotest.test_management_service.domain.submission.TestCaseId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class InMemoryTestCaseRepository implements TestCaseRepository {
    private final ConcurrentMap<TestCaseId, TestCase> testCases = new ConcurrentHashMap<>();

    @Override
    public void saveAll(List<TestCase> testCases) {
        testCases.forEach(testCase -> this.testCases.put(testCase.id(), testCase));
    }
}