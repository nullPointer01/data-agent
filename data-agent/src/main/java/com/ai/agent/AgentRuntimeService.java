package com.ai.agent;

import com.ai.agent.react.ReActStreamEventWriter;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.model.ConversationSession;
import com.ai.service.MultiAgentRuntimeService;
import com.ai.skill.SkillManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import com.ai.agent.orchestrator.OrchestratorAgent;
import com.ai.agent.orchestrator.OrchestratorResult;
import com.ai.agent.react.ReActAgent;
import com.ai.agent.tool.AgentConversationRecorder;
import com.ai.service.AgentExecutionTraceService;

import java.util.function.Consumer;

/**
 * 将准备好的请求路由（routing）到命令、技能、多 Agent 或 ReAct 执行器。
 *
 * @author data-agent
 */
@Service
public class AgentRuntimeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentRuntimeService.class);
    private static final String COMMAND_SKILL_NAME = "command";

    private final SkillManager skillManager;
    private final ReActAgent reActAgent;
    private final MultiAgentRuntimeService multiAgentRuntimeService;
    private final OrchestratorAgent orchestratorAgent;
    private final SkillExecutionService skillExecutionService;
    private final AgentConversationRecorder conversationRecorder;
    private final AgentExecutionTraceService traceService;
    private final ReActStreamEventWriter streamEventWriter;

    @Autowired
    public AgentRuntimeService(SkillManager skillManager,
            ReActAgent reActAgent,
            MultiAgentRuntimeService multiAgentRuntimeService,
            @Nullable OrchestratorAgent orchestratorAgent,
            SkillExecutionService skillExecutionService,
            AgentConversationRecorder conversationRecorder,
            @Nullable AgentExecutionTraceService traceService,
            ReActStreamEventWriter streamEventWriter) {
        this.skillManager = skillManager;
        this.reActAgent = reActAgent;
        this.multiAgentRuntimeService = multiAgentRuntimeService;
        this.orchestratorAgent = orchestratorAgent;
        this.skillExecutionService = skillExecutionService;
        this.conversationRecorder = conversationRecorder;
        this.traceService = traceService;
        this.streamEventWriter = streamEventWriter;
    }

    /**
     * 将单个已准备的请求路由到命令、已配置 Agent、技能或 ReAct 执行。
     *
     * @param context 已准备的执行上下文
     * @return 分析响应
     */
    public AnalysisResponse execute(AgentExecutionContext context) {
        AnalysisRequest request = context.getRequest();
        String fileContent = context.getFileContent();
        ConversationSession session = context.getSession();

        if (request.isCommand()) {
            return executeCommand(request, fileContent, session);
        }
        if (request.hasAgent()) {
            AnalysisResponse agentResponse = multiAgentRuntimeService.execute(request.getAgentId(), request, fileContent);
            if (session != null && agentResponse != null) {
                // 持久化由配置的 Agent 返回的会话记录
                conversationRecorder.recordAnalysisConversation(session, request, agentResponse.getResult(),
                        agentResponse.getSkillUsed(), null);
                agentResponse.setSessionId(session.getSessionId());
            }
            return agentResponse;
        }
        if (request.hasSkill()) {
            AnalysisResponse response = skillExecutionService.execute(request, fileContent, session);
            if (response != null) {
                return response;
            }
        }
        // 优先使用编排器（Orchestrator）当可用时，以支持多专家协作与结构化执行
        if (orchestratorAgent != null) {
            OrchestratorResult orchestratorResult = orchestratorAgent.executeStructured(request, fileContent, session);
            AnalysisResponse resp = orchestratorResult.executionResult().response();
            LOGGER.info("Orchestrator response thinkingSteps={}", resp.getThinkingSteps() == null ? 0 : resp.getThinkingSteps().size());
            // 记录执行轨迹（如果支持）
            if (traceService != null) {
                String traceId = traceService.record(request, session, orchestratorResult);
                resp.setTraceId(traceId);
            }
            // 持久化会话会话对话
            if (session != null) {
                conversationRecorder.recordAnalysisConversation(session, request, resp.getResult(), "orchestrator", null);
            }
            return resp;
        }
        return reActAgent.execute(request, fileContent);
    }

    /**
     * 流式执行：ReAct 走真流式，其他路径走同步+事后发事件。
     *
     * @param context 已准备的执行上下文
     * @param eventEmitter SSE 事件消费者
     */
    public void executeStreaming(AgentExecutionContext context, Consumer<String> eventEmitter) {
        AnalysisRequest request = context.getRequest();
        String fileContent = context.getFileContent();
        ConversationSession session = context.getSession();

        // 斜杠命令、Agent、Skill 走同步再发事件
        if (request.isCommand() || request.hasAgent() || request.hasSkill()) {
            AnalysisResponse response = execute(context);
            emitResponseAsStream(response, eventEmitter);
            return;
        }

        // 默认走 ReAct 真流式（ReActAgent.executeStreaming 内部已处理 done 事件）
        reActAgent.executeStreaming(request, fileContent, eventEmitter);
    }

    /** 把同步响应转为流式事件发送。 */
    private void emitResponseAsStream(AnalysisResponse response, Consumer<String> eventEmitter) {
        if (response == null) {
            streamEventWriter.emitError(eventEmitter, "分析结果为空");
            return;
        }
        // 发送 thinkingSteps
        if (response.getThinkingSteps() != null) {
            for (AnalysisResponse.ThinkingStep step : response.getThinkingSteps()) {
                String content = step.getToolResult() != null ? step.getToolResult() : step.getContent();
                if (content == null || content.isBlank()) {
                    continue;
                }
                String type = step.getType();
                if ("reflection".equals(type)) {
                    streamEventWriter.emitReflection(eventEmitter, content);
                } else if ("orchestrator".equals(type) || "orchestrator_task".equals(type)) {
                    streamEventWriter.emitOrchestration(eventEmitter,
                            "orchestrator".equals(type) ? "编排决策" : "编排任务 " + step.getStep(), content);
                } else if ("plan".equals(type)) {
                    streamEventWriter.emitExecutionPlan(eventEmitter, content);
                } else if ("parallel_precheck".equals(type)) {
                    streamEventWriter.emitParallelPrecheck(eventEmitter, content);
                } else {
                    String toolName = step.getToolName() != null ? step.getToolName() : type;
                    streamEventWriter.emitToolCall(eventEmitter, toolName, content);
                }
            }
        }
        // 发送最终结果
        if (response.isSuccess()) {
            streamEventWriter.emitToken(eventEmitter, response.getResult() != null ? response.getResult() : "");
        } else {
            streamEventWriter.emitError(eventEmitter, response.getError() != null ? response.getError() : "分析失败");
        }
        String sessionId = response.getSessionId() != null ? response.getSessionId() : "";
        streamEventWriter.emitDone(eventEmitter, sessionId, response.getTraceId());
    }

    private AnalysisResponse executeCommand(AnalysisRequest request, String fileContent, ConversationSession session) {
        String commandResult = skillManager.processWithCommand(request.getQuestion(), fileContent);
        if (commandResult == null) {
            // 未知的斜杠命令视为普通用户输入，让 Agent 继续回答
            return reActAgent.execute(request, fileContent);
        }

        if (session != null) {
            conversationRecorder.recordSessionConversation(session, request, commandResult, COMMAND_SKILL_NAME, null);
        }
        AnalysisResponse response = AnalysisResponse.ok(commandResult);
        response.setSkillUsed(COMMAND_SKILL_NAME);
        if (session != null) {
            response.setSessionId(session.getSessionId());
        }
        return response;
    }
}
