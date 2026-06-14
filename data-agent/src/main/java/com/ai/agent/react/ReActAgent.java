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
import com.ai.agent.TaskClassification;
import com.ai.agent.TaskComplexityClassifier;
import com.ai.memory.dto.MemoryContext;
import com.ai.agent.orchestrator.ExecutionPlan;
import com.ai.agent.orchestrator.ParallelPlanExecutionResult;
import com.ai.agent.orchestrator.ParallelPlanExecutor;
import com.ai.agent.orchestrator.ParallelPlanStepResult;
import com.ai.agent.orchestrator.TaskPlanner;
import com.ai.agent.tool.AgentConversationRecorder;
import com.ai.agent.tool.AgentToolInvoker;
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
    private static final String SYSTEM_PROMPT = """
            你是一个强大的数据分析智能体(Agent)，拥有多种工具来辅助完成用户的数据分析任务。

            ## 工作模式 (ReAct: 思考-行动-观察)

            对于每个用户请求，你需要:
            1. **思考(Thought)**: 分析用户意图，规划解决步骤
            2. **行动(Action)**: 选择并调用合适的工具
            3. **观察(Observation)**: 分析工具返回的结果
            4. 重复上述步骤直到获得足够信息
            5. **最终回答**: 整合所有信息，给出清晰专业的回答

            ## 核心原则
            - 绝不编造数据。有数据则基于数据分析；无数据则请求用户提供
            - 需要计算时使用 calculate 工具，不要心算
            - 对文件数据先用 analyzeFileData 获取概览，再深入分析
            - 系统可能会提供“检索上下文”，这是从向量库召回的企业资料。优先基于检索上下文回答；如果上下文不足，再调用工具或说明需要补充资料
            - 搜索相关知识时可以同时使用 searchMemory 和 searchKnowledge
            - 每次只调用一个工具
            - 回答应结构清晰、专业简洁，可使用 Markdown 格式

            ## 输出格式
            思考和工具调用时，先写出你的思考过程，然后调用工具:
            [思考] 用户想要...，我需要先...
            [CALL:工具名(参数)]

            最终回答时，直接给出完整答案，不要包含工具调用语法。
            """;

    private final SessionManager sessionManager;
    private final AgentToolInvoker toolInvoker;
    private final ReActRequestContextBuilder requestContextBuilder;
    private final AgentConversationRecorder conversationRecorder;
    private final ReActStreamEventWriter streamEventWriter;
    private final ReActLoopRunner loopRunner;
    private final ReActFastPathDecider fastPathDecider;
    private final ReActFastAnswerService fastAnswerService;
    private final TaskPlanner taskPlanner;
    private final ParallelPlanExecutor parallelPlanExecutor;
    private final AgentReasoningProperties reasoningProperties;
    private final MemoryManager memoryManager;
    private final ReActMetadataBuilder metadataBuilder;

    @Autowired
    public ReActAgent(SessionManager sessionManager,
            AgentToolInvoker toolInvoker,
            ReActRequestContextBuilder requestContextBuilder,
            AgentConversationRecorder conversationRecorder,
            ReActStreamEventWriter streamEventWriter,
            ReActLoopRunner loopRunner,
            @Nullable ReActFastPathDecider fastPathDecider,
            @Nullable ReActFastAnswerService fastAnswerService,
            @Nullable TaskPlanner taskPlanner,
            @Nullable ParallelPlanExecutor parallelPlanExecutor,
            @Nullable AgentReasoningProperties reasoningProperties,
            @Nullable MemoryManager memoryManager,
            @Nullable ReActMetadataBuilder metadataBuilder) {
        this.sessionManager = sessionManager;
        this.toolInvoker = toolInvoker;
        this.requestContextBuilder = requestContextBuilder;
        this.conversationRecorder = conversationRecorder;
        this.streamEventWriter = streamEventWriter;
        this.loopRunner = loopRunner;
        this.fastPathDecider = fastPathDecider;
        this.fastAnswerService = fastAnswerService;
        this.taskPlanner = taskPlanner;
        this.parallelPlanExecutor = parallelPlanExecutor;
        this.reasoningProperties = reasoningProperties;
        this.memoryManager = memoryManager;
        this.metadataBuilder = metadataBuilder;
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
                conversationRecorder.recordSessionConversation(session, request, answer, "fast-answer", null);
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
        List<ChatMessage> messages = buildInitialMessages(session, requestContext);
        String sessionId = session != null ? session.getSessionId() : null;
        executionResult = loopRunner.run(messages, toolSpecs, modelId, sessionId, requestContext.userQuery(), thinkingSteps);

        String result = executionResult.answer();
        conversationRecorder.recordReActConversation(session, request, result, modelId);

        AnalysisResponse response = AnalysisResponse.ok(result);
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
     */
    public void executeStreaming(AnalysisRequest request, String fileContent, Consumer<String> eventEmitter) {
        String modelId = request.hasModel() ? request.getModelId() : null;
        ConversationSession session = getSession(request);
        final ConversationSession finalSession = session;

        ReActRequestContext requestContext = requestContextBuilder.build(request, fileContent);
        if (requestContext.ragContext().hasContext()) {
            streamEventWriter.emitRagContext(eventEmitter, requestContext.ragContext().getHitCount());
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
                    conversationRecorder.recordSessionConversation(finalSession, request, finalAnswer, "fast-answer", null);
                    streamEventWriter.emitDone(eventEmitter, finalSession != null ? finalSession.getSessionId() : null);
                } catch (Exception e) {
                    LOGGER.error("快速直答流式输出出错", e);
                    streamEventWriter.emitError(eventEmitter, "分析出错: " + e.getMessage());
                }
                return;
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
        List<ChatMessage> messages = buildInitialMessages(session, requestContext);

        try {
            String sessionId = finalSession != null ? finalSession.getSessionId() : null;
            String result = loopRunner.runStreaming(messages, toolSpecs, modelId, sessionId,
                    requestContext.userQuery(), eventEmitter);
            conversationRecorder.recordReActConversation(finalSession, request, result, modelId);
            streamEventWriter.emitDone(eventEmitter, finalSession != null ? finalSession.getSessionId() : null);
        } catch (Exception e) {
            LOGGER.error("流式 ReAct 执行出错", e);
            streamEventWriter.emitError(eventEmitter, "分析出错: " + e.getMessage());
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
