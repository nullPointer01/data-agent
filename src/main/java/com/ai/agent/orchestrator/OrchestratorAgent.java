package com.ai.agent.orchestrator;

import com.ai.memory.MemoryManager;
import com.ai.memory.dto.MemoryContext;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.model.ConversationSession;
import com.ai.service.AgentProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.ai.agent.AgentType;
import com.ai.agent.CollaborationManager;
import com.ai.agent.IntentAnalysisResult;
import com.ai.agent.IntentAnalyzer;
import com.ai.agent.ResultIntegrator;
import com.ai.agent.TaskClassification;
import com.ai.agent.TaskComplexityClassifier;
import com.ai.agent.react.ReActAgent;
import com.ai.agent.specialist.AgentSpecialistRegistry;
import com.ai.agent.specialist.SpecialistFactory;
import com.ai.agent.specialist.SpecialistResult;
import com.ai.agent.specialist.SpecialistTask;
import com.ai.agent.AgentExecutionRequest;
import com.ai.agent.specialist.AgentSpecialist;

/**
 * 编排器 Agent，负责路由和协调多专家执行。
 *
 * <p>根据任务复杂度和意图分析，将请求路由到匹配的专家配置或兜底到内置 ReAct Agent。
 * 支持单任务路由、多专家协作和结构化执行计划。</p>
 *
 * @author data-agent
 */
