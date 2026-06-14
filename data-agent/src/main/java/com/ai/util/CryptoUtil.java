package com.ai.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 持久化密钥的 AES-GCM 加密助手。
 *
 * @author data-agent
 */
@Component
public class CryptoUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(CryptoUtil.class);

    @Value("${app.encryption.key:}")
    private String encryptionKey;

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String ENCRYPTED_PREFIX = "ENC:";
    private static final String MASKED_SECRET = "****";
    private static final int API_KEY_VISIBLE_PREFIX_LENGTH = 4;
    private static final int API_KEY_VISIBLE_SUFFIX_LENGTH = 4;
    private static final int API_KEY_MASK_THRESHOLD = 8;
    private static final int AES_KEY_LENGTH = 32;

    private final Environment environment;

    public CryptoUtil(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        if (isProductionProfile() && (encryptionKey == null || encryptionKey.isBlank())) {
            throw new IllegalStateException(
                    "app.encryption.key 必须在生产环境中配置，不允许使用默认密钥");
        }
        if (encryptionKey == null || encryptionKey.isBlank()) {
            LOGGER.warn("app.encryption.key 未配置，使用开发环境临时密钥。"
                    + "请为生产环境设置 APP_ENCRYPTION_KEY 环境变量。");
        }
        EncryptedStringConverter.setCryptoUtil(this);
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        try {
            byte[] key = deriveKey();
            SecretKeySpec keySpec = new SecretKeySpec(key, ALGORITHM);
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);
            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }
        if (!encryptedText.startsWith(ENCRYPTED_PREFIX)) {
            return encryptedText;
        }
        try {
            String base64 = encryptedText.substring(ENCRYPTED_PREFIX.length());
            byte[] key = deriveKey();
            SecretKeySpec keySpec = new SecretKeySpec(key, ALGORITHM);
            byte[] decoded = Base64.getDecoder().decode(base64);
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("解密失败，请检查 app.encryption.key 配置是否正确", e);
        }
    }

    private byte[] deriveKey() {
        String key = encryptionKey != null && !encryptionKey.isBlank()
                ? encryptionKey
                : "data-agent-dev-only-key-not-for-prod";
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return sha256.digest(key.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] result = new byte[AES_KEY_LENGTH];
            System.arraycopy(bytes, 0, result, 0, Math.min(bytes.length, AES_KEY_LENGTH));
            return result;
        }
    }

    private boolean isProductionProfile() {
        return environment.acceptsProfiles(Profiles.of("prod", "production"));
    }

    public static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= API_KEY_MASK_THRESHOLD) {
            return MASKED_SECRET;
        }
        return apiKey.substring(0, API_KEY_VISIBLE_PREFIX_LENGTH)
                + MASKED_SECRET
                + apiKey.substring(apiKey.length() - API_KEY_VISIBLE_SUFFIX_LENGTH);
    }
}
