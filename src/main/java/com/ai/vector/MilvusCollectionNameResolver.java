package com.ai.vector;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 根据 Embedding 身份生成满足 Milvus 约束的物理 Collection 名。
 *
 * @author data-agent
 */
@Component
public class MilvusCollectionNameResolver {

    private static final int MAX_COLLECTION_NAME_LENGTH = 255;
    private static final int LONG_NAME_HASH_LENGTH = 16;

    /**
     * 解析物理 Collection 名，不同模型或索引版本不会共享同一个 Collection。
     *
     * @param baseName 配置中的逻辑基础名称
     * @param profile 当前 Embedding 身份
     * @return 可直接用于 Milvus 的物理名称
     */
    public String resolve(String baseName, EmbeddingProfile profile) {
        String resolved = sanitize(baseName, "collection")
                + "__" + sanitize(profile.provider(), "provider")
                + "__" + sanitize(profile.modelId(), "model")
                + "__d" + profile.dimension()
                + "__" + sanitize(profile.metric(), "metric")
                + "__" + sanitize(profile.indexVersion(), "version");
        if (resolved.length() <= MAX_COLLECTION_NAME_LENGTH) {
            return resolved;
        }
        String hash = sha256(resolved).substring(0, LONG_NAME_HASH_LENGTH);
        int prefixLength = MAX_COLLECTION_NAME_LENGTH - hash.length() - 2;
        return resolved.substring(0, prefixLength) + "__" + hash;
    }

    private String sanitize(String value, String fallbackPrefix) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Milvus Collection 名称组件不能为空: " + fallbackPrefix);
        }
        String raw = value.trim().toLowerCase(Locale.ROOT);
        String normalized = raw.replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isEmpty()) {
            normalized = fallbackPrefix + "_" + sha256(raw).substring(0, 8);
        } else if (!normalized.equals(raw)) {
            normalized = normalized + "_" + sha256(raw).substring(0, 8);
        }
        if (Character.isDigit(normalized.charAt(0))) {
            normalized = fallbackPrefix + "_" + normalized;
        }
        return normalized;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }
}
