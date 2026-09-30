package com.autotest.test_management_service.infrastructure.parser;

import com.autotest.test_management_service.domain.submission.FileFormat;
import com.autotest.test_management_service.domain.submission.FileMetadata;
import com.autotest.test_management_service.domain.submission.FileParser;
import com.autotest.test_management_service.domain.submission.ParsedContent;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Set;

abstract class AbstractStubFileParser implements FileParser {
    private final Set<FileFormat> supportedFormats;

    AbstractStubFileParser(FileFormat... supportedFormats) {
        this.supportedFormats = Set.of(supportedFormats);
    }

    @Override
    public boolean supports(FileFormat format) {
        return supportedFormats.contains(format);
    }

    @Override
    public ParsedContent parse(FileMetadata metadata, InputStream content) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(content, "content");

        FileFormat format = detectFormat(metadata.originalName());
        if (!supports(format)) {
            throw new IllegalArgumentException("Unsupported file format for parser: " + format);
        }

        try {
            long actualSizeBytes = content.readAllBytes().length;
            return new ParsedContent(metadata, format, "", actualSizeBytes);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read submission content", exception);
        }
    }

    private FileFormat detectFormat(String fileName) {
        int extensionSeparator = fileName.lastIndexOf('.');
        if (extensionSeparator < 0 || extensionSeparator == fileName.length() - 1) {
            throw new IllegalArgumentException("Filename must have a supported extension: " + fileName);
        }
        return FileFormat.fromExtension(fileName.substring(extensionSeparator + 1));
    }
}