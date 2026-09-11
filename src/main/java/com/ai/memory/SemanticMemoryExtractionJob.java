package com.ai.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 在回答完成后异步提取隐式语义记忆，避免阻塞用户接收最终结果。
 *
 * @author data-agent
 */
@Service
public class SemanticMemoryExtractionJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(SemanticMemoryExtractionJob.class);

    private final SemanticMemoryExtractor extractor;
    private final SemanticMemoryService semanticMemoryService;

    public SemanticMemoryExtractionJob(SemanticMemoryExtractor extractor,
            SemanticMemoryService semanticMemoryService) {
        this.extractor = extractor;
        this.semanticMemoryService = semanticMemoryService;
    }

    /**
     * 提取并持久化普通对话中的高置信度语义记忆。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     * @param userMessage 用户消息
     * @param assistantReply 助手回复
     * @param modelId 本轮模型编号
     */
    @Async("memoryExtractionExecutor")
    public void extract(String tenantId, String userId, String sessionId,
            String userMessage, String assistantReply, String modelId) {
        try {
            List<SemanticMemoryCandidate> candidates = extractor.extractImplicit(
                    userMessage, assistantReply, modelId);
            int affected = semanticMemoryService.upsertAll(tenantId, userId, sessionId, candidates);
            if (affected > 0) {
                LOGGER.info("已沉淀隐式语义记忆: userId={}, sessionId={}, count={}",
                        userId, sessionId, affected);
            }
        } catch (Exception exception) {
            LOGGER.warn("隐式语义记忆提取失败: userId={}, sessionId={}", userId, sessionId, exception);
        }
    }
}
