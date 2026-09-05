package com.ai.agent.runtime;

import com.ai.agent.SkillExecutionService;
import com.ai.model.ConversationSession;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.service.MultiAgentRuntimeService;
import com.ai.skill.SkillManager;
import org.springframework.stereotype.Component;

/**
 * 执行配置 Agent 的纯对话模式。
 *
 * @author data-agent
 */
@Component
public class ChatExecutionStrategy implements AgentExecutionStrategy {

    private static final String COMMAND_SKILL_NAME = "command";

    private final MultiAgentRuntimeService multiAgentRuntimeService;
    private final SkillExecutionService skillExecutionService;
    private final SkillManager skillManager;

    public ChatExecutionStrategy(MultiAgentRuntimeService multiAgentRuntimeService,
            SkillExecutionService skillExecutionService,
            SkillManager skillManager) {
        this.multiAgentRuntimeService = multiAgentRuntimeService;
        this.skillExecutionService = skillExecutionService;
        this.skillManager = skillManager;
    }

    @Override
    public AgentExecutionMode mode() {
        return AgentExecutionMode.CHAT;
    }

    @Override
    public Result execute(AgentRunContext context, AgentRunRoute route) {
        AnalysisRequest request = context.executionContext().getRequest();
        String fileContent = context.executionContext().getFileContent();
        ConversationSession session = context.executionContext().getSession();
        AnalysisResponse response = switch (route.target()) {
            case COMMAND -> executeCommand(request, fileContent);
            case SKILL -> skillExecutionService.execute(request, fileContent, session);
            case CONFIGURED_AGENT -> executeConfiguredAgent(route, request, fileContent);
            case DEFAULT -> throw new IllegalStateException("默认路线不能使用 Chat 执行策略");
        };
        if (response == null) {
            throw new IllegalStateException("Chat 执行结果为空");
        }
        return new Result(response, false);
    }

    private AnalysisResponse executeCommand(AnalysisRequest request, String fileContent) {
        String commandResult = skillManager.processWithCommand(request.getQuestion(), fileContent);
        if (commandResult == null) {
            throw new IllegalArgumentException("无法识别命令: " + request.getQuestion());
        }
        AnalysisResponse response = AnalysisResponse.ok(commandResult);
        response.setSkillUsed(COMMAND_SKILL_NAME);
        return response;
    }

    private AnalysisResponse executeConfiguredAgent(AgentRunRoute route, AnalysisRequest request,
            String fileContent) {
        if (route.profile() == null) {
            throw new IllegalStateException("Chat 执行策略缺少 AgentProfile");
        }
        return multiAgentRuntimeService.execute(route.profile(), request, fileContent);
    }
}
