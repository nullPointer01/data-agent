package com.ai.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Agent 反馈洞察规则配置。
 *
 * @param issueRules 问题分类规则
 * @param healthyRecommendation 无明显问题时的全局建议
 * @param highNegativeRecommendation 有负向反馈时的全局建议
 * @param otherRecommendation 未命中分类规则时的建议
 * @param analyzeSampleLimit 洞察分析使用的最近负向反馈样本数
 * @param issueSampleLimit 每类问题保留的样本数
 * @author data-agent
 */
@ConfigurationProperties(prefix = "app.agent-feedback.insight")
public record AgentFeedbackInsightProperties(List<IssueRuleProperties> issueRules,
        String healthyRecommendation,
        String highNegativeRecommendation,
        String otherRecommendation,
        Integer analyzeSampleLimit,
        Integer issueSampleLimit) {

    private static final int DEFAULT_ANALYZE_SAMPLE_LIMIT = 100;
    private static final int DEFAULT_ISSUE_SAMPLE_LIMIT = 3;

    public AgentFeedbackInsightProperties {
        issueRules = issueRules == null || issueRules.isEmpty() ? defaultRules() : issueRules;
        healthyRecommendation = defaultIfBlank(healthyRecommendation, "当前负向反馈样本较少，先持续观察趋势，无需立即调整主流程。");
        highNegativeRecommendation = defaultIfBlank(highNegativeRecommendation, "负向反馈较多，建议优先复盘最近失败样本的执行轨迹，定位高频工具或检索问题。");
        otherRecommendation = defaultIfBlank(otherRecommendation, "人工抽检未命中规则的样本，补充新的分类规则或优化反馈备注采集。");
        analyzeSampleLimit = analyzeSampleLimit == null || analyzeSampleLimit <= 0 ? DEFAULT_ANALYZE_SAMPLE_LIMIT
                : analyzeSampleLimit;
        issueSampleLimit = issueSampleLimit == null || issueSampleLimit <= 0 ? DEFAULT_ISSUE_SAMPLE_LIMIT
                : issueSampleLimit;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static List<IssueRuleProperties> defaultRules() {
        return List.of(
                new IssueRuleProperties("ACCURACY", "答案准确性", "优先复盘负向样本的事实依据和计算过程，强化答案校验与引用约束。",
                        List.of("不准确", "错误", "错了", "算错", "幻觉", "不对", "胡说")),
                new IssueRuleProperties("RAG_GAP", "知识或引用缺口", "检查知识库覆盖范围、分块质量和检索阈值，必要时补充文档或下调最小相似度。",
                        List.of("没找到", "找不到", "知识库", "资料", "引用", "来源", "文档", "上下文")),
                new IssueRuleProperties("TOOL_FAILURE", "工具或数据源失败", "优先查看执行追踪中的工具调用结果，补齐 SQL、图表和数据源异常的恢复策略。",
                        List.of("报错", "失败", "异常", "sql", "数据库", "工具", "图表失败", "图表异常", "计算失败")),
                new IssueRuleProperties("COMPLETENESS", "回答完整性不足", "优化提示词中的输出结构要求，要求 Agent 明确结论、依据、限制和下一步建议。",
                        List.of("不完整", "太少", "漏了", "没有分析", "没说清", "不详细", "缺少")),
                new IssueRuleProperties("EXPERIENCE", "交互体验问题", "检查流式输出、格式化和响应耗时，减少冗余内容并保持答案结构稳定。",
                        List.of("太慢", "卡", "格式", "看不懂", "啰嗦", "重复", "体验")));
    }

    /**
     * 单条反馈问题分类规则。
     *
     * @param type 问题类型
     * @param name 问题名称
     * @param recommendation 优化建议
     * @param keywords 命中关键词
     */
    public record IssueRuleProperties(String type, String name, String recommendation, List<String> keywords) {

        public IssueRuleProperties {
            type = defaultIfBlank(type, "UNKNOWN");
            name = defaultIfBlank(name, type);
            recommendation = defaultIfBlank(recommendation, "复盘该类反馈样本，补充更明确的优化动作。");
            keywords = keywords == null ? List.of() : keywords;
        }

        public boolean matches(String text) {
            return keywords.stream().anyMatch(text::contains);
        }
    }
}
