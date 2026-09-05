package com.ai.agent.dto;

/**
 * Agent 增强推理验收项状态。
 *
 * @param key 验收项编码
 * @param name 验收项名称
 * @param status 验收状态，取值为 PASSED、PENDING、FAILED
 * @param passed 是否已通过
 * @param detail 验收说明
 * @author data-agent
 */
public record AgentReasoningAcceptanceCheck(String key,
        String name,
        String status,
        boolean passed,
        String detail) {
}
