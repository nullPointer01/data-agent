package com.ai.agent.durable;

import com.ai.agent.runtime.AgentRunContext;
import com.ai.agent.tool.governance.AgentToolInvocationContext;
import dev.langchain4j.data.message.ChatMessage;

import java.util.List;
import java.util.ArrayList;

/**
 * 从持久化 Checkpoint 重建的纯服务端恢复现场。
 *
 * @author data-agent
 */
public record RestoredAgentRun(
        AgentRunContext context,
        AgentRunCheckpoint checkpoint,
        List<ChatMessage> messages,
        AgentToolInvocationContext invocationContext) {

    public RestoredAgentRun {
        messages = new ArrayList<>(messages);
    }
}
