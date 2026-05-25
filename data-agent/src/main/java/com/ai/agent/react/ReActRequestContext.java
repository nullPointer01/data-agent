package com.ai.agent.react;

import com.ai.memory.dto.MemoryContext;
import com.ai.rag.dto.RagContextResponse;

/**
 * ReAct 执行前准备好的请求上下文。
 *
 * @param userQuery 发送给模型的查询
 * @param ragContext 已召回的 RAG 上下文
 * @param memoryContext 已聚合的记忆上下文
 * @author data-agent
 */
public record ReActRequestContext(String userQuery, RagContextResponse ragContext, MemoryContext memoryContext) {

    public ReActRequestContext(String userQuery, RagContextResponse ragContext) {
        this(userQuery, ragContext, MemoryContext.empty());
    }
}
