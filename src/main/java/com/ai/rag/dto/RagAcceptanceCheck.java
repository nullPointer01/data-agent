package com.ai.rag.dto;

/**
 * RAG 验收项状态。
 *
 * @param key 验收项编码
 * @param name 验收项名称
 * @param passed 是否通过
 * @param detail 验收说明
 * @author data-agent
 */
public record RagAcceptanceCheck(String key, String name, boolean passed, String detail) {
}
