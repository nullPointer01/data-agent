package com.ai.agent.tool;

import com.ai.security.SecurityContextHelper;
import com.ai.service.VectorMemoryService;
import com.ai.vector.VectorDocumentTypes;
import com.ai.vector.VectorMetadataKeys;
import com.ai.vector.VectorSearchResultFormatter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Agent 使用的知识库和向量记忆工具。
 *
 * @author data-agent
 */
@Service
public class AgentKnowledgeToolService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentKnowledgeToolService.class);
    private static final int MEMORY_SEARCH_TOP_K = 5;
    private static final double KNOWLEDGE_MIN_SCORE = 0.5D;
    private static final int KNOWLEDGE_SEARCH_TOP_K = 5;
    private static final String EMPTY_MEMORY_MESSAGE = "没有找到相关的历史记忆";
    private static final String EMPTY_KNOWLEDGE_MESSAGE = "知识库中没有找到相关内容。建议用户上传相关文档或数据。";

    private final VectorMemoryService vectorMemoryService;

    private final SecurityContextHelper securityContextHelper;

    private final VectorSearchResultFormatter vectorSearchResultFormatter;

    public AgentKnowledgeToolService(VectorMemoryService vectorMemoryService,
            SecurityContextHelper securityContextHelper,
            VectorSearchResultFormatter vectorSearchResultFormatter) {
        this.vectorMemoryService = vectorMemoryService;
        this.securityContextHelper = securityContextHelper;
        this.vectorSearchResultFormatter = vectorSearchResultFormatter;
    }

    public String searchMemory(String query) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();
        if (tenantId == null || tenantId.isBlank() || userId == null || userId.isBlank()) {
            return EMPTY_MEMORY_MESSAGE;
        }
        String result = vectorMemoryService.searchRelevant(query, MEMORY_SEARCH_TOP_K, KNOWLEDGE_MIN_SCORE,
                tenantId, userId, List.of(VectorDocumentTypes.MEMORY));
        if (result == null || result.isEmpty()) {
            return EMPTY_MEMORY_MESSAGE;
        }
        return result;
    }

    public String searchKnowledge(String query) {
        try {
            String tenantId = securityContextHelper.getCurrentTenantId();
            if (tenantId == null || tenantId.isBlank()) {
                return EMPTY_KNOWLEDGE_MESSAGE;
            }
            List<EmbeddingMatch<TextSegment>> knowledgeMatches = vectorMemoryService.searchMatches(
                            query,
                            KNOWLEDGE_SEARCH_TOP_K,
                            KNOWLEDGE_MIN_SCORE,
                            tenantId,
                            List.of(VectorDocumentTypes.KNOWLEDGE, VectorDocumentTypes.FILE))
                    .stream()
                    .filter(this::isKnowledgeOrFile)
                    .toList();

            if (knowledgeMatches.isEmpty()) {
                return EMPTY_KNOWLEDGE_MESSAGE;
            }

            return formatMatches(knowledgeMatches);
        } catch (Exception e) {
            LOGGER.warn("Knowledge search failed: {}", e.getMessage());
            return "知识库搜索失败: " + e.getMessage();
        }
    }

    public String getMemoryStats() {
        int total = vectorMemoryService.getIndexedCount();
        Map<String, Long> byType = vectorMemoryService.getIndexedCountByType();

        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append("向量记忆库状态:\n");
        resultBuilder.append("- 存储后端: Milvus\n");
        resultBuilder.append("- 已索引条目总数: ").append(total).append("\n");
        if (!byType.isEmpty()) {
            resultBuilder.append("- 按类型分布:\n");
            byType.forEach((type, count) ->
                    resultBuilder.append("  - ").append(type).append(": ").append(count).append("条\n"));
        }
        return resultBuilder.toString();
    }

    private boolean isKnowledgeOrFile(EmbeddingMatch<TextSegment> match) {
        String type = match.embedded().metadata().getString(VectorMetadataKeys.TYPE);
        return VectorDocumentTypes.KNOWLEDGE.equals(type) || VectorDocumentTypes.FILE.equals(type);
    }

    private String formatMatches(List<EmbeddingMatch<TextSegment>> matches) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < matches.size(); i++) {
            if (i > 0) {
                builder.append("\n\n");
            }
            builder.append(vectorSearchResultFormatter.format(matches.get(i), i + 1));
        }
        return builder.toString();
    }
}
