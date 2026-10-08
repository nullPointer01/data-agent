package com.ai.agent.durable;

import com.ai.agent.AgentChatToolService;
import com.ai.agent.react.ReActExecutionResult;
import com.ai.agent.runtime.planning.AgentToolChoice;
import com.ai.agent.tool.governance.AgentToolInvocationContext;
import com.ai.mcp.McpModelService;
import com.ai.mcp.ModelInvocationException;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 仅供审批恢复使用的原生消息续跑边界。
 * AiServices 1.20.2 没有无新增用户输入的恢复入口；这里保留最小循环，治理完全复用普通助手。
 * 已批准动作在 Worker 中完成，这里从它的 ToolExecutionResultMessage 继续，不重放动作。
 */
@Component
public class DurableToolContinuation {
    private final AgentChatToolService tools;
    private final McpModelService models;

    public DurableToolContinuation(AgentChatToolService tools, McpModelService models) {
        this.tools = tools;
        this.models = models;
    }

    /** 从已经校验并恢复的现场续跑；每轮受原 Run 预算约束，也允许再次审批暂停。 */
    public ReActExecutionResult resume(List<ChatMessage> restored, List<ToolSpecification> specs,
            String modelId, String sessionId, String question, AgentToolInvocationContext invocation) {
        if (restored.isEmpty() || !(restored.get(restored.size() - 1) instanceof ToolExecutionResultMessage)) {
            throw new IllegalArgumentException("审批续跑必须从已执行动作的原生结果开始");
        }
        var session = tools.open(specs, new ArrayList<>(), null, invocation, AgentToolChoice.AUTO);
        var model = session.chatModel(models.chatModel(modelId));
        List<ChatMessage> messages = new ArrayList<>(restored);
        try {
            while (true) {
                var response = model.chat(ChatRequest.builder().messages(messages).build());
                var message = response.aiMessage();
                if (!message.hasToolExecutionRequests()) {
                    if (message.text() == null || message.text().isBlank()) {
                        throw new ModelInvocationException("模型未返回有效回答");
                    }
                    return tools.finish(message.text(), null, sessionId, question);
                }
                messages.add(message);
                for (var request : message.toolExecutionRequests()) {
                    messages.add(ToolExecutionResultMessage.from(request, session.execute(request)));
                }
            }
        } catch (RuntimeException error) {
            return tools.finish(null, error, sessionId, question);
        }
    }
}
