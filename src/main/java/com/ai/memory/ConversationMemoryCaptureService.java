package com.ai.memory;

import com.ai.memory.dto.MemoryCaptureRequest;
import com.ai.memory.dto.MemoryCompressionResult;
import com.ai.security.SecurityContextHelper;
import org.springframework.stereotype.Service;

import java.util.List;
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
    private final MemoryCompressor memoryCompressor;
    private final MemoryWorthinessEvaluator memoryWorthinessEvaluator;
    private final SemanticMemoryExtractor semanticMemoryExtractor;
    private final SemanticMemoryService semanticMemoryService;
    private final SemanticMemoryExtractionJob semanticMemoryExtractionJob;
    private final SecurityContextHelper securityContextHelper;

    public ConversationMemoryCaptureService(MemoryManager memoryManager,
            MemoryCompressor memoryCompressor,
            MemoryWorthinessEvaluator memoryWorthinessEvaluator,
            SemanticMemoryExtractor semanticMemoryExtractor,
            SemanticMemoryService semanticMemoryService,
            SemanticMemoryExtractionJob semanticMemoryExtractionJob,
            SecurityContextHelper securityContextHelper) {
        this.memoryManager = memoryManager;
        this.memoryCompressor = memoryCompressor;
        this.memoryWorthinessEvaluator = memoryWorthinessEvaluator;
        this.semanticMemoryExtractor = semanticMemoryExtractor;
        this.semanticMemoryService = semanticMemoryService;
        this.semanticMemoryExtractionJob = semanticMemoryExtractionJob;
        this.securityContextHelper = securityContextHelper;
    }

    /**
     * 从一轮已完成的对话中捕获短期摘要和显式长期用户记忆。
     *
     * @param sessionId 对话会话 ID
     * @param userMessage 用户消息
     * @param assistantReply 助手回复
     */
    public void captureCompletedConversation(
            String sessionId, String userMessage, String assistantReply, String modelId) {
        if (!memoryWorthinessEvaluator.shouldStoreConversation(userMessage, assistantReply)) {
            return;
        }
        captureShortTermSummary(sessionId, userMessage, assistantReply);
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();
        List<SemanticMemoryCandidate> explicitCandidates = semanticMemoryExtractor.extractExplicit(userMessage);
        if (!explicitCandidates.isEmpty()) {
            semanticMemoryService.upsertAll(tenantId, userId, sessionId, explicitCandidates);
            return;
        }
        semanticMemoryExtractionJob.extract(
                tenantId, userId, sessionId, userMessage, assistantReply, modelId);
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
