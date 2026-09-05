package com.ai.agent.durable;

import com.ai.agent.runtime.AgentExecutionMode;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 可跨 JVM 恢复的版本化 Agent Run 现场。
 *
 * @param schemaVersion Checkpoint 合同版本
 * @param mode 执行模式
 * @param strategyId 执行策略标识
 * @param modelId 模型配置ID
 * @param executorId 工具执行者标识
 * @param executionPosition 策略内恢复位置
 * @param iterationIndex 当前迭代位置
 * @param recoveryCount 已发生的恢复次数
 * @param partialAnswer 暂停前已生成的可见答案
 * @param messages 可移植消息序列
 * @param pendingTool 待审批工具动作
 * @param allowedToolsAtRequest 发起执行时的工具白名单，仅用于恢复时收窄，不能替代重新授权
 * @param budget 冻结预算
 * @param capturedAt 捕获时间
 * @author data-agent
 */
public record AgentRunCheckpoint(
        int schemaVersion,
        AgentExecutionMode mode,
        String strategyId,
        String modelId,
        String executorId,
        String executionPosition,
        int iterationIndex,
        int recoveryCount,
        String partialAnswer,
        List<AgentCheckpointMessage> messages,
        AgentPendingToolCheckpoint pendingTool,
        Set<String> allowedToolsAtRequest,
        AgentRunBudgetCheckpoint budget,
        Instant capturedAt) {

    public AgentRunCheckpoint {
        AgentCheckpointVersion.requireSupported(schemaVersion);
        if (mode == null || budget == null || pendingTool == null || capturedAt == null) {
            throw new IllegalArgumentException("Checkpoint 核心字段不能为空");
        }
        if (strategyId == null || strategyId.isBlank()
                || executorId == null || executorId.isBlank()
                || executionPosition == null || executionPosition.isBlank()) {
            throw new IllegalArgumentException("Checkpoint 执行位置不能为空");
        }
        if (iterationIndex < 0 || recoveryCount < 0) {
            throw new IllegalArgumentException("Checkpoint 计数不能为负数");
        }
        partialAnswer = partialAnswer == null ? "" : partialAnswer;
        modelId = modelId == null ? "" : modelId;
        messages = messages == null ? List.of() : List.copyOf(messages);
        allowedToolsAtRequest = allowedToolsAtRequest == null ? Set.of() : Set.copyOf(allowedToolsAtRequest);
    }
}
