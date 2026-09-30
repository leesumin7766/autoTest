package com.autotest.test_management_service.application.service;

import com.autotest.test_management_service.application.port.FileParser;
import com.autotest.test_management_service.domain.vo.SubmissionType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public final class FileParserFactory {
    private final List<FileParser> parsers;

    public FileParserFactory(List<FileParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    public FileParser getParser(SubmissionType type) {
        return parsers.stream()
                .filter(parser -> parser.supports(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No parser supports submission type: " + type));
    }
}