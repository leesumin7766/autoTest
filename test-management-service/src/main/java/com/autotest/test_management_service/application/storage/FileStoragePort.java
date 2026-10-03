package com.autotest.test_management_service.application.storage;

import com.autotest.test_management_service.domain.submission.StoredPath;

import java.io.InputStream;

public interface FileStoragePort {
    StoredPath store(InputStream content, String originalName, String contentType);

    /** Stores a generated artifact under a key prefix that keeps it apart from uploaded originals. */
    StoredPath storeUnderPrefix(String keyPrefix, InputStream content, String fileName, String contentType);

    InputStream load(StoredPath path);

    void delete(StoredPath path);
}