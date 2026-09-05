package com.ai.agent;

import java.util.Locale;

/**
 * 应用级 Agent 运行类型。
 *
 * @author data-agent
 */
public enum AgentType {

    /**
     * 使用工具调用和增强推理循环的 ReAct Agent。
     */
    REACT("REACT", "ReAct 工具 Agent", "tool_reasoning", "适合复杂推理、工具调用、RAG 检索和兜底执行。"),

    /**
     * 绑定技能配置的技能 Agent。
     */
    SKILL("SKILL", "技能 Agent", "skill_execution", "适合固定业务流程、模板生成和可复用技能执行。"),

    /**
     * 绑定数据源配置的数据 Agent。
     */
    DATA("DATA", "数据源 Agent", "data_analysis", "适合数据预览、指标分析、SQL 查询和业务数据解释。"),

    /**
     * 绑定知识库配置的知识检索 Agent。
     */
    KNOWLEDGE("KNOWLEDGE", "知识检索 Agent", "knowledge_retrieval", "适合知识库检索、文档引用和资料整理输出。"),

    /**
     * 生成图表配置和可视化建议的图表 Agent。
     */
    CHART("CHART", "图表 Agent", "chart_generation", "适合根据分析结果生成 ECharts 图表配置和可视化建议。"),

    /**
     * 生成报告、总结和方案文档的报告 Agent。
     */
    REPORT("REPORT", "报告 Agent", "report_generation", "适合生成结构化报告、复盘总结、方案和邮件草稿。"),

    /**
     * 使用系统提示词直接对话的 Chat Agent。
     */
    CHAT("CHAT", "对话 Agent", "direct_chat", "适合轻量问答、角色化助手和无需工具的直接回复。");

    private final String code;
    private final String displayName;
    private final String capability;
    private final String description;

    AgentType(String code, String displayName, String capability, String description) {
        this.code = code;
        this.displayName = displayName;
        this.capability = capability;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCapability() {
        return capability;
    }

    public String getDescription() {
        return description;
    }

    public static AgentType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return REACT;
        }
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        for (AgentType type : values()) {
            if (type.code.equals(normalizedCode)) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的 Agent 类型: " + code);
    }
}
