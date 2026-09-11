package com.ai.memory;

/**
 * 从一轮对话中提取出的结构化语义记忆候选。
 *
 * @param type 语义记忆类型
 * @param semanticKey 用于覆盖和去重的稳定语义键
 * @param content 归一化后的记忆内容
 * @param confidence 提取置信度，范围 [0, 1]
 * @param evidence 用户原话中的证据片段
 * @param explicit 是否由用户明确要求记住
 * @author data-agent
 */
public record SemanticMemoryCandidate(
        MemoryType type,
        String semanticKey,
        String content,
        double confidence,
        String evidence,
        boolean explicit) {
}
