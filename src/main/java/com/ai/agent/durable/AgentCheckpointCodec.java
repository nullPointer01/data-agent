package com.ai.agent.durable;

import com.ai.util.CryptoUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Checkpoint JSON 的整体 AES-GCM 加密编解码器。
 *
 * @author data-agent
 */
@Component
public class AgentCheckpointCodec {

    private static final String ENCRYPTED_PREFIX = "ENC:";

    private final ObjectMapper objectMapper;
    private final CryptoUtil cryptoUtil;

    public AgentCheckpointCodec(ObjectMapper objectMapper, CryptoUtil cryptoUtil) {
        this.objectMapper = objectMapper;
        this.cryptoUtil = cryptoUtil;
    }

    public String encode(AgentRunCheckpoint checkpoint) {
        if (checkpoint == null) {
            throw new IllegalArgumentException("Agent Checkpoint 不能为空");
        }
        AgentCheckpointVersion.requireSupported(checkpoint.schemaVersion());
        try {
            String ciphertext = cryptoUtil.encrypt(objectMapper.writeValueAsString(checkpoint));
            if (ciphertext == null || !ciphertext.startsWith(ENCRYPTED_PREFIX)) {
                throw new IllegalStateException("Agent Checkpoint 未被加密");
            }
            return ciphertext;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Agent Checkpoint 序列化失败", e);
        }
    }

    public AgentRunCheckpoint decode(String ciphertext, int persistedSchemaVersion) {
        AgentCheckpointVersion.requireSupported(persistedSchemaVersion);
        if (ciphertext == null || !ciphertext.startsWith(ENCRYPTED_PREFIX)) {
            throw new IllegalArgumentException("Agent Checkpoint 必须是加密载荷");
        }
        try {
            AgentRunCheckpoint checkpoint = objectMapper.readValue(
                    cryptoUtil.decrypt(ciphertext), AgentRunCheckpoint.class);
            AgentCheckpointVersion.requireSupported(checkpoint.schemaVersion());
            if (checkpoint.schemaVersion() != persistedSchemaVersion) {
                throw new IllegalArgumentException("Agent Checkpoint 版本与数据库记录不一致");
            }
            return checkpoint;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Agent Checkpoint 反序列化失败", e);
        }
    }
}
