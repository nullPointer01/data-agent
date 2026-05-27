package com.ai.agent.orchestrator;

import com.ai.mcp.McpModelService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.ai.agent.AgentIntent;
import com.ai.agent.AgentType;
import com.ai.agent.IntentAnalysisResult;

/**
 * 根据识别出的意图构建轻量级编排计划。
 *
 * <p>当前实现保持确定性，不依赖 LLM。这样既能验证协作链路，也便于稳定测试。</p>
 *
 * @author data-agent
 */
@Service
public class OrchestratorTaskPlanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrchestratorTaskPlanner.class);
    private static final String TASK_COLLECT_DATA = "t1";
    private static final String TASK_RETRIEVE_KNOWLEDGE = "t2";
    private static final String TASK_SYNTHESIZE_ANALYSIS = "t3";
    private static final String TASK_BUILD_REPORT = "t4";
    private static final String TASK_PREVIEW_SOURCE = "t0";
    private static final String JSON_START = "{";
    private static final String JSON_END = "}";

    private final McpModelService modelService;
    private final ObjectMapper objectMapper;
    private final OrchestratorProperties orchestratorProperties;

    @Autowired
    public OrchestratorTaskPlanner(@Nullable McpModelService modelService,
            ObjectMapper objectMapper,
            OrchestratorProperties orchestratorProperties) {
        this.modelService = modelService;
        this.objectMapper = objectMapper;
        this.orchestratorProperties = orchestratorProperties;
    }

    /**
     * 为当前请求构建编排计划。
     *
     * @param userQuery 用户请求
     * @param intentAnalysis 已分析的意图
     * @return 编排计划
     */
    public OrchestrationPlan plan(String userQuery, IntentAnalysisResult intentAnalysis) {
        OrchestrationPlan rulePlan = planByRules(userQuery, intentAnalysis);
        if (!shouldUseLlm(rulePlan, intentAnalysis)) {
            return rulePlan;
        }
        return planByLlm(userQuery, intentAnalysis, rulePlan);
    }

    private OrchestrationPlan planByRules(String userQuery, IntentAnalysisResult intentAnalysis) {
        if (requiresCollaboration(intentAnalysis)) {
            return planCollaboration(userQuery, intentAnalysis);
        }
        if (intentAnalysis != null && AgentType.CHART == intentAnalysis.preferredType()) {
            return planChartGeneration(userQuery, intentAnalysis);
        }
        if (intentAnalysis != null && AgentIntent.DATA_ANALYSIS == intentAnalysis.intent()) {
            return planDataAnalysis(userQuery, intentAnalysis);
        }
        if (intentAnalysis != null && AgentIntent.KNOWLEDGE_RETRIEVAL == intentAnalysis.intent()) {
            return planKnowledgeRetrieval(userQuery, intentAnalysis);
        }
        return planSingleTask(userQuery, intentAnalysis);
    }

    /**
     * 为简单对话类意图构建单任务兜底计划。
     *
     * @param intentAnalysis 已分析的意图
     * @return 最小计划
     */
    public OrchestrationPlan planSimple(IntentAnalysisResult intentAnalysis) {
        return plan("", intentAnalysis);
    }

    private boolean shouldUseLlm(OrchestrationPlan rulePlan, IntentAnalysisResult intentAnalysis) {
        return modelService != null
                && orchestratorProperties.isLlmPlanningEnabled()
                && rulePlan != null
                && StringUtils.hasText(rulePlan.originalQuery())
                && intentAnalysis != null
                && (requiresCollaboration(intentAnalysis) || AgentIntent.COMPLEX_REASONING == intentAnalysis.intent());
    }

    private OrchestrationPlan planByLlm(String userQuery, IntentAnalysisResult intentAnalysis,
            OrchestrationPlan fallback) {
        try {
            String response = modelService.callModel(buildPlanningPrompt(userQuery, intentAnalysis, fallback),
                    orchestratorProperties.getModelId());
            OrchestrationPlan plan = parsePlanResponse(response, userQuery, fallback.sharedContextKeys());
            return plan.hasTasks() ? plan : fallback;
        } catch (Exception e) {
            LOGGER.warn("Orchestrator LLM 任务规划失败，回退规则计划: {}", e.getMessage());
            return fallback;
        }
    }

    private static final String PLANNING_PROMPT_TEMPLATE = loadPromptResource("prompts/orchestrator-planning.txt");

    private static String loadPromptResource(String path) {
        try (var is = OrchestratorTaskPlanner.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                return "%s\n%s\nmax_tasks=%d\nfallback=%s";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String buildPlanningPrompt(String userQuery, IntentAnalysisResult intentAnalysis,
            OrchestrationPlan fallback) {
        return PLANNING_PROMPT_TEMPLATE.formatted(userQuery, intentAnalysis.toThinkingContent(),
                Math.max(1, orchestratorProperties.getMaxPlanTasks()),
                fallback.toThinkingContent());
    }

    private OrchestrationPlan parsePlanResponse(String response, String userQuery, List<String> fallbackSharedKeys)
            throws java.io.IOException {
        JsonNode root = objectMapper.readTree(extractJson(response));
        List<OrchestrationTask> tasks = parseTasks(root.path("tasks"), userQuery);
        List<ExecutionPhase> phases = parsePhases(root.path("executionPhases"), tasks);
        List<String> sharedContextKeys = parseTextArray(root.path("sharedContextKeys"), fallbackSharedKeys);
        return new OrchestrationPlan(UUID.randomUUID().toString(), safeQuery(userQuery),
                tasks, phases, sharedContextKeys, System.currentTimeMillis());
    }

    private List<OrchestrationTask> parseTasks(JsonNode tasksNode, String userQuery) {
        if (tasksNode == null || !tasksNode.isArray()) {
            return List.of();
        }
        List<OrchestrationTask> tasks = new ArrayList<>();
        int maxTasks = Math.max(1, orchestratorProperties.getMaxPlanTasks());
        for (JsonNode taskNode : tasksNode) {
            if (tasks.size() >= maxTasks) {
                break;
            }
            OrchestrationTask task = parseTask(taskNode, userQuery, tasks.size() + 1);
            if (task != null) {
                tasks.add(task);
            }
        }
        return List.copyOf(tasks);
    }

    private OrchestrationTask parseTask(JsonNode taskNode, String userQuery, int index) {
        if (taskNode == null || !taskNode.isObject()) {
            return null;
        }
        String taskId = textOrDefault(taskNode.path("taskId"), "t" + index);
        String description = textOrDefault(taskNode.path("description"), "执行用户请求");
        String specialistName = parseSpecialistName(taskNode.path("specialistName").asText());
        Map<String, Object> parameters = Map.of("query", safeQuery(userQuery));
        List<String> inputFrom = parseTextArray(taskNode.path("inputFrom"), List.of());
        List<String> outputTo = parseTextArray(taskNode.path("outputTo"), List.of());
        List<String> canParallelWith = parseTextArray(taskNode.path("canParallelWith"), List.of());
        String expectedOutput = textOrDefault(taskNode.path("expectedOutput"), "输出任务结果");
        return new OrchestrationTask(taskId, description, specialistName, parameters,
                inputFrom, outputTo, canParallelWith, expectedOutput);
    }

    private String parseSpecialistName(String value) {
        try {
            return AgentType.fromCode(value).name();
        } catch (Exception e) {
            LOGGER.debug("无法解析 specialist 类型 '{}', 回退为 REACT: {}", value, e.getMessage());
            return AgentType.REACT.name();
        }
    }

    private List<ExecutionPhase> parsePhases(JsonNode phasesNode, List<OrchestrationTask> tasks) {
        Set<String> taskIds = new LinkedHashSet<>(tasks.stream().map(OrchestrationTask::taskId).toList());
        if (phasesNode == null || !phasesNode.isArray()) {
            return defaultPhases(tasks);
        }
        List<ExecutionPhase> phases = new ArrayList<>();
        for (JsonNode phaseNode : phasesNode) {
            List<String> phaseTasks = parseTextArray(phaseNode.path("tasks"), List.of()).stream()
                    .filter(taskIds::contains)
                    .toList();
            if (!phaseTasks.isEmpty()) {
                int phase = phaseNode.path("phase").canConvertToInt() ? phaseNode.path("phase").asInt() : phases.size() + 1;
                phases.add(new ExecutionPhase(phase, phaseTasks, phaseNode.path("parallel").asBoolean(false)));
            }
        }
        return phases.isEmpty() ? defaultPhases(tasks) : List.copyOf(phases);
    }

    private List<ExecutionPhase> defaultPhases(List<OrchestrationTask> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        return List.of(new ExecutionPhase(1, tasks.stream().map(OrchestrationTask::taskId).toList(), false));
    }

    private List<String> parseTextArray(JsonNode node, List<String> fallback) {
        if (node == null || !node.isArray()) {
            return fallback;
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            if (StringUtils.hasText(item.asText())) {
                values.add(item.asText());
            }
        });
        return values.isEmpty() ? fallback : List.copyOf(values);
    }

    private String textOrDefault(JsonNode node, String fallback) {
        return node != null && StringUtils.hasText(node.asText()) ? node.asText() : fallback;
    }

    private String extractJson(String response) {
        if (!StringUtils.hasText(response)) {
            return "{}";
        }
        int start = response.indexOf(JSON_START);
        int end = response.lastIndexOf(JSON_END);
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    private boolean requiresCollaboration(IntentAnalysisResult intentAnalysis) {
        if (intentAnalysis == null) {
            return false;
        }
        return AgentIntent.REPORT_GENERATION == intentAnalysis.intent()
                || AgentIntent.TOOL_ORCHESTRATION == intentAnalysis.intent();
    }

    private OrchestrationPlan planCollaboration(String userQuery, IntentAnalysisResult intentAnalysis) {
        OrchestrationTask collectDataTask = new OrchestrationTask(TASK_COLLECT_DATA,
                "收集数据源、上传文件或指标上下文",
                AgentType.DATA.name(),
                Map.of("query", safeQuery(userQuery)),
                List.of(),
                List.of(TASK_SYNTHESIZE_ANALYSIS),
                List.of(TASK_RETRIEVE_KNOWLEDGE),
                "输出可用于分析的数据事实");
        OrchestrationTask retrieveKnowledgeTask = new OrchestrationTask(TASK_RETRIEVE_KNOWLEDGE,
                "检索知识库、历史记忆或业务资料",
                AgentType.KNOWLEDGE.name(),
                Map.of("query", safeQuery(userQuery)),
                List.of(),
                List.of(TASK_SYNTHESIZE_ANALYSIS),
                List.of(TASK_COLLECT_DATA),
                "输出与问题相关的知识依据");
        OrchestrationTask synthesizeTask = new OrchestrationTask(TASK_SYNTHESIZE_ANALYSIS,
                "结合数据事实和知识依据形成分析结论",
                chooseSynthesisSpecialist(intentAnalysis),
                Map.of("query", safeQuery(userQuery)),
                List.of(TASK_COLLECT_DATA, TASK_RETRIEVE_KNOWLEDGE),
                buildSynthesisOutput(intentAnalysis),
                List.of(),
                "输出综合分析结论");
        List<OrchestrationTask> tasks = appendReportTaskIfNeeded(intentAnalysis,
                collectDataTask, retrieveKnowledgeTask, synthesizeTask);
        List<ExecutionPhase> phases = buildPhases(intentAnalysis);
        return new OrchestrationPlan(UUID.randomUUID().toString(), safeQuery(userQuery), tasks, phases,
                chooseSharedContextKeys(intentAnalysis), System.currentTimeMillis());
    }

    private OrchestrationPlan planDataAnalysis(String userQuery, IntentAnalysisResult intentAnalysis) {
        OrchestrationTask previewTask = new OrchestrationTask(TASK_PREVIEW_SOURCE,
                "预览数据源、文件或已有上下文，确认可用字段和范围",
                AgentType.DATA.name(),
                Map.of("query", safeQuery(userQuery)),
                List.of(),
                List.of(TASK_COLLECT_DATA),
                List.of(),
                "输出可供分析的原始事实");
        OrchestrationTask analyzeTask = new OrchestrationTask(TASK_COLLECT_DATA,
                "基于可用数据执行指标分析并总结趋势",
                AgentType.DATA.name(),
                Map.of("query", safeQuery(userQuery)),
                List.of(TASK_PREVIEW_SOURCE),
                List.of(),
                List.of(),
                "输出分析结论和关键指标");
        List<OrchestrationTask> tasks = List.of(previewTask, analyzeTask);
        List<ExecutionPhase> phases = List.of(
                new ExecutionPhase(1, List.of(TASK_PREVIEW_SOURCE), true),
                new ExecutionPhase(2, List.of(TASK_COLLECT_DATA), false));
        return new OrchestrationPlan(UUID.randomUUID().toString(), safeQuery(userQuery), tasks, phases,
                chooseSharedContextKeys(intentAnalysis), System.currentTimeMillis());
    }

    private OrchestrationPlan planKnowledgeRetrieval(String userQuery, IntentAnalysisResult intentAnalysis) {
        OrchestrationTask retrieveTask = new OrchestrationTask(TASK_RETRIEVE_KNOWLEDGE,
                "检索知识库、历史记忆和企业资料",
                AgentType.KNOWLEDGE.name(),
                Map.of("query", safeQuery(userQuery)),
                List.of(),
                List.of(TASK_SYNTHESIZE_ANALYSIS),
                List.of(),
                "输出可引用的知识依据");
        OrchestrationTask synthesizeTask = new OrchestrationTask(TASK_SYNTHESIZE_ANALYSIS,
                "整理检索结果并形成简明结论",
                AgentType.REACT.name(),
                Map.of("query", safeQuery(userQuery)),
                List.of(TASK_RETRIEVE_KNOWLEDGE),
                List.of(),
                List.of(),
                "输出整合后的回答");
        List<OrchestrationTask> tasks = List.of(retrieveTask, synthesizeTask);
        List<ExecutionPhase> phases = List.of(
                new ExecutionPhase(1, List.of(TASK_RETRIEVE_KNOWLEDGE), true),
                new ExecutionPhase(2, List.of(TASK_SYNTHESIZE_ANALYSIS), false));
        return new OrchestrationPlan(UUID.randomUUID().toString(), safeQuery(userQuery), tasks, phases,
                chooseSharedContextKeys(intentAnalysis), System.currentTimeMillis());
    }

    private OrchestrationPlan planChartGeneration(String userQuery, IntentAnalysisResult intentAnalysis) {
        OrchestrationTask collectDataTask = new OrchestrationTask(TASK_COLLECT_DATA,
                "收集图表所需的数据事实和字段结构",
                AgentType.DATA.name(),
                Map.of("query", safeQuery(userQuery)),
                List.of(),
                List.of(TASK_SYNTHESIZE_ANALYSIS),
                List.of(),
                "输出可视化所需的数据样本");
        OrchestrationTask chartTask = new OrchestrationTask(TASK_SYNTHESIZE_ANALYSIS,
                "根据数据事实生成图表配置",
                AgentType.CHART.name(),
                Map.of("query", safeQuery(userQuery)),
                List.of(TASK_COLLECT_DATA),
                List.of(),
                List.of(),
                "输出 ECharts 图表配置");
        List<OrchestrationTask> tasks = List.of(collectDataTask, chartTask);
        List<ExecutionPhase> phases = List.of(
                new ExecutionPhase(1, List.of(TASK_COLLECT_DATA), false),
                new ExecutionPhase(2, List.of(TASK_SYNTHESIZE_ANALYSIS), false));
        return new OrchestrationPlan(UUID.randomUUID().toString(), safeQuery(userQuery), tasks, phases,
                chooseSharedContextKeys(intentAnalysis), System.currentTimeMillis());
    }

    private OrchestrationPlan planSingleTask(String userQuery, IntentAnalysisResult intentAnalysis) {
        String taskId = TASK_COLLECT_DATA;
        String specialistName = chooseSpecialistName(intentAnalysis);
        OrchestrationTask task = new OrchestrationTask(taskId, buildTaskDescription(intentAnalysis),
                specialistName, Map.of("query", safeQuery(userQuery)), List.of(), List.of(), List.of(),
                "返回可直接给用户的答案");
        ExecutionPhase phase = new ExecutionPhase(1, List.of(taskId), false);
        return new OrchestrationPlan(UUID.randomUUID().toString(), safeQuery(userQuery), List.of(task), List.of(phase),
                chooseSharedContextKeys(intentAnalysis), System.currentTimeMillis());
    }

    private List<OrchestrationTask> appendReportTaskIfNeeded(IntentAnalysisResult intentAnalysis,
            OrchestrationTask collectDataTask,
            OrchestrationTask retrieveKnowledgeTask,
            OrchestrationTask synthesizeTask) {
        if (intentAnalysis == null || AgentIntent.REPORT_GENERATION != intentAnalysis.intent()) {
            return List.of(collectDataTask, retrieveKnowledgeTask, synthesizeTask);
        }
        OrchestrationTask reportTask = new OrchestrationTask(TASK_BUILD_REPORT,
                "基于综合分析结论生成面向用户的报告",
                AgentType.REPORT.name(),
                Map.of(),
                List.of(TASK_SYNTHESIZE_ANALYSIS),
                List.of(),
                List.of(),
                "输出完整报告或总结");
        return List.of(collectDataTask, retrieveKnowledgeTask, synthesizeTask, reportTask);
    }

    private List<ExecutionPhase> buildPhases(IntentAnalysisResult intentAnalysis) {
        if (intentAnalysis != null && AgentIntent.REPORT_GENERATION == intentAnalysis.intent()) {
            return List.of(
                    new ExecutionPhase(1, List.of(TASK_COLLECT_DATA, TASK_RETRIEVE_KNOWLEDGE), true),
                    new ExecutionPhase(2, List.of(TASK_SYNTHESIZE_ANALYSIS), false),
                    new ExecutionPhase(3, List.of(TASK_BUILD_REPORT), false));
        }
        return List.of(
                new ExecutionPhase(1, List.of(TASK_COLLECT_DATA, TASK_RETRIEVE_KNOWLEDGE), true),
                new ExecutionPhase(2, List.of(TASK_SYNTHESIZE_ANALYSIS), false));
    }

    private List<String> buildSynthesisOutput(IntentAnalysisResult intentAnalysis) {
        if (intentAnalysis != null && AgentIntent.REPORT_GENERATION == intentAnalysis.intent()) {
            return List.of(TASK_BUILD_REPORT);
        }
        return List.of();
    }

    private String chooseSynthesisSpecialist(IntentAnalysisResult intentAnalysis) {
        if (intentAnalysis != null && AgentIntent.REPORT_GENERATION == intentAnalysis.intent()) {
            return AgentType.REPORT.name();
        }
        return AgentType.DATA.name();
    }

    private String chooseSpecialistName(IntentAnalysisResult intentAnalysis) {
        if (intentAnalysis == null) {
            return AgentType.REACT.name();
        }
        if (AgentIntent.DATA_ANALYSIS == intentAnalysis.intent()) {
            return AgentType.DATA.name();
        }
        if (AgentIntent.KNOWLEDGE_RETRIEVAL == intentAnalysis.intent()) {
            return AgentType.KNOWLEDGE.name();
        }
        if (AgentIntent.REPORT_GENERATION == intentAnalysis.intent()) {
            return AgentType.REPORT.name();
        }
        if (AgentIntent.GENERAL_CHAT == intentAnalysis.intent()) {
            return AgentType.CHAT.name();
        }
        return AgentType.REACT.name();
    }

    private String buildTaskDescription(IntentAnalysisResult intentAnalysis) {
        if (intentAnalysis == null) {
            return "执行用户请求";
        }
        return switch (intentAnalysis.intent()) {
            case DATA_ANALYSIS -> "执行数据分析与指标解释";
            case KNOWLEDGE_RETRIEVAL -> "检索知识库并整理引用";
            case REPORT_GENERATION -> "生成报告或总结内容";
            case TOOL_ORCHESTRATION -> "编排工具并汇总结果";
            case GENERAL_CHAT -> "直接回复简单问题";
            case COMPLEX_REASONING -> "完成复杂推理并形成结论";
        };
    }

    private List<String> chooseSharedContextKeys(IntentAnalysisResult intentAnalysis) {
        if (intentAnalysis == null) {
            return List.of();
        }
        if (intentAnalysis.intent() == AgentIntent.DATA_ANALYSIS) {
            return List.of("analysis_result", "metrics", "trend");
        }
        if (intentAnalysis.intent() == AgentIntent.KNOWLEDGE_RETRIEVAL) {
            return List.of("knowledge_hits", "citations");
        }
        if (intentAnalysis.intent() == AgentIntent.REPORT_GENERATION) {
            return List.of("summary", "report");
        }
        return List.of();
    }

    private String safeQuery(String userQuery) {
        return userQuery == null ? "" : userQuery;
    }
}
