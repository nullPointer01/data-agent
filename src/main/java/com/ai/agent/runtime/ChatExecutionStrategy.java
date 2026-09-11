package com.ai.agent.runtime;

import com.ai.agent.SkillExecutionService;
import com.ai.model.ConversationSession;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.skill.SkillManager;
import org.springframework.stereotype.Component;

/**
 * 执行命令、显式 Skill 或配置化 Agent 的 Chat 模式。
 *
 * @author data-agent
 */
@Component
final class ChatExecutionStrategy implements AgentExecutionStrategy {

    private static final String COMMAND_SKILL_NAME = "command";

    private final ConfiguredAgentExecutionService configuredAgentExecutionService;
    private final SkillExecutionService skillExecutionService;
    private final SkillManager skillManager;

    ChatExecutionStrategy(ConfiguredAgentExecutionService configuredAgentExecutionService,
            SkillExecutionService skillExecutionService,
            SkillManager skillManager) {
        this.configuredAgentExecutionService = configuredAgentExecutionService;
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
            case CONFIGURED_AGENT -> configuredAgentExecutionService.execute(context, route);
        };
        if (response == null) {
            throw new IllegalStateException("Chat 执行结果为空");
        }
        boolean processEventsEmitted = route.target() == AgentRunRoute.Target.CONFIGURED_AGENT
                && context.eventSink().isStreaming();
        return new Result(response, processEventsEmitted);
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
}
