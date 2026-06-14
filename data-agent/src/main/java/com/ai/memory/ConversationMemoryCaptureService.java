package com.ai.memory;

import com.ai.memory.dto.MemoryCaptureRequest;
import com.ai.memory.dto.MemoryCompressionResult;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 从已完成的用户-助手对话中捕获可复用的记忆。
 *
 * @author data-agent
 */
@Service
public class ConversationMemoryCaptureService {

    private static final int SHORT_TERM_IMPORTANCE = 3;
    private static final int LONG_TERM_IMPORTANCE = 5;
    private static final String SOURCE_CONVERSATION_CAPTURE = "conversation_capture";

    private final MemoryManager memoryManager;
    private final MemoryCompressor memoryCompressor;
    private final MemoryWorthinessEvaluator memoryWorthinessEvaluator;

    public ConversationMemoryCaptureService(MemoryManager memoryManager,
            MemoryCompressor memoryCompressor,
            MemoryWorthinessEvaluator memoryWorthinessEvaluator) {
        this.memoryManager = memoryManager;
        this.memoryCompressor = memoryCompressor;
        this.memoryWorthinessEvaluator = memoryWorthinessEvaluator;
    }

    /**
     * 从一轮已完成的对话中捕获短期摘要和显式长期用户记忆。
     *
     * @param sessionId 对话会话 ID
     * @param userMessage 用户消息
     * @param assistantReply 助手回复
     */
    public void captureCompletedConversation(String sessionId, String userMessage, String assistantReply) {
        if (!memoryWorthinessEvaluator.shouldStoreConversation(userMessage, assistantReply)) {
            return;
        }
        captureShortTermSummary(sessionId, userMessage, assistantReply);
        if (memoryWorthinessEvaluator.hasExplicitMemoryIntent(userMessage)) {
            captureExplicitLongTermMemory(sessionId, userMessage);
        }
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
                compressionResult.keyEntities(),
                compressionResult.topicTags(),
                SHORT_TERM_IMPORTANCE);
        memoryManager.capture(request);
    }

    private void captureExplicitLongTermMemory(String sessionId, String userMessage) {
        MemoryType memoryType = memoryWorthinessEvaluator.classifyExplicitMemory(userMessage);
        MemoryCompressionResult compressionResult = memoryCompressor.compressExplicitMemory(userMessage, memoryType);
        MemoryCaptureRequest request = new MemoryCaptureRequest(
                MemoryTier.LONG_TERM,
                memoryType,
                MemorySource.USER_EXPLICIT,
                sessionId,
                compressionResult.content(),
                compressionResult.compressedContent(),
                Map.of("source", SOURCE_CONVERSATION_CAPTURE, "captureMode", "explicit_user_statement"),
                compressionResult.keyEntities(),
                compressionResult.topicTags(),
                LONG_TERM_IMPORTANCE);
        memoryManager.capture(request);
    }
}
