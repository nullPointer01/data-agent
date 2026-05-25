package com.ai.agent;

import com.ai.mcp.McpModelService;
import com.ai.model.AnalysisRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.ai.agent.orchestrator.OrchestratorProperties;

/**
 * 确定性意图分析器，通过关键词信号匹配和可选 LLM 增强来识别用户请求的高层意图。
 *
 * <p>分析流程：
 * <ol>
 *     <li>扫描用户问题中的关键词信号（如"销售"、"知识库"、"报告"等）</li>
 *     <li>检测上下文信号（如是否有上传文件）</li>
 *     <li>根据信号匹配结果确定意图和置信度</li>
 *     <li>置信度不足且启用 LLM 增强时，调用模型进行二次判断</li>
 * </ol>
 *
 * @author data-agent
 */
@Component
public class IntentAnalyzer {

    private static final Logger LOGGER = LoggerFactory.getLogger(IntentAnalyzer.class);

    /** 数据分析类关键词 */
    private static final String[] DATA_SIGNALS = {"销售", "数据", "指标", "分析", "趋势", "统计", "报表", "SQL", "查询"};
    /** 知识检索类关键词 */
    private static final String[] KNOWLEDGE_SIGNALS = {"知识库", "检索", "文档", "制度", "资料", "手册"};
    /** 报告生成类关键词 */
    private static final String[] REPORT_SIGNALS = {"报告", "总结", "复盘", "方案", "邮件", "草稿"};
    /** 图表生成类关键词 */
    private static final String[] CHART_SIGNALS = {"图表", "可视化", "画图", "柱状图", "折线图", "饼图"};

    /** 高置信度阈值 */
    private static final double HIGH_CONFIDENCE = 0.85D;
    /** 中置信度阈值 */
    private static final double MEDIUM_CONFIDENCE = 0.6D;

    private final McpModelService modelService;
    private final ObjectMapper objectMapper;
    private final OrchestratorProperties properties;

    /**
     * 无参构造器，仅使用关键词匹配，不启用 LLM 增强。
     */
    public IntentAnalyzer() {
        this(null, new ObjectMapper(), new OrchestratorProperties());
    }

    /**
     * 完整构造器，支持可选的 LLM 增强意图分析。
     *
     * @param modelService 模型调用服务，可为空
     * @param objectMapper JSON 序列化工具
     * @param properties 编排配置
     */
    public IntentAnalyzer(McpModelService modelService, ObjectMapper objectMapper,
            OrchestratorProperties properties) {
        this.modelService = modelService;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.properties = properties == null ? new OrchestratorProperties() : properties;
    }

    /**
     * 分析用户请求的意图。
     *
     * @param request 分析请求
     * @param fileContent 关联的文件内容，可为空
     * @param classification 任务复杂度分类
     * @return 意图分析结果
     */
    public IntentAnalysisResult analyze(AnalysisRequest request, String fileContent,
            TaskClassification classification) {
        String question = request.getQuestion() == null ? "" : request.getQuestion();
        String lowerQuestion = question.toLowerCase(Locale.ROOT);
        List<String> matchedSignals = new ArrayList<>();
        AgentIntent intent = null;
        AgentType preferredType = null;
        double confidence = 0.0D;
        StringBuilder reason = new StringBuilder();

        // 1. 检测上传文件信号
        if (StringUtils.hasText(request.getFileId())) {
            matchedSignals.add("uploaded-file");
            intent = AgentIntent.DATA_ANALYSIS;
            preferredType = AgentType.DATA;
            confidence = HIGH_CONFIDENCE;
            reason.append("用户上传了文件，倾向数据分析");
        }

        // 2. 关键词信号扫描
        double dataScore = scanSignals(question, DATA_SIGNALS, matchedSignals);
        double knowledgeScore = scanSignals(question, KNOWLEDGE_SIGNALS, matchedSignals);
        double reportScore = scanSignals(question, REPORT_SIGNALS, matchedSignals);
        double chartScore = scanSignals(question, CHART_SIGNALS, matchedSignals);

        // 3. 选取最高得分信号（REPORT 优先：当存在报告关键词时，即使数据关键词更多也优先视为报告生成）
        if (intent == null) {
            double maxScore = Math.max(Math.max(dataScore, knowledgeScore), Math.max(reportScore, chartScore));
            if (maxScore > 0) {
                if (reportScore > 0) {
                    intent = AgentIntent.REPORT_GENERATION;
                    preferredType = AgentType.REPORT;
                    confidence = Math.min(1.0D, 0.5D + reportScore * 0.15D);
                    reason.append("关键词匹配到报告生成信号");
                } else if (dataScore == maxScore) {
                    intent = AgentIntent.DATA_ANALYSIS;
                    preferredType = AgentType.DATA;
                    confidence = Math.min(1.0D, 0.5D + dataScore * 0.15D);
                    reason.append("关键词匹配到数据分析信号");
                } else if (knowledgeScore == maxScore) {
                    intent = AgentIntent.KNOWLEDGE_RETRIEVAL;
                    preferredType = AgentType.KNOWLEDGE;
                    confidence = Math.min(1.0D, 0.5D + knowledgeScore * 0.15D);
                    reason.append("关键词匹配到知识检索信号");
                } else if (reportScore == maxScore) {
                    intent = AgentIntent.REPORT_GENERATION;
                    preferredType = AgentType.REPORT;
                    confidence = Math.min(1.0D, 0.5D + reportScore * 0.15D);
                    reason.append("关键词匹配到报告生成信号");
                } else {
                    intent = AgentIntent.DATA_ANALYSIS;
                    preferredType = AgentType.CHART;
                    confidence = Math.min(1.0D, 0.5D + chartScore * 0.15D);
                    reason.append("关键词匹配到图表生成信号");
                }
            }
        }

        // 4. 无信号时的默认处理
        if (intent == null) {
            intent = AgentIntent.COMPLEX_REASONING;
            preferredType = AgentType.REACT;
            confidence = 0.3D;
            reason.append("未匹配到明确信号，默认复杂推理");
        }

        // 5. 低置信度时尝试 LLM 增强
        if (confidence < properties.getIntentConfidenceThreshold() && properties.isLlmIntentEnabled()
                && modelService != null) {
            try {
                IntentAnalysisResult llmResult = analyzeWithLlm(question, matchedSignals);
                if (llmResult != null) {
                    return llmResult;
                }
            } catch (Exception e) {
                LOGGER.warn("LLM 意图分析失败，使用关键词分析结果: {}", e.getMessage());
            }
        }

        return new IntentAnalysisResult(intent, preferredType, confidence, matchedSignals, reason.toString());
    }

