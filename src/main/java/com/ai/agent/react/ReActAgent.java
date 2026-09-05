package com.ai.agent.react;

import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.model.ConversationSession;
import com.ai.service.SessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import com.ai.agent.AgentReasoningProperties;
import com.ai.agent.AgentSystemPrompts;
import com.ai.agent.TaskClassification;
import com.ai.agent.TaskComplexityClassifier;
import com.ai.memory.dto.MemoryContext;
import com.ai.agent.orchestrator.ExecutionPlan;
import com.ai.agent.orchestrator.ParallelPlanExecutionResult;
import com.ai.agent.orchestrator.ParallelPlanExecutor;
import com.ai.agent.orchestrator.ParallelPlanStepResult;
import com.ai.agent.orchestrator.TaskPlanner;
import com.ai.agent.tool.AgentToolInvoker;
import com.ai.agent.tool.governance.AgentToolInvocationContext;
import com.ai.agent.tool.governance.AgentToolInvocationContextFactory;
import com.ai.memory.MemoryManager;

/**
 * ReAct 风格的数据分析智能体（Agent）。
 *
 * @author data-agent
 */
@Component
public class ReActAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReActAgent.class);
    private static final String ROLE_USER = "user";
    // 默认 ReAct Agent 的 System Prompt = 角色层 + 框架基座（见 AgentSystemPrompts）。
    // 不再硬编码"思考-行动-观察"教导（现代模型天生会 ReAct，过度教导反而诱导其"先写思考文本再调工具"
    // 从而出现 intent without action）；工具调用纪律统一收敛到框架基座，与配置 Agent 共享。
    private static final String SYSTEM_PROMPT = AgentSystemPrompts.DEFAULT_REACT;

    private final SessionManager sessionManager;
    private final AgentToolInvoker toolInvoker;
    private final ReActRequestContextBuilder requestContextBuilder;
    private final ReActStreamEventWriter streamEventWriter;
    private final ReActLoopRunner loopRunner;
    private final ReActFastPathDecider fastPathDecider;
    private final ReActFastAnswerService fastAnswerService;
    private final TaskPlanner taskPlanner;
    private final ParallelPlanExecutor parallelPlanExecutor;
    private final AgentReasoningProperties reasoningProperties;
    private final MemoryManager memoryManager;
    private final ReActMetadataBuilder metadataBuilder;
    private final AgentToolInvocationContextFactory toolInvocationContextFactory;

    @Autowired
    public ReActAgent(SessionManager sessionManager,
            AgentToolInvoker toolInvoker,
            ReActRequestContextBuilder requestContextBuilder,
            ReActStreamEventWriter streamEventWriter,
            ReActLoopRunner loopRunner,
            @Nullable ReActFastPathDecider fastPathDecider,
            @Nullable ReActFastAnswerService fastAnswerService,
            @Nullable TaskPlanner taskPlanner,
            @Nullable ParallelPlanExecutor parallelPlanExecutor,
            @Nullable AgentReasoningProperties reasoningProperties,
            @Nullable MemoryManager memoryManager,
            @Nullable ReActMetadataBuilder metadataBuilder,
            AgentToolInvocationContextFactory toolInvocationContextFactory) {
        this.sessionManager = sessionManager;
        this.toolInvoker = toolInvoker;
        this.requestContextBuilder = requestContextBuilder;
        this.streamEventWriter = streamEventWriter;
        this.loopRunner = loopRunner;
        this.fastPathDecider = fastPathDecider;
        this.fastAnswerService = fastAnswerService;
        this.taskPlanner = taskPlanner;
        this.parallelPlanExecutor = parallelPlanExecutor;
        this.reasoningProperties = reasoningProperties;
        this.memoryManager = memoryManager;
        this.metadataBuilder = metadataBuilder;
        this.toolInvocationContextFactory = toolInvocationContextFactory;
    }

    /**
     * 执行 ReAct 循环并返回完整的分析响应。
     *
     * @param request 分析请求
     * @param fileContent 可选的上传文件内容
     * @return 分析响应
     */
    public AnalysisResponse execute(AnalysisRequest request, String fileContent) {
        String modelId = request.hasModel() ? request.getModelId() : null;
        ConversationSession session = getSession(request);
        ReActRequestContext requestContext = requestContextBuilder.build(request, fileContent);

        // 自主模式：跳过分类/快路/规划/预检，直接进循环，决策权全交模型
        if (reasoningProperties != null && reasoningProperties.isAutonomousMode()) {
            return executeAutonomous(request, fileContent, requestContext, session, modelId);
        }

        // 任务分类（用于判断是否可直答）
        TaskClassification classification = new TaskComplexityClassifier().classify(request, fileContent, session);

        // 先尝试 Fast Path（直答）
        if (reasoningProperties == null || reasoningProperties.isFastPathEnabled()) {
            if (classification.fastPathAllowed() && fastAnswerService != null && fastAnswerService != null
                    && reasoningProperties != null && reasoningProperties.isFastPathEnabled()) {
                MemoryContext memoryContext = memoryManager == null ? MemoryContext.empty()
                        : memoryManager.buildContext(session == null ? "" : session.getSessionId(), request.getQuestion());
                List<AnalysisResponse.ThinkingStep> thinkingSteps = new ArrayList<>();
                String answer = fastAnswerService.answer(request.getQuestion(), fileContent, memoryContext);
                AnalysisResponse response = AnalysisResponse.ok(answer);
                response.setSkillUsed("fast-answer");
                response.setThinkingSteps(thinkingSteps);
                if (session != null) {
                    response.setSessionId(session.getSessionId());
                }
                if (metadataBuilder != null) {
                    response.setExecutionMetadata(metadataBuilder.buildFastPathMetadata(classification, thinkingSteps, memoryContext));
                }
                return response;
            }
        }

        // 规划阶段
        ReActExecutionResult executionResult;
        List<AnalysisResponse.ThinkingStep> thinkingSteps = new ArrayList<>();
        ExecutionPlan executionPlan;
        if (reasoningProperties != null && !reasoningProperties.isPlanningEnabled()) {
            executionPlan = taskPlanner.passthrough(classification);
        } else {
            executionPlan = taskPlanner.plan(request, requestContext, classification);
        }

        // 并行预检（如可用）
        ParallelPlanExecutionResult precheck = null;
        if (parallelPlanExecutor != null && reasoningProperties != null && reasoningProperties.isParallelPrecheckEnabled()) {
            precheck = parallelPlanExecutor.execute(executionPlan, fileContent == null ? "" : fileContent);
            if (precheck != null && precheck.stepResults() != null && !precheck.stepResults().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (ParallelPlanStepResult step : precheck.stepResults()) {
                    sb.append(step.result()).append("; ");
                }
                thinkingSteps.add(new AnalysisResponse.ThinkingStep(0, "parallel_precheck", sb.toString()));
            }
        }

        List<ToolSpecification> toolSpecs = toolInvoker.buildToolSpecifications();
        AgentToolInvocationContext toolContext = toolInvocationContextFactory.defaultReact();
        List<ChatMessage> messages = buildInitialMessages(session, requestContext);
        String sessionId = session != null ? session.getSessionId() : null;
        executionResult = loopRunner.run(messages, toolSpecs, modelId, sessionId, requestContext.userQuery(),
                thinkingSteps, toolContext);

        String result = executionResult.answer();
        AnalysisResponse response = executionResult.success()
                ? AnalysisResponse.ok(result)
                : AnalysisResponse.fail(result);
        response.setApprovalId(executionResult.approvalId());
        response.setSkillUsed("react-agent");
        response.setThinkingSteps(thinkingSteps);
        if (session != null) {
            response.setSessionId(session.getSessionId());
        }
        if (metadataBuilder != null) {
            response.setExecutionMetadata(metadataBuilder.buildReasoningMetadata(classification, executionPlan, precheck, executionResult, requestContext, thinkingSteps));
        }
        return response;
    }

    /**
     * 以流式方式向调用方发送 ReAct 事件。
     *
     * @param request 分析请求
     * @param fileContent 可选的上传文件内容
     * @param eventEmitter JSON 事件消费者
     * @return 完整分析响应
     */
    public AnalysisResponse executeStreaming(AnalysisRequest request, String fileContent, Consumer<String> eventEmitter) {
        String modelId = request.hasModel() ? request.getModelId() : null;
        ConversationSession session = getSession(request);
        final ConversationSession finalSession = session;

        ReActRequestContext requestContext = requestContextBuilder.build(request, fileContent);
        if (requestContext.ragContext().hasContext()) {
            streamEventWriter.emitRagContext(eventEmitter, requestContext.ragContext().getHitCount());
        }

        // 自主模式：跳过分类/快路/规划/预检，直接进循环，决策权全交模型
        if (reasoningProperties != null && reasoningProperties.isAutonomousMode()) {
            return executeStreamingAutonomous(request, fileContent, requestContext, finalSession, eventEmitter);
        }

        // 任务分类（用于判断是否可直答）
        TaskClassification classification = new TaskComplexityClassifier().classify(request, fileContent, session);

        // 尝试 Fast Path 流式直答
        if (reasoningProperties == null || reasoningProperties.isFastPathEnabled()) {
            if (classification.fastPathAllowed() && fastAnswerService != null && reasoningProperties != null && reasoningProperties.isFastPathEnabled()) {
                MemoryContext memoryContext = memoryManager == null ? MemoryContext.empty()
                        : memoryManager.buildContext(session == null ? "" : session.getSessionId(), request.getQuestion());
                try {
                    // fastAnswerService 会把 token 逐个回调到给定的 Consumer
                    String finalAnswer = fastAnswerService.answerStreaming(request.getQuestion(), modelId, memoryContext, token -> {
                        // 把 token 包装为 stream 事件
                        streamEventWriter.emitToken(eventEmitter, token);
                    });
                    AnalysisResponse response = AnalysisResponse.ok(finalAnswer);
                    response.setSkillUsed("fast-answer");
                    if (finalSession != null) {
                        response.setSessionId(finalSession.getSessionId());
                    }
                    return response;
                } catch (Exception e) {
                    LOGGER.error("快速直答流式输出出错", e);
                    streamEventWriter.emitError(eventEmitter, "分析出错: " + e.getMessage());
                    return AnalysisResponse.fail("分析出错: " + e.getMessage());
                }
            }
        }

        // 规划与并行预检
        ExecutionPlan executionPlan;
        if (reasoningProperties != null && !reasoningProperties.isPlanningEnabled()) {
            executionPlan = taskPlanner.passthrough(classification);
        } else {
            executionPlan = taskPlanner.plan(request, requestContext, classification);
            // 将执行计划通过流式事件发送
            if (executionPlan != null) {
                streamEventWriter.emitExecutionPlan(eventEmitter, executionPlan.toString());
            }
        }

        ParallelPlanExecutionResult precheck = null;
        if (parallelPlanExecutor != null && reasoningProperties != null && reasoningProperties.isParallelPrecheckEnabled()) {
            precheck = parallelPlanExecutor.execute(executionPlan, fileContent == null ? "" : fileContent);
            if (precheck != null && precheck.stepResults() != null && !precheck.stepResults().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (var step : precheck.stepResults()) {
                    sb.append(step.result()).append("; ");
                }
                streamEventWriter.emitParallelPrecheck(eventEmitter, sb.toString());
            }
        }

        List<ToolSpecification> toolSpecs = toolInvoker.buildToolSpecifications();
        AgentToolInvocationContext toolContext = toolInvocationContextFactory.defaultReact();
        List<ChatMessage> messages = buildInitialMessages(session, requestContext);

        try {
            String sessionId = finalSession != null ? finalSession.getSessionId() : null;
            ReActExecutionResult execResult = loopRunner.runStreaming(messages, toolSpecs, modelId, sessionId,
                    requestContext.userQuery(), eventEmitter, toolContext);
            AnalysisResponse response = execResult.success()
                    ? AnalysisResponse.ok(execResult.answer())
                    : AnalysisResponse.fail(execResult.answer());
            response.setApprovalId(execResult.approvalId());
            response.setSkillUsed("react-agent");
            response.setSessionId(sessionId);
            return response;
        } catch (Exception e) {
            LOGGER.error("流式 ReAct 执行出错", e);
            streamEventWriter.emitError(eventEmitter, "分析出错: " + e.getMessage());
            return AnalysisResponse.fail("分析出错: " + e.getMessage());
        }
    }

    /**
     * 自主模式同步执行：不分类、不规划、不预检，构建消息后直接进循环。
     *
     * <p>与编排式 {@link #execute} 的差别在于——这里模型从第一轮就握有全部工具，
     * "用不用、用哪个、几轮、何时停"全由模型自己决定，而非代码预先裁决。</p>
     */
    private AnalysisResponse executeAutonomous(AnalysisRequest request, String fileContent,
            ReActRequestContext requestContext, ConversationSession session, String modelId) {
        List<ToolSpecification> toolSpecs = toolInvoker.buildToolSpecifications();
        AgentToolInvocationContext toolContext = toolInvocationContextFactory.defaultReact();
        List<ChatMessage> messages = buildInitialMessages(session, requestContext);
        String sessionId = session != null ? session.getSessionId() : null;
        List<AnalysisResponse.ThinkingStep> thinkingSteps = new ArrayList<>();
        LOGGER.info("Autonomous sync execute | 模型全权决策 | tools={}", toolSpecs.size());
        ReActExecutionResult executionResult = loopRunner.run(messages, toolSpecs, modelId, sessionId,
                requestContext.userQuery(), thinkingSteps, toolContext);
        String result = executionResult.answer();
        AnalysisResponse response = executionResult.success()
                ? AnalysisResponse.ok(result)
                : AnalysisResponse.fail(result);
        response.setApprovalId(executionResult.approvalId());
        response.setSkillUsed("react-agent-autonomous");
        response.setThinkingSteps(thinkingSteps);
        if (session != null) {
            response.setSessionId(session.getSessionId());
        }
        return response;
    }

    /**
     * 自主模式流式执行：构建消息后直接进流式循环，全部工具暴露给模型。
     */
    private AnalysisResponse executeStreamingAutonomous(AnalysisRequest request, String fileContent,
            ReActRequestContext requestContext, ConversationSession session, Consumer<String> eventEmitter) {
        String modelId = request.hasModel() ? request.getModelId() : null;
        List<ToolSpecification> toolSpecs = toolInvoker.buildToolSpecifications();
        AgentToolInvocationContext toolContext = toolInvocationContextFactory.defaultReact();
        List<ChatMessage> messages = buildInitialMessages(session, requestContext);
        String sessionId = session != null ? session.getSessionId() : null;
        LOGGER.info("Autonomous streaming execute | 模型全权决策 | tools={}", toolSpecs.size());
        try {
            ReActExecutionResult execResult = loopRunner.runStreaming(messages, toolSpecs, modelId, sessionId,
                    requestContext.userQuery(), eventEmitter, toolContext);
            AnalysisResponse response = execResult.success()
                    ? AnalysisResponse.ok(execResult.answer())
                    : AnalysisResponse.fail(execResult.answer());
            response.setApprovalId(execResult.approvalId());
            response.setSkillUsed("react-agent-autonomous");
            response.setSessionId(sessionId);
            return response;
        } catch (Exception e) {
            LOGGER.error("自主流式执行出错", e);
            streamEventWriter.emitError(eventEmitter, "分析出错: " + e.getMessage());
            return AnalysisResponse.fail("分析出错: " + e.getMessage());
        }
    }

    /**
     * 解析受租户保护的会话。SessionManager 会执行归属权校验。
     */
    private ConversationSession getSession(AnalysisRequest request) {
        if (request.hasSession()) {
            return sessionManager.getSession(request.getSessionId());
        }
        return null;
    }

    /**
     * 构建 ReAct 初始消息，在会话上下文可用时保留最近的对话历史。
     */
    private List<ChatMessage> buildInitialMessages(ConversationSession session, ReActRequestContext requestContext) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(SYSTEM_PROMPT));
        if (session != null) {
            // 按角色还原历史，让模型区分用户与助手发言，而非压成单条 UserMessage 丢失角色结构
            for (ConversationSession.Message message : session.getHistory()) {
                String content = message.getContent();
                if (content == null || content.isBlank()) {
                    continue;
                }
                if (ROLE_USER.equals(message.getRole())) {
                    messages.add(UserMessage.from(content));
                } else {
                    messages.add(AiMessage.from(content));
                }
            }
        }
        messages.add(UserMessage.from(requestContext.userQuery()));
        return messages;
    }
}
