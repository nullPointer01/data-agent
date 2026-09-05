package com.ai.agent.durable;

import com.ai.agent.AgentExecutionContext;
import com.ai.agent.runtime.AgentRunContext;
import com.ai.agent.runtime.event.AgentEventSink;
import com.ai.agent.tool.governance.AgentToolAuthorizationSnapshot;
import com.ai.agent.tool.governance.AgentToolExecutionJournal;
import com.ai.agent.tool.governance.AgentToolGovernanceProperties;
import com.ai.agent.tool.governance.AgentToolInvocationContext;
import com.ai.model.AnalysisRequest;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 仅从加密服务端状态和当前授权结果重建可执行 RunContext。
 *
 * @author data-agent
 */
@Component
public class AgentCheckpointRestorer {

    private final AgentCheckpointCodec checkpointCodec;
    private final AgentCheckpointMessageMapper messageMapper;
    private final AgentRunControlFactory controlFactory;
    private final AgentToolGovernanceProperties toolProperties;

    public AgentCheckpointRestorer(AgentCheckpointCodec checkpointCodec,
            AgentCheckpointMessageMapper messageMapper,
            AgentRunControlFactory controlFactory,
            AgentToolGovernanceProperties toolProperties) {
        this.checkpointCodec = checkpointCodec;
        this.messageMapper = messageMapper;
        this.controlFactory = controlFactory;
        this.toolProperties = toolProperties;
    }

    public AgentRunCheckpoint decode(AgentRunStateEntity run) {
        if (run.getCheckpointSchemaVersion() == null) {
            throw new IllegalArgumentException("Agent Run 缺少 Checkpoint 版本");
        }
        return checkpointCodec.decode(
                run.getCheckpointCiphertext(),
                run.getCheckpointSchemaVersion());
    }

    public RestoredAgentRun restore(AgentRunStateEntity run,
            AgentRunCheckpoint checkpoint,
            AgentToolAuthorizationSnapshot ownerAuthorization) {
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion(lastUserMessage(checkpoint));
        request.setModelId(checkpoint.modelId());
        request.setSessionId(run.getSessionId());
        request.setAgentId(run.getAgentId());
        AgentExecutionContext executionContext = new AgentExecutionContext(request, null, null);
        Set<String> currentAllowedTools = checkpoint.allowedToolsAtRequest().stream()
                .filter(ownerAuthorization.serverEnabledTools()::contains)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        AgentToolInvocationContext invocationContext = new AgentToolInvocationContext(
                checkpoint.executorId(), currentAllowedTools);
        AgentRunContext context = new AgentRunContext(
                run.getRunId(),
                run.getTenantId(),
                run.getUserId(),
                run.getSessionId(),
                run.getAgentId(),
                run.getMode(),
                executionContext,
                controlFactory.restore(checkpoint.budget()),
                AgentEventSink.noop(),
                ownerAuthorization,
                new AgentToolExecutionJournal(toolProperties.getJournalCapacity()));
        return new RestoredAgentRun(
                context,
                checkpoint,
                messageMapper.restore(checkpoint.messages()),
                invocationContext);
    }

    private String lastUserMessage(AgentRunCheckpoint checkpoint) {
        for (int index = checkpoint.messages().size() - 1; index >= 0; index--) {
            AgentCheckpointMessage message = checkpoint.messages().get(index);
            if (message.role() == AgentCheckpointMessage.Role.USER) {
                return message.content();
            }
        }
        return "继续执行已批准的工具动作";
    }
}
