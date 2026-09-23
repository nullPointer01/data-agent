package com.ai.agent.tool;

import com.ai.memory.MemoryManager;
import com.ai.rag.RagRetrievalService;
import com.ai.rag.dto.RagContextResponse;
import com.ai.service.VectorMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Agent 使用的知识库和向量记忆工具。
 *
 * @author data-agent
 */
@Service
public class AgentKnowledgeToolService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentKnowledgeToolService.class);
    private static final String EMPTY_MEMORY_MESSAGE = "没有找到相关的历史记忆";
    private static final String EMPTY_KNOWLEDGE_MESSAGE = "知识库中没有找到相关内容。建议用户上传相关文档或数据。";

    private final VectorMemoryService vectorMemoryService;

    private final MemoryManager memoryManager;
    private final RagRetrievalService ragRetrievalService;

    public AgentKnowledgeToolService(VectorMemoryService vectorMemoryService,
            MemoryManager memoryManager,
            RagRetrievalService ragRetrievalService) {
        this.vectorMemoryService = vectorMemoryService;
        this.memoryManager = memoryManager;
        this.ragRetrievalService = ragRetrievalService;
    }

    /** 复用预加载的长期记忆入口；检索异常继续交给工具管道处理，不能伪装成未命中。 */
    public String searchMemory(String query) {
        String result = memoryManager.recallSemanticMemory(query);
        if (result == null || result.isBlank()) {
            return EMPTY_MEMORY_MESSAGE;
        }
        return result;
    }

    /** 按需工具和预加载共用混合 RAG，身份过滤、精排及引用均由统一检索入口负责。 */
    public String searchKnowledge(String query) {
        try {
            RagContextResponse result = ragRetrievalService.retrieve(query);
            if (!result.isRetrievalAvailable()) {
                throw new IllegalStateException("知识检索暂不可用");
            }
            if (!result.hasContext()) {
                return EMPTY_KNOWLEDGE_MESSAGE;
            }
            return "以下是本次检索到的知识库证据，请保留 [R1] 形式的引用编号：\n" + result.getContext();
        } catch (Exception e) {
            LOGGER.warn("知识库搜索失败: errorType={}", e.getClass().getSimpleName());
            throw new IllegalStateException("知识库搜索失败", e);
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

}
