package com.ai.agent.orchestrator;

import com.ai.model.AnalysisRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.ai.agent.TaskClassification;
import com.ai.agent.TaskComplexity;
import com.ai.agent.react.ReActAgent;
import com.ai.agent.react.ReActRequestContext;

/**
 * 在进入 ReAct 循环前创建轻量级执行计划。
 *
 * <p>这是 Plan-Execute-Reflect 架构的第一步。当前实现保持确定性和安全，后续可以
 * 在不改动 ReActAgent 的前提下替换成 LLM 驱动的规划器。</p>
 *
 * @author data-agent
 */
@Component
public class TaskPlanner {

    private static final List<String> DATA_KEYWORDS = List.of(
            "数据", "销售", "订单", "客户", "利润", "gmv", "同比", "环比", "趋势", "统计", "分析");
    private static final List<String> CHART_KEYWORDS = List.of("图表", "画图", "可视化", "折线图", "柱状图", "饼图");
    private static final List<String> KNOWLEDGE_KEYWORDS = List.of("知识库", "资料", "文档", "制度", "说明", "向量");

    /**
     * 根据请求、检索上下文和分类结果构建执行计划。
     *
     * @param request 用户分析请求
     * @param requestContext 增强后的 ReAct 请求上下文
     * @param classification 任务分类结果
     * @return 确定性的执行计划
     */
    public ExecutionPlan plan(AnalysisRequest request, ReActRequestContext requestContext,
            TaskClassification classification) {
        if (classification.fastPathAllowed()) {
            return new ExecutionPlan(classification.complexity(), classification.reason(),
                    List.of(new ExecutionPlanStep("direct-answer", "直接回答用户的简单问题", "", false)));
        }
        List<ExecutionPlanStep> steps = new ArrayList<>();
        addExplicitContextSteps(request, steps);
        addKeywordDrivenSteps(request, requestContext, steps);
        if (steps.isEmpty()) {
            addDefaultSteps(classification, steps);
        }
        return new ExecutionPlan(classification.complexity(), classification.reason(), steps);
    }

    private void addExplicitContextSteps(AnalysisRequest request, List<ExecutionPlanStep> steps) {
        if (request.hasFile()) {
            steps.add(new ExecutionPlanStep("read-file", "读取并理解上传文件内容", "getFileContent/analyzeFileData",
                    false, 1));
        }
        if (request.hasSkill()) {
            steps.add(new ExecutionPlanStep("run-skill", "按用户指定技能执行任务", "useSkill", false, 2));
        }
        if (request.hasAgent()) {
            steps.add(new ExecutionPlanStep("route-agent", "按用户指定 Agent 能力处理请求", "", false, 2));
        }
    }

    private void addKeywordDrivenSteps(AnalysisRequest request, ReActRequestContext requestContext,
            List<ExecutionPlanStep> steps) {
        String question = normalize(request.getQuestion());
        if (hasRagContext(requestContext) || containsAny(question, KNOWLEDGE_KEYWORDS)) {
            steps.add(new ExecutionPlanStep("retrieve-knowledge", "检索相关知识库或企业资料并保留引用来源",
                    "searchKnowledge", true, 1));
        }
        if (containsAny(question, DATA_KEYWORDS)) {
            steps.add(new ExecutionPlanStep("inspect-data", "确认可用数据范围和核心指标口径",
                    "listDataSources/queryDataSource", true, 1));
            steps.add(new ExecutionPlanStep("analyze-metrics", "计算关键指标并识别趋势、异常和原因", "calculate",
                    false, 2));
        }
        if (containsAny(question, CHART_KEYWORDS)) {
            steps.add(new ExecutionPlanStep("build-chart", "根据分析结果生成合适的图表配置", "generateChart",
                    false, 3));
        }
    }

    private void addDefaultSteps(TaskClassification classification, List<ExecutionPlanStep> steps) {
        if (classification.complexity() == TaskComplexity.TOOL_ASSISTED) {
            steps.add(new ExecutionPlanStep("choose-tool", "选择最合适的工具获取事实依据", "", false, 1));
            steps.add(new ExecutionPlanStep("summarize-result", "整合工具结果并给出结论", "", false, 2));
            return;
        }
        steps.add(new ExecutionPlanStep("understand-intent", "拆解用户意图和需要验证的事实", "", false, 1));
        steps.add(new ExecutionPlanStep("gather-context", "按需检索记忆、知识库或业务数据", "searchMemory/searchKnowledge",
                true, 1));
        steps.add(new ExecutionPlanStep("validate-answer", "校验结论是否有依据并说明不确定性", "", false, 2));
    }

    /**
     * 创建不含规划阶段的直通计划。
     *
     * @param classification 任务分类结果
     * @return 直通计划
     */
    public ExecutionPlan passthrough(TaskClassification classification) {
        return new ExecutionPlan(classification.complexity(), "增强规划已关闭，使用标准 ReAct 循环",
                List.of(new ExecutionPlanStep("react-loop", "使用标准 ReAct 循环处理请求", "", false, 1)));
    }

    private boolean hasRagContext(ReActRequestContext requestContext) {
        return requestContext != null && requestContext.ragContext() != null && requestContext.ragContext().hasContext();
    }

    private boolean containsAny(String value, List<String> keywords) {
        return keywords.stream().anyMatch(value::contains);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
