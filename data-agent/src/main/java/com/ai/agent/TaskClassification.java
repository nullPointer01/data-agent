package com.ai.agent;

/**
 * Agent 执行前的任务复杂度分类结果。
 *
 * @param complexity 粗粒度复杂度
 * @param fastPathAllowed 是否允许直接回答
 * @param reason 面向日志和测试的分类原因
 * @author data-agent
 */
public record TaskClassification(TaskComplexity complexity, boolean fastPathAllowed, String reason) {

    /**
     * 创建直接回答分类。
     *
     * @param reason 分类原因
     * @return 简单分类
     */
    public static TaskClassification simple(String reason) {
        return new TaskClassification(TaskComplexity.SIMPLE, true, reason);
    }

    /**
     * 创建工具辅助分类。
     *
     * @param reason 分类原因
     * @return 工具辅助分类
     */
    public static TaskClassification toolAssisted(String reason) {
        return new TaskClassification(TaskComplexity.TOOL_ASSISTED, false, reason);
    }

    /**
     * 创建复杂推理分类。
     *
     * @param reason 分类原因
     * @return 复杂分类
     */
    public static TaskClassification complex(String reason) {
        return new TaskClassification(TaskComplexity.COMPLEX, false, reason);
    }
}
