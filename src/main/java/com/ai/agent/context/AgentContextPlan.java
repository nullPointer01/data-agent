package com.ai.agent.context;

import java.util.List;
import java.util.Set;

/**
 * 一次模型调用在执行裁剪前生成的上下文预算计划。
 *
 * <p>计划只保存消息位置、类型和 Token 估算，不保存消息原文。</p>
 *
 * @param messages 逐消息预算元数据
 * @param estimatedMessageTokens 消息输入 Token 估算
 * @param estimatedToolTokens 工具定义 Token 估算
 * @param modelWindowTokens 当前模型窗口
 * @param runRemainingTokens 当前 Run 剩余 Token
 * @param inputTokenLimit 本次调用允许使用的输入 Token
 * @param reservedOutputTokens 为模型输出预留的 Token
 * @param safetyMarginTokens Token 估算安全边界
 * @param compactionEnabled 是否允许执行上下文压缩
 * @author data-agent
 */
public record AgentContextPlan(
        List<PlannedMessage> messages,
        long estimatedMessageTokens,
        long estimatedToolTokens,
        long modelWindowTokens,
        long runRemainingTokens,
        long inputTokenLimit,
        long reservedOutputTokens,
        long safetyMarginTokens,
        boolean compactionEnabled) {

    public AgentContextPlan {
        messages = messages == null ? List.of() : List.copyOf(messages);
        requireNonNegative(estimatedMessageTokens, "estimatedMessageTokens");
        requireNonNegative(estimatedToolTokens, "estimatedToolTokens");
        requirePositive(modelWindowTokens, "modelWindowTokens");
        requireNonNegative(runRemainingTokens, "runRemainingTokens");
        requireNonNegative(inputTokenLimit, "inputTokenLimit");
        requireNonNegative(reservedOutputTokens, "reservedOutputTokens");
        requireNonNegative(safetyMarginTokens, "safetyMarginTokens");
    }

    /**
     * 返回消息与工具定义合计的输入 Token 估算。
     *
     * @return 输入 Token 估算
     */
    public long estimatedInputTokens() {
        return estimatedMessageTokens + estimatedToolTokens;
    }

    /**
     * 返回所有不可丢弃消息的 Token 估算。
     *
     * @return protected 消息 Token 估算
     */
    public long protectedMessageTokens() {
        return messages.stream()
                .filter(PlannedMessage::protectedMessage)
                .mapToLong(PlannedMessage::estimatedTokens)
                .sum();
    }

    /**
     * 单条消息的安全预算描述。
     *
     * @param index 原消息序号
     * @param kind 消息类型
     * @param estimatedTokens Token 估算
     * @param protections 不可丢弃原因；空集合表示普通候选消息
     */
    public record PlannedMessage(
            int index,
            MessageKind kind,
            long estimatedTokens,
            Set<ProtectionReason> protections) {

        public PlannedMessage {
            if (index < 0) {
                throw new IllegalArgumentException("消息序号不能为负数");
            }
            if (kind == null) {
                throw new IllegalArgumentException("消息类型不能为空");
            }
            requireNonNegative(estimatedTokens, "estimatedTokens");
            protections = protections == null ? Set.of() : Set.copyOf(protections);
        }

        public boolean protectedMessage() {
            return !protections.isEmpty();
        }
    }

    /** 模型消息类型，不依赖需要序列化的消息正文。 */
    public enum MessageKind {
        SYSTEM,
        USER,
        ASSISTANT,
        TOOL_RESULT,
        OTHER
    }

    /** 消息被保护、不可静默删除的原因。 */
    public enum ProtectionReason {
        SYSTEM_INSTRUCTION,
        TASK_CONTRACT,
        CURRENT_TASK,
        UNRESOLVED_TOOL_PROTOCOL,
        ACTIVE_APPROVAL,
        RECENT_EVIDENCE
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " 必须为正数");
        }
    }

    private static void requireNonNegative(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " 不能为负数");
        }
    }
}
