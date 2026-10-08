package com.ai.rag;

import com.ai.rag.dto.RagContextResponse;
import com.ai.security.SecurityContextHelper;
import org.springframework.stereotype.Service;
import dev.langchain4j.data.message.ChatMessage;
import java.util.List;

/**
 * Agent 预加载与按需工具共用的个人知识 RAG 入口。
 *
 * @author data-agent
 */
@Service
public class RagRetrievalService {

    private final SecurityContextHelper securityContextHelper;

    private final EnhancedRagPipeline enhancedRagPipeline;

    public RagRetrievalService(SecurityContextHelper securityContextHelper, EnhancedRagPipeline enhancedRagPipeline) {
        this.securityContextHelper = securityContextHelper;
        this.enhancedRagPipeline = enhancedRagPipeline;
    }

    /** 根据当前认证身份检索；是否需要检索由请求计划/工具决策负责，不在底层重新猜意图。 */
    public RagContextResponse retrieve(String query) {
        return retrieve(query, List.of());
    }

    /** 原始追问可由已鉴权调用方提供历史；模型不能提供租户或用户身份。 */
    public RagContextResponse retrieve(String query, List<ChatMessage> history) {
        if (query == null || query.isBlank()) {
            return RagContextResponse.empty();
        }
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();
        if (tenantId == null || tenantId.isBlank() || userId == null || userId.isBlank()) {
            return RagContextResponse.empty();
        }
        return enhancedRagPipeline.execute(query.strip(), tenantId, userId, history);
    }
}
