package com.autotest.test_management_service.infrastructure.parser;

import com.autotest.test_management_service.domain.submission.FileFormat;
import org.springframework.stereotype.Component;

@Component
public final class ExcelFileParser extends AbstractStubFileParser {
    public ExcelFileParser() {
        super(FileFormat.XLSX, FileFormat.XLS);
    }
}