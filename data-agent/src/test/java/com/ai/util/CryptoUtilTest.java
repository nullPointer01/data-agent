package com.ai.util;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CryptoUtilTest {

    @Test
    void encryptAndDecryptRoundTrip() {
        CryptoUtil util = createUtil("test-secret-key-for-unit-tests-1234");
        String encrypted = util.encrypt("hello world");
        assertTrue(encrypted.startsWith("ENC:"));
        assertEquals("hello world", util.decrypt(encrypted));
    }

    @Test
    void encryptNullReturnsNull() {
        CryptoUtil util = createUtil("test-key-1234567890123456");
        assertNull(util.encrypt(null));
    }

    @Test
    void encryptEmptyReturnsEmpty() {
        CryptoUtil util = createUtil("test-key-1234567890123456");
        assertEquals("", util.encrypt(""));
    }

    @Test
    void decryptNonEncryptedReturnsOriginal() {
        CryptoUtil util = createUtil("test-key-1234567890123456");
        assertEquals("plain text", util.decrypt("plain text"));
    }

    @Test
    void decryptWithWrongKeyThrows() {
        CryptoUtil util1 = createUtil("key-one-for-encryption-test-1234");
        String encrypted = util1.encrypt("secret");

        CryptoUtil util2 = createUtil("key-two-different-from-first-1234");
        assertThrows(RuntimeException.class, () -> util2.decrypt(encrypted));
    }

    @Test
    void decryptCorruptedDataThrows() {
        CryptoUtil util = createUtil("test-key-1234567890123456");
        assertThrows(RuntimeException.class, () -> util.decrypt("ENC:not-valid-base64!!!"));
    }

    @Test
    void maskApiKeyShortReturnsStars() {
        assertEquals("****", CryptoUtil.maskApiKey(null));
        assertEquals("****", CryptoUtil.maskApiKey("short"));
    }

    @Test
    void maskApiKeyLongMasksMiddle() {
        String masked = CryptoUtil.maskApiKey("sk-1234567890abcdef");
        assertTrue(masked.startsWith("sk-1"));
        assertTrue(masked.endsWith("cdef"));
        assertTrue(masked.contains("****"));
    }

    private CryptoUtil createUtil(String key) {
        CryptoUtil util = new CryptoUtil(mock(Environment.class));
        try {
            java.lang.reflect.Field field = CryptoUtil.class.getDeclaredField("encryptionKey");
            field.setAccessible(true);
            field.set(util, key);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return util;
    }
}
