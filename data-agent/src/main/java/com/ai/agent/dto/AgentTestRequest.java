package com.ai.agent.dto;

/**
 * Agent 试运行请求。
 *
 * @param question 测试问题
 * @param fileContent 可选上下文内容
 * @param modelId 可选临时模型编号
 * @param sessionId 可选会话编号
 * @author data-agent
 */
public record AgentTestRequest(String question,
        String fileContent,
        String modelId,
        String sessionId) {
}
