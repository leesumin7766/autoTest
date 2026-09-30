package com.autotest.test_management_service.infrastructure.parser;

import com.autotest.test_management_service.domain.submission.FileFormat;
import org.springframework.stereotype.Component;

@Component
public final class DocxFileParser extends AbstractStubFileParser {
    public DocxFileParser() {
        super(FileFormat.DOCX);
    }
}