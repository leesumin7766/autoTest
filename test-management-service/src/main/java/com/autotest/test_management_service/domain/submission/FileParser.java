package com.autotest.test_management_service.domain.submission;

import java.io.InputStream;

public interface FileParser {
    boolean supports(FileFormat format);

    ParsedContent parse(FileMetadata metadata, InputStream content);
}