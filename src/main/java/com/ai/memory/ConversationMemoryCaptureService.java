package com.ai.memory;

import com.ai.memory.dto.MemoryCaptureRequest;
import com.ai.memory.dto.MemoryCompressionResult;
import com.ai.security.SecurityContextHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 从已完成的用户-助手对话中捕获可复用的记忆。
 *
 * @author data-agent
 */
@Service
public class ConversationMemoryCaptureService {

    private static final int SHORT_TERM_IMPORTANCE = 3;
    private static final String SOURCE_CONVERSATION_CAPTURE = "conversation_capture";

    private final MemoryManager memoryManager;
    private final DeterministicMemoryCompressor memoryCompressor;
    private final MemoryExtractionTaskStore tasks;
    private final SecurityContextHelper securityContextHelper;

    public ConversationMemoryCaptureService(MemoryManager memoryManager,
            DeterministicMemoryCompressor memoryCompressor,
            MemoryExtractionTaskStore tasks,
            SecurityContextHelper securityContextHelper) {
        this.memoryManager = memoryManager;
        this.memoryCompressor = memoryCompressor;
        this.tasks = tasks;
        this.securityContextHelper = securityContextHelper;
    }

    /**
     * 保留短期摘要，按请求计划调度长期记忆提取；计划只决定是否调用提取器。
     *
     * @param sessionId 对话会话 ID
     * @param userMessage 用户消息
     * @param assistantReply 助手回复
     * @param modelId 提取使用的模型 ID
     * @param captureMode 语义规划器给出的提取调度，空值保守跳过
     * @param sourceMessageId 同事务保存的用户消息自增 ID
     * @param memoryRevision 请求开始时的用户记忆修订快照
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void captureCompletedConversation(
            String sessionId, String userMessage, String assistantReply, String modelId,
            MemoryCaptureMode captureMode, long sourceMessageId, long memoryRevision) {
        if (!StringUtils.hasText(userMessage) && !StringUtils.hasText(assistantReply)) {
            return;
        }
        captureShortTermSummary(sessionId, userMessage, assistantReply);
        // 普通问答仍保存会话摘要，但不再为每一轮额外调用提取模型。
        if (captureMode == null || captureMode == MemoryCaptureMode.SKIP) {
            return;
        }
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();
        tasks.enqueue(tenantId, userId, sessionId, modelId, sourceMessageId, memoryRevision,
                userMessage, assistantReply);
    }

    private void captureShortTermSummary(String sessionId, String userMessage, String assistantReply) {
        MemoryCompressionResult compressionResult = memoryCompressor.compressConversation(userMessage, assistantReply);
        MemoryCaptureRequest request = new MemoryCaptureRequest(
                MemoryTier.SHORT_TERM,
                MemoryType.SUMMARY,
                MemorySource.SYSTEM_GENERATED,
                sessionId,
                compressionResult.content(),
                compressionResult.compressedContent(),
                Map.of("source", SOURCE_CONVERSATION_CAPTURE, "captureMode", "deterministic_summary"),
                null,
                0D,
                compressionResult.keyEntities(),
                compressionResult.topicTags(),
                SHORT_TERM_IMPORTANCE);
        memoryManager.capture(request);
    }

}
