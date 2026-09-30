package com.autotest.test_management_service.infrastructure.parser;

import com.autotest.test_management_service.domain.submission.FileFormat;
import org.springframework.stereotype.Component;

@Component
public final class HwpFileParser extends AbstractStubFileParser {
    // TODO: ai-service에 위임 예정
    public HwpFileParser() {
        super(FileFormat.HWP);
    }
}