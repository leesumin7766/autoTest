package com.autotest.test_management_service.application.port;

import com.autotest.test_management_service.domain.submission.TestCase;
import com.autotest.test_management_service.domain.vo.SubmissionType;

import java.io.InputStream;
import java.util.List;

public interface FileParser {
    boolean supports(SubmissionType type);

    List<TestCase> parse(InputStream input, String filename);

    String extractText(InputStream input);
}