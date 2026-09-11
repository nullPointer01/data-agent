package com.ai.rag;

import com.ai.rag.dto.RagContextResponse;
import com.ai.security.SecurityContextHelper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Agent 推理前的 RAG 检索服务。
 *
 * @author data-agent
 */
@Service
public class RagRetrievalService {

    private static final int RAG_SKIP_MAX_LENGTH = 12;
    private static final List<String> RAG_SKIP_GREETINGS = List.of(
            "你好", "您好", "hello", "hi", "在吗", "你是谁", "谢谢", "感谢", "再见", "介绍一下你自己");

    private final SecurityContextHelper securityContextHelper;

    private final EnhancedRagPipeline enhancedRagPipeline;

    public RagRetrievalService(SecurityContextHelper securityContextHelper, EnhancedRagPipeline enhancedRagPipeline) {
        this.securityContextHelper = securityContextHelper;
        this.enhancedRagPipeline = enhancedRagPipeline;
    }

    public RagContextResponse retrieve(String query) {
        if (shouldSkip(query)) {
            return RagContextResponse.empty();
        }
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();
        if (tenantId == null || tenantId.isBlank() || userId == null || userId.isBlank()) {
            return RagContextResponse.empty();
        }
        return enhancedRagPipeline.execute(query, tenantId, userId);
    }

    private boolean shouldSkip(String query) {
        if (query == null) {
            return true;
        }
        String normalized = query.strip().toLowerCase(Locale.ROOT);
        return normalized.isEmpty()
                || (normalized.length() <= RAG_SKIP_MAX_LENGTH
                && RAG_SKIP_GREETINGS.stream().anyMatch(normalized::contains));
    }
}
