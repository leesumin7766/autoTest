package com.autotest.test_management_service.infrastructure.storage;

import com.autotest.test_management_service.domain.submission.StoredPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class S3FileStorageAdapterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void storesLoadsAndDeletesLocallyWhenS3IsUnavailable() throws Exception {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(503).message("S3 unavailable").build());
        S3FileStorageAdapter adapter = new S3FileStorageAdapter(
                s3Client,
                "autotest-docs",
                true,
                temporaryDirectory
        );
        byte[] content = {1, 2, 3, 4};

        StoredPath storedPath = adapter.store(
                new ByteArrayInputStream(content),
                "manual.pdf",
                "application/pdf"
        );

        assertTrue(storedPath.isLocal());
        assertTrue(storedPath.value().startsWith("local:" + temporaryDirectory.toAbsolutePath().normalize()));
        try (InputStream loadedContent = adapter.load(storedPath)) {
            assertArrayEquals(content, loadedContent.readAllBytes());
        }

        adapter.delete(storedPath);

        try (var storedFiles = Files.walk(temporaryDirectory)) {
            assertTrue(storedFiles.noneMatch(Files::isRegularFile));
        }
    }
}