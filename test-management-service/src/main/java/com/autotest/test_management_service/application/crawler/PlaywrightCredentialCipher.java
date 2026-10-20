package com.autotest.test_management_service.application.crawler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;

/** Independent key stored outside the database, on a persistent Docker volume. */
@Component
public class PlaywrightCredentialCipher {
    private final Path keyPath;
    private final SecureRandom random = new SecureRandom();
    public PlaywrightCredentialCipher(@Value("${playwright.credentials-key-file:./.runtime/playwright/credentials.key}") String keyFile) {
        this.keyPath = Path.of(keyFile);
    }

    private synchronized byte[] key(boolean create) throws Exception {
        if (!Files.exists(keyPath)) {
            if (!create) throw new IllegalStateException("Credential key is missing");
            Files.createDirectories(keyPath.toAbsolutePath().getParent());
            byte[] generated = new byte[32];
            random.nextBytes(generated);
            try {
                Files.createFile(keyPath, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            } catch (UnsupportedOperationException windows) {
                Files.createFile(keyPath);
            }
            Files.write(keyPath, generated, StandardOpenOption.WRITE);
        }
        byte[] key = Files.readAllBytes(keyPath);
        if (key.length != 32) throw new IllegalStateException("Invalid credential key");
        return key;
    }

    public String encrypt(UUID submissionId, String password) {
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(true), "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(submissionId.toString().getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(iv) + ":"
                    + Base64.getEncoder().encodeToString(cipher.doFinal(password.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new ProductDescriptionException(HttpStatus.SERVICE_UNAVAILABLE, "PLAYWRIGHT_CREDENTIAL_STORAGE_UNAVAILABLE",
                    "로그인 정보 암호화 저장소를 사용할 수 없습니다. 키 파일 및 저장 볼륨을 확인해 주세요.");
        }
    }

    // Only the future browser worker should call this; never expose through a public setup response.
    public String decrypt(UUID submissionId, String ciphertext) {
        try {
            String[] parts = ciphertext.split(":", -1);
            if (parts.length != 3 || !"v1".equals(parts[0])) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(false), "AES"),
                    new GCMParameterSpec(128, Base64.getDecoder().decode(parts[1])));
            cipher.updateAAD(submissionId.toString().getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (Exception failure) {
            throw new ProductDescriptionException(HttpStatus.SERVICE_UNAVAILABLE, "PLAYWRIGHT_CREDENTIAL_STORAGE_UNAVAILABLE",
                    "저장된 로그인 정보를 읽을 수 없습니다. 키 저장소를 확인하거나 비밀번호를 다시 입력해 주세요.");
        }
    }
}
