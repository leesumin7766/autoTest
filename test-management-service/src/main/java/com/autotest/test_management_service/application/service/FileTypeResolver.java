package com.autotest.test_management_service.application.service;

import com.autotest.test_management_service.domain.vo.SubmissionType;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Component
public final class FileTypeResolver {

    private static final Set<String> PDF_MIMES = Set.of("application/pdf");
    private static final Set<String> EXCEL_MIMES = Set.of("application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final Set<String> HWP_MIMES = Set.of("application/x-hwp", "application/haansofthwp", "application/vnd.hancom.hwp", "application/vnd.hancom.hwp.document", "application/hwp", "application/vnd.hancom.hwpx");
    private static final Set<String> WORD_MIMES = Set.of("application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    public SubmissionType resolve(String originalFilename, String contentType) {
        Objects.requireNonNull(originalFilename, "originalFilename");
        int extensionSeparator = originalFilename.lastIndexOf('.');
        if (extensionSeparator < 0 || extensionSeparator == originalFilename.length() - 1) {
            throw new IllegalArgumentException("허용되지 않은 파일 형식입니다. PDF, Excel, HWP, Word만 업로드 가능합니다.");
        }
        String extension = originalFilename.substring(extensionSeparator + 1).toLowerCase(Locale.ROOT);
        String mime = contentType == null
            ? ""
            : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);

        if ("pdf".equals(extension) && PDF_MIMES.contains(mime)) {
            return SubmissionType.PDF;
        }
        if (("xls".equals(extension) || "xlsx".equals(extension)) && EXCEL_MIMES.contains(mime)) {
            return SubmissionType.EXCEL;
        }
        if (("hwp".equals(extension) || "hwpx".equals(extension)) && HWP_MIMES.contains(mime)) {
            return SubmissionType.HWP;
        }
        if (("doc".equals(extension) || "docx".equals(extension)) && WORD_MIMES.contains(mime)) {
            return SubmissionType.WORD;
        }

        throw new IllegalArgumentException("허용되지 않은 파일 형식입니다. PDF, Excel, HWP, Word만 업로드 가능합니다.");
    }
}