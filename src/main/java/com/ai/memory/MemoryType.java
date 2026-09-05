package com.ai.memory;

/**
 * 记忆条目的语义类型。
 *
 * @author data-agent
 */
public enum MemoryType {

    /**
     * 原始或摘要对话记忆。
     */
    CONVERSATION,

    /**
     * 对话摘要记忆。
     */
    SUMMARY,

    /**
     * 用户意图记忆。
     */
    INTENT,

    /**
     * 实体记忆，如人物、公司、产品或指标。
     */
    ENTITY,

    /**
     * 实体之间的关系记忆。
     */
    RELATION,

    /**
     * 用户偏好记忆。
     */
    PREFERENCE,

    /**
     * 行为模式记忆。
     */
    PATTERN,

    /**
     * 持久化知识记忆。
     */
    KNOWLEDGE,

    /**
     * 重要结论记忆。
     */
    CONCLUSION
}
