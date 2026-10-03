package com.autotest.test_management_service.infrastructure.storage;

import com.autotest.test_management_service.application.storage.FileStoragePort;
import com.autotest.test_management_service.domain.submission.StoredPath;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public final class S3FileStorageAdapter implements FileStoragePort {
    private static final Logger LOGGER = LoggerFactory.getLogger(S3FileStorageAdapter.class);
    private static final String S3_PATH_PREFIX = "s3://";
    private static final String LOCAL_PATH_PREFIX = "local:";
    private static final Path DEFAULT_LOCAL_ROOT = Path.of("/tmp/autotest-docs");

    private final S3Client s3Client;

    @Value("${s3.bucket}")
    private String bucket;

    @Value("${s3.enabled:true}")
    private boolean enabled;

    private Path localRoot = DEFAULT_LOCAL_ROOT;

    S3FileStorageAdapter(S3Client s3Client, String bucket, boolean enabled, Path localRoot) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client");
        this.bucket = Objects.requireNonNull(bucket, "bucket");
        this.enabled = enabled;
        this.localRoot = Objects.requireNonNull(localRoot, "localRoot").toAbsolutePath().normalize();
    }

    @Override
    public StoredPath store(InputStream content, String originalName, String contentType) {
        return storeWithKey(content, createObjectKey(originalName), contentType);
    }

    @Override
    public StoredPath storeUnderPrefix(String keyPrefix, InputStream content, String fileName, String contentType) {
        Objects.requireNonNull(keyPrefix, "keyPrefix");
        if (!keyPrefix.matches("[a-z0-9][a-z0-9/_-]*") || keyPrefix.contains("//") || keyPrefix.endsWith("/")) {
            throw new IllegalArgumentException("Invalid storage key prefix");
        }
        return storeWithKey(content, keyPrefix + "/" + createObjectKey(fileName), contentType);
    }

    private StoredPath storeWithKey(InputStream content, String objectKey, String contentType) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(contentType, "contentType");

        byte[] bytes = readContent(content);
        if (!enabled) {
            return storeLocally(objectKey, bytes);
        }

        try {
            ensureBucketExists();
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(bytes));
            return new StoredPath(S3_PATH_PREFIX + bucket + "/" + objectKey);
        } catch (SdkException exception) {
            LOGGER.warn("S3 upload failed; falling back to local storage", exception);
            return storeLocally(objectKey, bytes);
        }
    }

    @Override
    public InputStream load(StoredPath storedPath) {
        Objects.requireNonNull(storedPath, "storedPath");
        if (storedPath.isLocal()) {
            return openLocalFile(localFilePath(storedPath));
        }
        if (!storedPath.isS3()) {
            throw new IllegalArgumentException("Unsupported stored path: " + storedPath.value());
        }

        S3Location location = parseS3Location(storedPath.value());
        try {
            return s3Client.getObject(GetObjectRequest.builder()
                    .bucket(location.bucket())
                    .key(location.key())
                    .build());
        } catch (SdkException exception) {
            throw new IllegalStateException("Failed to load submission file from S3", exception);
        }
    }

    @Override
    public void delete(StoredPath storedPath) {
        Objects.requireNonNull(storedPath, "storedPath");
        if (storedPath.isLocal()) {
            try {
                Files.deleteIfExists(localFilePath(storedPath));
                return;
            } catch (IOException exception) {
                throw new UncheckedIOException("Failed to delete local submission file", exception);
            }
        }
        if (!storedPath.isS3()) {
            throw new IllegalArgumentException("Unsupported stored path: " + storedPath.value());
        }

        S3Location location = parseS3Location(storedPath.value());
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(location.bucket())
                    .key(location.key())
                    .build());
        } catch (SdkException exception) {
            throw new IllegalStateException("Failed to delete submission file from S3", exception);
        }
    }

    private void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) {
                throw exception;
            }
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }

    private StoredPath storeLocally(String objectKey, byte[] bytes) {
        Path target = localRoot.resolve(objectKey).normalize();
        if (!target.startsWith(localRoot)) {
            throw new IllegalArgumentException("Invalid local storage path");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return new StoredPath(LOCAL_PATH_PREFIX + target);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to store submission file locally", exception);
        }
    }

    private Path localFilePath(StoredPath storedPath) {
        String pathValue = storedPath.value().substring(LOCAL_PATH_PREFIX.length());
        Path path = Path.of(pathValue).toAbsolutePath().normalize();
        if (pathValue.isBlank() || !path.startsWith(localRoot)) {
            throw new IllegalArgumentException("Invalid local storage path");
        }
        return path;
    }

    private S3Location parseS3Location(String storedPath) {
        String location = storedPath.substring(S3_PATH_PREFIX.length());
        int separator = location.indexOf('/');
        if (separator <= 0 || separator == location.length() - 1) {
            throw new IllegalArgumentException("Invalid S3 stored path: " + storedPath);
        }
        return new S3Location(location.substring(0, separator), location.substring(separator + 1));
    }

    private InputStream openLocalFile(Path path) {
        try {
            return new FileInputStream(path.toFile());
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load local submission file", exception);
        }
    }

    private byte[] readContent(InputStream content) {
        try {
            return content.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read submission content", exception);
        }
    }

    private String createObjectKey(String originalName) {
        String fileName = originalName.replace('\\', '/');
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1).strip();
        if (fileName.isBlank() || fileName.equals(".") || fileName.equals("..")) {
            throw new IllegalArgumentException("Invalid original file name");
        }
        return UUID.randomUUID() + "-" + fileName;
    }

    private record S3Location(String bucket, String key) {
    }
}