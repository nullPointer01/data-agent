package com.ai.rag;

import com.ai.rag.dto.RagContextResponse;
import com.ai.security.SecurityContextHelper;
import org.springframework.stereotype.Service;

/**
 * Agent 推理前的 RAG 检索服务。
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

    public RagContextResponse retrieve(String query) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            return RagContextResponse.empty();
        }
        return enhancedRagPipeline.execute(query, tenantId);
    }
}