@Component
public class OrchestratorAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrchestratorAgent.class);
    private static final String REACT_FALLBACK_NAME = "内置 ReAct";
    private static final String MULTI_AGENT_NAME = "多专家协作";
    private static final String SKILL_USED_PREFIX = "orchestrator:";
    private static final String SKILL_USED_REACT = "orchestrator:react";
    private static final String SKILL_USED_MULTI = "orchestrator:multi-agent";

    private final AgentProfileService profileService;
    private final AgentSpecialistRegistry specialistRegistry;
    private final ReActAgent reActAgent;
    private final TaskComplexityClassifier taskComplexityClassifier;
    private final IntentAnalyzer intentAnalyzer;
    private final OrchestratorTaskPlanner orchestratorTaskPlanner;
    private final CollaborationManager collaborationManager;
    private final ResultIntegrator resultIntegrator;
    private final ParallelTaskExecutor parallelTaskExecutor;
    private final MemoryManager memoryManager;
    private final SpecialistFactory specialistFactory;

    /**
     * 构造编排器 Agent。
     *
     * @param profileService Agent 配置服务
     * @param specialistRegistry 专家注册表
     * @param reActAgent 内置 ReAct Agent
     * @param taskComplexityClassifier 任务复杂度分类器
     * @param intentAnalyzer 意图分析器
     * @param orchestratorTaskPlanner 编排任务规划器
     * @param collaborationManager 协作管理器
     * @param resultIntegrator 结果整合器
     * @param parallelTaskExecutor 并行任务执行器
     * @param memoryManager 记忆管理器
     * @param specialistFactory 专家工厂
     */
    public OrchestratorAgent(AgentProfileService profileService,
            AgentSpecialistRegistry specialistRegistry,
            ReActAgent reActAgent,
            TaskComplexityClassifier taskComplexityClassifier,
            IntentAnalyzer intentAnalyzer,
            OrchestratorTaskPlanner orchestratorTaskPlanner,
            CollaborationManager collaborationManager,
            ResultIntegrator resultIntegrator,
            ParallelTaskExecutor parallelTaskExecutor,
            MemoryManager memoryManager,
            SpecialistFactory specialistFactory) {
        this.profileService = profileService;
        this.specialistRegistry = specialistRegistry;
        this.reActAgent = reActAgent;
        this.taskComplexityClassifier = taskComplexityClassifier;
        this.intentAnalyzer = intentAnalyzer;
        this.orchestratorTaskPlanner = orchestratorTaskPlanner;
        this.collaborationManager = collaborationManager;
        this.resultIntegrator = resultIntegrator;
        this.parallelTaskExecutor = parallelTaskExecutor;
        this.memoryManager = memoryManager;
        this.specialistFactory = specialistFactory;
    }

    /**
     * 执行请求并返回分析响应。
     *
     * @param request 分析请求
     * @param fileContent 可选文件内容
     * @param session 当前会话
     * @return 分析响应
     */
    public AnalysisResponse execute(AnalysisRequest request, String fileContent, ConversationSession session) {
        OrchestratorExecutionResult result = executeWithTrace(request, fileContent, session);
        return result.response();
    }

    /**
     * 执行请求并返回带追踪信息的结构化结果。
     *
     * @param request 分析请求
     * @param fileContent 可选文件内容
     * @param session 当前会话
     * @return 执行结果
     */
    public OrchestratorExecutionResult executeWithTrace(AnalysisRequest request, String fileContent,
            ConversationSession session) {
        OrchestratorDecision decision = decide(request, fileContent, session);
        MemoryContext memoryContext = buildMemoryContext(request);

        if (decision.selectedProfile() != null) {
            // 路由到匹配的专家配置
            return executeWithProfile(decision, request, fileContent, memoryContext);
        }

        // 兜底到内置 ReAct
        return executeWithReAct(decision, request, fileContent);
    }

    /**
     * 结构化执行请求，返回包含计划、共享上下文的完整结果。
     *
     * @param request 分析请求
     * @param fileContent 可选文件内容
     * @param session 当前会话
     * @return 结构化执行结果
     */
    public OrchestratorResult executeStructured(AnalysisRequest request, String fileContent,
            ConversationSession session) {
        try {
            OrchestratorDecision decision = decide(request, fileContent, session);
            MemoryContext memoryContext = buildMemoryContext(request);
            IntentAnalysisResult intentResult = decision.intentResult();
            OrchestrationPlan plan = orchestratorTaskPlanner.plan(
                    request.getQuestion(), intentResult);

            if (!plan.hasTasks()) {
                // 无任务时直接路由
                OrchestratorExecutionResult execResult;
                if (decision.selectedProfile() != null) {
                    execResult = executeWithProfile(decision, request, fileContent, memoryContext);
                } else {
                    execResult = executeWithReAct(decision, request, fileContent);
                }
                return new OrchestratorResult(execResult.response().isSuccess(),
                        execResult.response().getResult(),
                        execResult.selectedType(),
                        execResult.selectedAgentName(),
                        execResult, plan, Map.of(), intentResult);
            }

            // 多任务协作执行
            return executeMultiTaskPlan(decision, plan, request, fileContent, memoryContext, intentResult);

        } catch (Exception e) {
            LOGGER.error("编排器执行失败: {}", e.getMessage(), e);
            return OrchestratorResult.failure("编排器执行失败: " + e.getMessage());
        }
    }

    /**
     * 对请求进行路由决策。
     *
     * @param request 分析请求
     * @param fileContent 可选文件内容
     * @param session 当前会话
     * @return 路由决策
     */
    public OrchestratorDecision decide(AnalysisRequest request, String fileContent, ConversationSession session) {
        TaskClassification classification = taskComplexityClassifier.classify(request, fileContent, session);
        IntentAnalysisResult intentResult = intentAnalyzer.analyze(request, fileContent, classification);

        List<AgentProfile> profiles = profileService.listEnabledProfiles();

        // 优先文本匹配
        AgentProfile matchedProfile = matchProfileByText(request, profiles);
        if (matchedProfile != null) {
            return new OrchestratorDecision(classification, intentResult, null,
                    matchedProfile, matchedProfile.getType(),
                    "文本匹配到配置: " + matchedProfile.getName());
        }

        // 其次意图类型匹配
        AgentType preferredType = intentResult != null ? intentResult.preferredType() : AgentType.REACT;
        matchedProfile = matchProfileByType(preferredType, profiles);
        if (matchedProfile != null) {
            return new OrchestratorDecision(classification, intentResult, null,
                    matchedProfile, matchedProfile.getType(),
                    "意图类型匹配到配置: " + matchedProfile.getName());
        }

        // 兜底
        return new OrchestratorDecision(classification, intentResult, null,
                null, preferredType, "未匹配到配置，使用意图推荐类型");
    }

    /**
     * 按文本匹配启用的 Agent 配置（profile 名称或描述出现在问题中，或反之）。
     */
    private AgentProfile matchProfileByText(AnalysisRequest request, List<AgentProfile> profiles) {
        if (profiles == null || profiles.isEmpty() || request == null
                || !StringUtils.hasText(request.getQuestion())) {
            return null;
        }
        String question = request.getQuestion().toLowerCase(Locale.ROOT);
        for (AgentProfile profile : profiles) {
            String name = profile.getName() == null ? "" : profile.getName().toLowerCase(Locale.ROOT);
            String description = profile.getDescription() == null ? ""
                    : profile.getDescription().toLowerCase(Locale.ROOT);
            if ((!name.isEmpty() && question.contains(name))
                    || (!description.isEmpty() && question.contains(description))
                    || (!name.isEmpty() && name.contains(question))
                    || (!description.isEmpty() && description.contains(question))) {
                return profile;
            }
        }
        return null;
    }

    /**
     * 按意图推荐的 Agent 类型匹配启用的配置。
     */
    private AgentProfile matchProfileByType(AgentType type, List<AgentProfile> profiles) {
        if (type == null || profiles == null || profiles.isEmpty()) {
            return null;
        }
        for (AgentProfile profile : profiles) {
            if (type == profile.getType()) {
                return profile;
            }
        }
        return null;
    }

    /**
     * 使用匹配的 Agent 配置执行。
     */
    private OrchestratorExecutionResult executeWithProfile(OrchestratorDecision decision,
            AnalysisRequest request, String fileContent, MemoryContext memoryContext) {
        AgentProfile profile = decision.selectedProfile();
        AgentSpecialist specialist = specialistRegistry.resolve(profile.getType());
        AgentExecutionRequest execRequest = new AgentExecutionRequest(profile, request, fileContent, null,
                memoryContext);
        AnalysisResponse response = specialist.execute(execRequest);
        response.setSkillUsed(SKILL_USED_PREFIX + profile.getName());

        List<AnalysisResponse.ThinkingStep> thinkingSteps = new ArrayList<>();
        thinkingSteps.add(new AnalysisResponse.ThinkingStep(1, "orchestrator",
                "路由到专家: " + profile.getName() + " (" + profile.getType() + ")"));
        if (response.getThinkingSteps() != null) {
            thinkingSteps.addAll(response.getThinkingSteps());
        }
        response.setThinkingSteps(thinkingSteps);

        OrchestratorExecutionResult result = new OrchestratorExecutionResult(null, response, false,
                profile.getType(), profile.getName());
        return result;
    }

    /**
     * 兜底到内置 ReAct Agent 执行。
     */
    private OrchestratorExecutionResult executeWithReAct(OrchestratorDecision decision,
            AnalysisRequest request, String fileContent) {
        String enhancedFileContent = buildReActFileContent(fileContent, decision);
        AnalysisResponse response = reActAgent.execute(request, enhancedFileContent);
        response.setSkillUsed(SKILL_USED_REACT);

        List<AnalysisResponse.ThinkingStep> thinkingSteps = new ArrayList<>();
        thinkingSteps.add(new AnalysisResponse.ThinkingStep(1, "orchestrator",
                "未匹配专家配置，回退到 " + REACT_FALLBACK_NAME));
        if (response.getThinkingSteps() != null) {
            thinkingSteps.addAll(response.getThinkingSteps());
        }
        response.setThinkingSteps(thinkingSteps);

        OrchestratorExecutionResult result = new OrchestratorExecutionResult(null, response, true,
                AgentType.REACT, REACT_FALLBACK_NAME);
        return result;
    }

    /**
     * 执行多任务编排计划。
     */
    private OrchestratorResult executeMultiTaskPlan(OrchestratorDecision decision,
            OrchestrationPlan plan, AnalysisRequest request, String fileContent,
            MemoryContext memoryContext, IntentAnalysisResult intentResult) {
        List<SpecialistResult> taskResults = new ArrayList<>();
        List<AnalysisResponse.ThinkingStep> allThinkingSteps = new ArrayList<>();
        Map<String, Object> sharedContext = new LinkedHashMap<>();
        Map<String, SpecialistResult> resultIndex = new LinkedHashMap<>();
        List<String> skippedTasks = new ArrayList<>();
        boolean hasFailure = false;

        allThinkingSteps.add(new AnalysisResponse.ThinkingStep(0, "orchestrator",
                "编排计划: " + plan.tasks().size() + " 个任务, " + plan.executionPhases().size() + " 个阶段"));

        // 按阶段顺序执行
        for (ExecutionPhase phase : plan.executionPhases()) {
            List<OrchestrationTask> phaseTasks = plan.tasksByIds(phase.tasks());
            for (OrchestrationTask task : phaseTasks) {
                // 检查上游依赖
                List<String> blocking = collaborationManager.findBlockingDependencies(task, resultIndex);
                if (!blocking.isEmpty()) {
                    String blockingTaskIds = String.join(", ", blocking);
                    SpecialistResult skipResult = new SpecialistResult(task.taskId(), "编排器", false, "",
                            "上游依赖失败或缺失，跳过任务: " + blockingTaskIds, 0L,
                            Map.of("status", "SKIPPED"), List.of());
                    taskResults.add(skipResult);
                    resultIndex.put(task.taskId(), skipResult);
                    skippedTasks.add(task.taskId());
                    continue;
                }

                allThinkingSteps.add(new AnalysisResponse.ThinkingStep(
                        taskResults.size() + 1, "orchestrator_task",
                        "执行任务 " + task.taskId() + ": " + task.description()));

                // 执行任务，同时收集 thinking steps
                AnalysisResponse taskResponse = executeTaskWithResponse(task, decision, plan, request,
                        fileContent, sharedContext, resultIndex, memoryContext);
                if (taskResponse.getThinkingSteps() != null) {
                    allThinkingSteps.addAll(taskResponse.getThinkingSteps());
                }

                long elapsed = 0L;
                SpecialistResult taskResult;
                String specialistId = resolveTaskType(task).getDisplayName();
                if (taskResponse.isSuccess()) {
                    taskResult = new SpecialistResult(task.taskId(), specialistId, true,
                            taskResponse.getResult(), "", elapsed,
                            Map.of("final_answer", taskResponse.getResult()), List.of());
                } else {
                    taskResult = new SpecialistResult(task.taskId(), specialistId, false,
                            "", taskResponse.getError() != null ? taskResponse.getError() : "执行失败",
                            elapsed, Map.of(), List.of());
                }

                taskResults.add(taskResult);
                resultIndex.put(task.taskId(), taskResult);
                // 增量填充 sharedContext，使下游任务能通过 injectSharedContext 获取上游依赖
                Map<String, Object> taskContext = new LinkedHashMap<>();
                taskContext.put("result", taskResult.result());
                sharedContext.put(task.taskId(), taskContext);
                if (!taskResult.success()) {
                    hasFailure = true;
                }
            }
        }

        // 合并共享上下文
        sharedContext = collaborationManager.manageSharedContext(plan.tasks(), taskResults);
        if (!skippedTasks.isEmpty()) {
            sharedContext.put("skippedTasks", List.copyOf(skippedTasks));
        }
        // 调试日志：记录每个任务的执行结果
        if (LOGGER.isDebugEnabled()) {
            for (int i = 0; i < taskResults.size(); i++) {
                SpecialistResult r = taskResults.get(i);
                LOGGER.debug("任务结果[{}]: id={}, specialist={}, success={}, result={}, error={}", i, r.taskId(), r.specialistId(), r.success(), r.result(), r.error());
            }
        }

        // 确定选中的专家类型
        AgentType selectedType = determineSelectedType(plan, decision);
        boolean multiAgent = isMultiAgentPlan(plan);
        String selectedAgentName;
        String skillUsed;
        if (multiAgent) {
            // 多专家协作计划（涉及不同类型的专家）
            selectedAgentName = MULTI_AGENT_NAME;
            skillUsed = SKILL_USED_MULTI;
        } else if (decision.selectedProfile() != null) {
            // 单专家且匹配到配置
            selectedAgentName = decision.selectedProfile().getName();
            skillUsed = SKILL_USED_PREFIX + decision.selectedProfile().getName();
        } else {
            selectedAgentName = selectedType.getDisplayName();
            skillUsed = SKILL_USED_REACT;
        }

        // 整合最终答案
        AnalysisResponse lastResponse = findLastSuccessfulResponse(taskResults);
        lastResponse.setThinkingSteps(allThinkingSteps);
        OrchestratorExecutionResult execResult = new OrchestratorExecutionResult(
                taskResults, lastResponse, false, selectedType, selectedAgentName);
        String finalAnswer = resultIntegrator.integrate(request.getQuestion(), decision, execResult, sharedContext);
        lastResponse.setSkillUsed(skillUsed);
        Map<String, Object> integrationSummary = resultIntegrator.buildIntegrationSummary(execResult, sharedContext);
        Map<String, Object> executionMetadata = new LinkedHashMap<>();
        executionMetadata.put("integrationSummary", integrationSummary);
        lastResponse.setExecutionMetadata(executionMetadata);

        sharedContext.put("final_answer", finalAnswer);
        sharedContext.put("selected_agent", selectedAgentName);
        sharedContext.put("taskResults", taskResults);

        boolean success = !hasFailure && skippedTasks.isEmpty();
        return new OrchestratorResult(success, finalAnswer, selectedType, selectedAgentName,
                execResult, plan, sharedContext, intentResult);
    }

    /**
     * 执行单个编排任务，返回完整 AnalysisResponse（保留 thinkingSteps）。
     */
    private AnalysisResponse executeTaskWithResponse(OrchestrationTask task, OrchestratorDecision decision,
            OrchestrationPlan plan, AnalysisRequest request, String fileContent,
            Map<String, Object> sharedContext, Map<String, SpecialistResult> resultIndex,
            MemoryContext memoryContext) {
        try {
            AgentType taskType = resolveTaskType(task);
            AgentProfile taskProfile = resolveTaskProfile(task, decision, taskType);
            SpecialistTask specialistTask = collaborationManager.injectSharedContext(task, sharedContext, taskProfile);

            if (taskType == AgentType.REACT) {
                String enhancedContent = buildTaskFileContent(fileContent, task);
                return reActAgent.execute(request, enhancedContent);
            }
            AgentSpecialist specialist = specialistRegistry.findByType(taskType).orElse(null);
            if (specialist != null) {
                AgentExecutionRequest execRequest = new AgentExecutionRequest(taskProfile, request, fileContent,
                        specialistTask, memoryContext);
                return specialist.execute(execRequest);
            }
            String enhancedContent = buildTaskFileContent(fileContent, task);
            return reActAgent.execute(request, enhancedContent);
        } catch (Exception e) {
            LOGGER.warn("任务 {} 执行异常: {}", task.taskId(), e.getMessage());
            return AnalysisResponse.fail("任务执行异常: " + e.getMessage());
        }
    }

    /**
     * 解析任务绑定的 Agent 类型。
     */
    private AgentType resolveTaskType(OrchestrationTask task) {
        if (!StringUtils.hasText(task.specialistName())) {
            return AgentType.REACT;
        }
        try {
            return AgentType.fromCode(task.specialistName());
        } catch (Exception e) {
            return AgentType.REACT;
        }
    }

    /**
     * 解析任务的 Agent 配置。
     */
    private AgentProfile resolveTaskProfile(OrchestrationTask task, OrchestratorDecision decision,
            AgentType taskType) {
        if (decision.selectedProfile() != null && decision.selectedProfile().getType() == taskType) {
            return decision.selectedProfile();
        }
        AgentProfile profile = new AgentProfile();
        profile.setName(taskType.getDisplayName());
        profile.setType(taskType);
        return profile;
    }

    /**
     * 确定结构化结果中选中的 Agent 类型。
     */
    private AgentType determineSelectedType(OrchestrationPlan plan, OrchestratorDecision decision) {
        if (decision.selectedProfile() != null) {
            return decision.selectedProfile().getType();
        }
        if (!plan.tasks().isEmpty()) {
            return resolveTaskType(plan.tasks().get(0));
        }
        return AgentType.REACT;
    }

    /**
     * 判断计划是否涉及多种专家类型（需要多专家协作）。
     */
    private boolean isMultiAgentPlan(OrchestrationPlan plan) {
        if (plan == null || plan.tasks().size() <= 1) {
            return false;
        }
        long distinctTypes = plan.tasks().stream()
                .map(task -> resolveTaskType(task))
                .distinct()
                .count();
        return distinctTypes > 1;
    }

    /**
     * 查找最后一个成功的响应，兜底返回空结果响应。
     */
    private AnalysisResponse findLastSuccessfulResponse(List<SpecialistResult> taskResults) {
        for (int i = taskResults.size() - 1; i >= 0; i--) {
            SpecialistResult r = taskResults.get(i);
            if (r.success() && StringUtils.hasText(r.result())) {
                return AnalysisResponse.ok(r.result());
            }
        }
        return AnalysisResponse.ok("");
    }

    /**
     * 构建增强的文件内容，供 ReAct 兜底使用。
     */
    private String buildReActFileContent(String fileContent, OrchestratorDecision decision) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(fileContent)) {
            sb.append(fileContent).append('\n');
        }
        if (decision.intentResult() != null) {
            sb.append("当前编排任务意图: ").append(decision.intentResult().intent()).append('\n');
        } else {
            sb.append("当前编排任务\n");
        }
        return sb.toString();
    }

    /**
     * 构建任务文件内容。
     */
    private String buildTaskFileContent(String fileContent, OrchestrationTask task) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(fileContent)) {
            sb.append(fileContent).append('\n');
        }
        sb.append("当前编排任务: ").append(task.description()).append('\n');
        sb.append(task.taskId()).append(": ").append(task.description());
        return sb.toString();
    }

    /**
     * 构建记忆上下文。
     */
    private MemoryContext buildMemoryContext(AnalysisRequest request) {
        try {
            String sessionId = request.getSessionId() != null ? request.getSessionId() : "";
            return memoryManager.buildContext(sessionId, request.getQuestion());
        } catch (Exception e) {
            LOGGER.warn("构建记忆上下文失败: {}", e.getMessage());
            return MemoryContext.empty();
        }
    }
}