    /**
     * 扫描问题中的关键词信号并累加匹配数。
     *
     * @param question 用户问题
     * @param signals 信号关键词数组
     * @param matchedSignals 已匹配的信号列表（会追加匹配项）
     * @return 匹配数量
     */
    private double scanSignals(String question, String[] signals, List<String> matchedSignals) {
        double score = 0;
        for (String signal : signals) {
            if (question.contains(signal)) {
                matchedSignals.add(signal);
                score++;
            }
        }
        return score;
    }

    /**
     * 使用 LLM 进行意图增强分析。
     *
     * @param question 用户问题
     * @param existingSignals 已匹配的信号
     * @return LLM 增强后的分析结果，解析失败时返回 null
     */
    private IntentAnalysisResult analyzeWithLlm(String question, List<String> existingSignals) {
        String prompt = buildLlmIntentPrompt(question);
        String response = modelService.callModel(prompt, null);
        if (!StringUtils.hasText(response)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            String intentStr = root.has("intent") ? root.get("intent").asText() : null;
            String typeStr = root.has("preferredType") ? root.get("preferredType").asText() : null;
            double llmConfidence = root.has("confidence") ? root.get("confidence").asDouble() : 0.5D;
            String llmReason = root.has("reason") ? root.get("reason").asText() : "";

            AgentIntent llmIntent = parseIntent(intentStr);
            AgentType llmType = parseType(typeStr);

            List<String> mergedSignals = new ArrayList<>(existingSignals);
            if (root.has("matchedSignals") && root.get("matchedSignals").isArray()) {
                for (JsonNode signalNode : root.get("matchedSignals")) {
                    String signal = signalNode.asText();
                    if (!mergedSignals.contains(signal)) {
                        mergedSignals.add(signal);
                    }
                }
            }

            return new IntentAnalysisResult(llmIntent, llmType, llmConfidence, mergedSignals,
                    "LLM增强: " + llmReason);
        } catch (Exception e) {
            LOGGER.warn("LLM 意图响应解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 构建 LLM 意图分析的 Prompt。
     *
     * @param question 用户问题
     * @return Prompt 文本
     */
    private String buildLlmIntentPrompt(String question) {
        return """
                请分析用户问题的意图，返回 JSON 格式：
                {"intent":"意图枚举","preferredType":"专家类型","confidence":0.0-1.0,"matchedSignals":["信号"],"reason":"分析原因"}

                可选意图：GENERAL_CHAT, DATA_ANALYSIS, KNOWLEDGE_RETRIEVAL, REPORT_GENERATION, TOOL_ORCHESTRATION, COMPLEX_REASONING
                可选专家类型：REACT, SKILL, DATA, KNOWLEDGE, CHART, REPORT, CHAT

                用户问题：""" + question;
    }

    /**
     * 安全解析意图字符串。
     */
    private AgentIntent parseIntent(String intentStr) {
        if (!StringUtils.hasText(intentStr)) {
            return AgentIntent.COMPLEX_REASONING;
        }
        try {
            return AgentIntent.valueOf(intentStr.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return AgentIntent.COMPLEX_REASONING;
        }
    }

    /**
     * 安全解析专家类型字符串。
     */
    private AgentType parseType(String typeStr) {
        if (!StringUtils.hasText(typeStr)) {
            return AgentType.REACT;
        }
        try {
            return AgentType.fromCode(typeStr.trim());
        } catch (IllegalArgumentException e) {
            return AgentType.REACT;
        }
    }
}
