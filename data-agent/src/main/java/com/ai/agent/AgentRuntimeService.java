package com.ai.agent;

import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.model.ConversationSession;
import com.ai.service.MultiAgentRuntimeService;
import com.ai.skill.SkillManager;
import org.springframework.stereotype.Service;
import com.ai.agent.orchestrator.OrchestratorAgent;
import com.ai.agent.orchestrator.OrchestratorResult;
import com.ai.agent.react.ReActAgent;
import com.ai.agent.tool.AgentConversationRecorder;
import com.ai.service.AgentExecutionTraceService;

/**
 * Routes prepared requests to command, skill, multi-agent or ReAct executors.
 *
 * @author data-agent
 */
@Service
public class AgentRuntimeService {

    private static final String COMMAND_SKILL_NAME = "command";

    private final SkillManager skillManager;
    private final ReActAgent reActAgent;
    private final MultiAgentRuntimeService multiAgentRuntimeService;
    private final OrchestratorAgent orchestratorAgent;
    private final SkillExecutionService skillExecutionService;
    private final AgentConversationRecorder conversationRecorder;
    private final AgentExecutionTraceService traceService;

    public AgentRuntimeService(SkillManager skillManager,
            ReActAgent reActAgent,
            MultiAgentRuntimeService multiAgentRuntimeService,
            SkillExecutionService skillExecutionService,
            AgentConversationRecorder conversationRecorder) {
        this(skillManager, reActAgent, multiAgentRuntimeService, null, skillExecutionService,
                conversationRecorder, null);
    }

    public AgentRuntimeService(SkillManager skillManager,
            ReActAgent reActAgent,
            MultiAgentRuntimeService multiAgentRuntimeService,
            OrchestratorAgent orchestratorAgent,
            SkillExecutionService skillExecutionService,
            AgentConversationRecorder conversationRecorder,
            AgentExecutionTraceService traceService) {
        this.skillManager = skillManager;
        this.reActAgent = reActAgent;
        this.multiAgentRuntimeService = multiAgentRuntimeService;
        this.orchestratorAgent = orchestratorAgent;
        this.skillExecutionService = skillExecutionService;
        this.conversationRecorder = conversationRecorder;
        this.traceService = traceService;
    }

    /**
     * Routes one prepared request to command, configured agent, skill or ReAct execution.
     *
     * @param context prepared execution context
     * @return analysis response
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

    private AnalysisResponse executeCommand(AnalysisRequest request, String fileContent, ConversationSession session) {
        String commandResult = skillManager.processWithCommand(request.getQuestion(), fileContent);
        if (commandResult == null) {
            // Unknown slash commands are treated as normal user input so the agent can still answer.
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
