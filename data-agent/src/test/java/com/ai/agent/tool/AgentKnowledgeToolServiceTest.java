package com.ai.agent.tool;

import com.ai.security.SecurityContextHelper;
import com.ai.service.VectorMemoryService;
import com.ai.vector.VectorDocumentTypes;
import com.ai.vector.VectorChunk;
import com.ai.vector.VectorMetadataFactory;
import com.ai.vector.VectorSearchResultFormatter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentKnowledgeToolServiceTest {

    @Test
    void searchKnowledgeFormatsCitationFriendlyResults() {
        VectorMemoryService vectorMemoryService = mock(VectorMemoryService.class);
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        AgentKnowledgeToolService service = new AgentKnowledgeToolService(
                vectorMemoryService, securityContextHelper, new VectorSearchResultFormatter());
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-1");
        when(vectorMemoryService.searchMatches(eq("客户流失"), eq(5), eq(0.5D), eq("tenant-1"),
                eq(List.of(VectorDocumentTypes.KNOWLEDGE, VectorDocumentTypes.FILE))))
                .thenReturn(List.of(match()));

        String result = service.searchKnowledge("客户流失");

        assertTrue(result.contains("[R1"));
        assertTrue(result.contains("knowledge-1"));
        assertTrue(result.contains("年度报告 > 客户分析"));
        assertTrue(result.contains("字符 100-130"));
        assertTrue(result.contains("客户流失来自售后响应慢"));
    }

    @Test
    void searchMemoryUsesTenantAndUserScope() {
        VectorMemoryService vectorMemoryService = mock(VectorMemoryService.class);
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        AgentKnowledgeToolService service = new AgentKnowledgeToolService(
                vectorMemoryService, securityContextHelper, new VectorSearchResultFormatter());
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-1");
        when(securityContextHelper.getCurrentUserId()).thenReturn("user-1");
        when(vectorMemoryService.searchRelevant(eq("偏好"), eq(5), eq(0.5D), eq("tenant-1"), eq("user-1"),
                eq(List.of(VectorDocumentTypes.MEMORY)))).thenReturn("记忆: 偏好表格");

        String result = service.searchMemory("偏好");

        assertTrue(result.contains("偏好表格"));
    }

    private EmbeddingMatch<TextSegment> match() {
        TextSegment segment = TextSegment.from("客户流失来自售后响应慢",
                VectorMetadataFactory.create(VectorDocumentTypes.KNOWLEDGE,
                        new VectorChunk("chunk-1", "knowledge-1", "客户流失来自售后响应慢",
                                "年度报告 > 客户分析", 100, 130, false, false, false),
                        "tenant-1", "user-1"));
        return new EmbeddingMatch<>(0.91D, "chunk-1", null, segment);
    }
}
