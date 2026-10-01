package com.autotest.test_management_service.infrastructure.parser;

import com.autotest.test_management_service.domain.vo.SubmissionType;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Parser for plain text source files: Java, Python, JavaScript.
 * Inherits test-case parsing logic from {@link AbstractTextTestCaseParser}.
 */
@Component
public final class SimpleTextFileParser extends AbstractTextTestCaseParser {
    private static final Set<SubmissionType> SUPPORTED_TYPES = Set.of(
            SubmissionType.JAVA,
            SubmissionType.PYTHON,
            SubmissionType.JAVASCRIPT
    );

    @Override
    public boolean supports(SubmissionType type) {
        return SUPPORTED_TYPES.contains(type);
    }
}