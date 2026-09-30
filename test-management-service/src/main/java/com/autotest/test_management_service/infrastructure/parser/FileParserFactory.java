package com.autotest.test_management_service.infrastructure.parser;

import com.autotest.test_management_service.domain.submission.FileFormat;
import com.autotest.test_management_service.domain.submission.FileParser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public final class FileParserFactory {
    private final List<FileParser> parsers;

    public FileParserFactory(List<FileParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    public FileParser getParser(FileFormat format) {
        Objects.requireNonNull(format, "format");
        return parsers.stream()
                .filter(parser -> parser.supports(format))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No parser supports format: " + format));
    }
}