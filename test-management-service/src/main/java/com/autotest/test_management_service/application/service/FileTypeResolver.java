package com.autotest.test_management_service.application.service;

import com.autotest.test_management_service.domain.vo.SubmissionType;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public final class FileTypeResolver {
    public SubmissionType resolve(String originalFilename) {
        Objects.requireNonNull(originalFilename, "originalFilename");
        int extensionSeparator = originalFilename.lastIndexOf('.');
        if (extensionSeparator < 0 || extensionSeparator == originalFilename.length() - 1) {
            return SubmissionType.UNKNOWN;
        }
        return SubmissionType.fromExtension(originalFilename.substring(extensionSeparator + 1));
    }
}