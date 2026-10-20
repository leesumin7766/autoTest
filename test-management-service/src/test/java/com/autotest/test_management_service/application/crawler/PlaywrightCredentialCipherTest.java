package com.autotest.test_management_service.application.crawler;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;

class PlaywrightCredentialCipherTest {
    @TempDir Path temp;

    @Test void encryptedPasswordSurvivesRestartAndIsBoundToSubmission() {
        var key = temp.resolve("keys/credentials.key");
        var cipher = new PlaywrightCredentialCipher(key.toString());
        var id = UUID.randomUUID();
        String password = " test-password-한글 ";
        String encrypted = cipher.encrypt(id, password);
        assertFalse(encrypted.contains(password));
        assertNotEquals(encrypted, cipher.encrypt(id, password));
        var restarted = new PlaywrightCredentialCipher(key.toString());
        assertEquals(password, restarted.decrypt(id, encrypted));
        assertThrows(ProductDescriptionException.class, () -> restarted.decrypt(UUID.randomUUID(), encrypted));
        assertThrows(ProductDescriptionException.class, () -> restarted.decrypt(id, encrypted + "tampered"));
    }

    @Test void losingKeyFailsClosedWithoutGeneratingReplacement() throws Exception {
        Path key = temp.resolve("credentials.key");
        var cipher = new PlaywrightCredentialCipher(key.toString());
        var id = UUID.randomUUID();
        String encrypted = cipher.encrypt(id, "password");
        Files.delete(key);
        assertThrows(ProductDescriptionException.class, () -> cipher.decrypt(id, encrypted));
        assertFalse(Files.exists(key));
    }
}
