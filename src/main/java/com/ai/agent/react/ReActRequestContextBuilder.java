package com.ai.agent.react;

import com.ai.memory.MemoryContextPromptFormatter;
import com.ai.memory.MemoryManager;
import com.ai.memory.dto.MemoryContext;
import com.ai.model.AnalysisRequest;
import com.ai.rag.RagRetrievalService;
import com.ai.rag.dto.RagContextResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * 构建 ReAct 执行的模型查询上下文。
 *
 * @author data-agent
 */
@Component
public class ReActRequestContextBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReActRequestContextBuilder.class);
    private static final String FILE_CONTEXT_HINT = "\n\n[已加载上下文]\n用户已上传文件数据，请使用 analyzeFileData 或 getFileContent 工具获取数据";
    private final RagRetrievalService ragRetrievalService;
    private final MemoryManager memoryManager;
    private final MemoryContextPromptFormatter memoryFormatter;

    @Autowired
    public ReActRequestContextBuilder(RagRetrievalService ragRetrievalService,
            @Nullable MemoryManager memoryManager) {
        this.ragRetrievalService = ragRetrievalService;
        this.memoryManager = memoryManager;
        this.memoryFormatter = new MemoryContextPromptFormatter();
    }

    /**
     * 从用户请求、上传文件和 RAG 上下文构建模型查询。
     *
     * @param request 分析请求
     * @param fileContent 文件内容
     * @return ReAct 请求上下文
     */
    public ReActRequestContext build(AnalysisRequest request, String fileContent) {
        String userQuery = request.getQuestion();
        if (fileContent != null && !fileContent.isEmpty()) {
            userQuery += FILE_CONTEXT_HINT;
        }
        RagContextResponse ragContext = retrieveRagContext(request.getQuestion());
        userQuery = enrichQueryWithRagContext(userQuery, ragContext);
        userQuery = enrichQueryWithMemory(userQuery, request.getSessionId(), request.getQuestion());
        return new ReActRequestContext(userQuery, ragContext);
    }

    private RagContextResponse retrieveRagContext(String question) {
        try {
            return ragRetrievalService.retrieve(question);
        } catch (Exception e) {
            LOGGER.warn("RAG 检索失败，回退到无上下文模式: {}", e.getMessage());
            return RagContextResponse.unavailable();
        }
    }

    private String enrichQueryWithRagContext(String userQuery, RagContextResponse ragContext) {
        if (ragContext == null || !ragContext.hasContext()) {
            return userQuery;
        }
        return """
                [检索上下文]
                以下内容来自个人知识库的向量与全文混合召回（引用编号），请优先使用这些资料回答。若资料与问题无关或不足，请明确说明缺口。
                %s

                [用户问题]
                %s
                """.formatted(ragContext.getContext(), userQuery);
    }

    private String enrichQueryWithMemory(String userQuery, String sessionId, String question) {
        if (memoryManager == null || sessionId == null) {
            return userQuery;
        }
        try {
            MemoryContext memoryContext = memoryManager.buildContext(sessionId, question);
            if (memoryContext != null && memoryFormatter.hasMemory(memoryContext)) {
                return userQuery + "\n\n" + memoryFormatter.toSection(memoryContext);
            }
        } catch (Exception e) {
            LOGGER.warn("记忆上下文构建失败: {}", e.getMessage());
        }
        return userQuery;
    }
}
