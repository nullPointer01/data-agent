package com.ai.agent.dto;

import java.util.List;

/**
 * Agent 增强推理健康和验收状态。
 *
 * @param healthy 是否不存在失败项
 * @param accepted 是否所有验收项均已通过
 * @param sampleSize 最近轨迹样本数
 * @param fastPathEnabled 快速路径是否启用
 * @param planningEnabled 任务规划是否启用
 * @param parallelPrecheckEnabled 并行预检是否启用
 * @param reflectionEnabled 反思机制是否启用
 * @param workingMemoryEnabled 工作记忆是否启用
 * @param simpleSampleSize 简单问题样本数
 * @param complexSampleSize 复杂推理样本数
 * @param averageSimpleDurationMs 简单问题平均耗时
 * @param averageComplexDurationMs 复杂问题平均耗时
 * @param retryCandidateCount 错误恢复候选样本数
 * @param retryRecoveredCount 错误恢复后成功样本数
 * @param retryRecoveryRate 错误恢复成功率
 * @param acceptanceChecks 验收项列表
 * @param issues 待处理问题
 * @author data-agent
 */
public record AgentReasoningHealthResponse(boolean healthy,
        boolean accepted,
        int sampleSize,
        boolean fastPathEnabled,
        boolean planningEnabled,
        boolean parallelPrecheckEnabled,
        boolean reflectionEnabled,
        boolean workingMemoryEnabled,
        long simpleSampleSize,
        long complexSampleSize,
        long averageSimpleDurationMs,
        long averageComplexDurationMs,
        long retryCandidateCount,
        long retryRecoveredCount,
        double retryRecoveryRate,
        List<AgentReasoningAcceptanceCheck> acceptanceChecks,
        List<String> issues) {
}
