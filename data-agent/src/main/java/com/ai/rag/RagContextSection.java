package com.ai.rag;

/**
 * 待注入模型的 RAG 上下文片段。
 *
 * @param referenceId 引用编号
 * @param header 来源头信息
 * @param content 片段正文
 * @author data-agent
 */
public record RagContextSection(String referenceId, String header, String content) {
}
