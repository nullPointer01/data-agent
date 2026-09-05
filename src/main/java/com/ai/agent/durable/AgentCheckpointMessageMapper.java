package com.ai.agent.durable;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 将框架消息快照转换为不依赖具体模型客户端的持久化 DTO。
 *
 * @author data-agent
 */
@Component
public class AgentCheckpointMessageMapper {

    public List<AgentCheckpointMessage> capture(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<AgentCheckpointMessage> result = new ArrayList<>();
        for (ChatMessage message : messages) {
            captureMessage(message, result);
        }
        return List.copyOf(result);
    }

    /**
     * 捕获审批暂停消息，并将最后一条 AI 并行工具请求收窄为当前待审批动作。
     *
     * @param messages 完整消息历史
     * @param pendingRequest 本次唯一待审批请求
     * @return 协议关系成对的可移植消息
     */
    public List<AgentCheckpointMessage> captureForPending(List<ChatMessage> messages,
            ToolExecutionRequest pendingRequest) {
        if (messages == null || messages.isEmpty() || pendingRequest == null) {
            throw new IllegalArgumentException("审批 Checkpoint 缺少消息或待处理工具请求");
        }
        List<AgentCheckpointMessage> result = new ArrayList<>();
        int lastIndex = messages.size() - 1;
        for (int index = 0; index < messages.size(); index++) {
            ChatMessage message = messages.get(index);
            if (index == lastIndex && message instanceof AiMessage assistant
                    && assistant.hasToolExecutionRequests()) {
                if (assistant.text() != null && !assistant.text().isBlank()) {
                    result.add(textMessage(AgentCheckpointMessage.Role.ASSISTANT, assistant.text()));
                }
                result.add(new AgentCheckpointMessage(
                        AgentCheckpointMessage.Role.ASSISTANT,
                        "",
                        pendingRequest.id(),
                        pendingRequest.name(),
                        pendingRequest.arguments()));
            } else {
                captureMessage(message, result);
            }
        }
        return List.copyOf(result);
    }

    public List<ChatMessage> restore(List<AgentCheckpointMessage> checkpointMessages) {
        if (checkpointMessages == null || checkpointMessages.isEmpty()) {
            return new ArrayList<>();
        }
        List<ChatMessage> restored = new ArrayList<>();
        List<ToolExecutionRequest> pendingAssistantCalls = new ArrayList<>();
        for (AgentCheckpointMessage message : checkpointMessages) {
            boolean assistantToolCall = message.role() == AgentCheckpointMessage.Role.ASSISTANT
                    && message.toolCallId() != null;
            if (assistantToolCall) {
                pendingAssistantCalls.add(toToolRequest(message));
                continue;
            }
            flushAssistantCalls(restored, pendingAssistantCalls);
            switch (message.role()) {
                case SYSTEM -> restored.add(SystemMessage.from(message.content()));
                case USER -> restored.add(UserMessage.from(message.content()));
                case ASSISTANT -> restored.add(AiMessage.from(message.content()));
                case TOOL -> {
                    ToolExecutionRequest request = toToolRequest(message);
                    restored.add(ToolExecutionResultMessage.from(request, message.content()));
                }
            }
        }
        flushAssistantCalls(restored, pendingAssistantCalls);
        return restored;
    }

    private void captureMessage(ChatMessage message, List<AgentCheckpointMessage> target) {
        if (message instanceof SystemMessage system) {
            target.add(textMessage(AgentCheckpointMessage.Role.SYSTEM, system.text()));
            return;
        }
        if (message instanceof UserMessage user) {
            String text = user.contents().stream()
                    .filter(TextContent.class::isInstance)
                    .map(TextContent.class::cast)
                    .map(TextContent::text)
                    .collect(Collectors.joining("\n"));
            target.add(textMessage(AgentCheckpointMessage.Role.USER, text));
            return;
        }
        if (message instanceof AiMessage assistant) {
            if (assistant.text() != null && !assistant.text().isBlank()) {
                target.add(textMessage(AgentCheckpointMessage.Role.ASSISTANT, assistant.text()));
            }
            if (assistant.hasToolExecutionRequests()) {
                for (ToolExecutionRequest request : assistant.toolExecutionRequests()) {
                    target.add(new AgentCheckpointMessage(
                            AgentCheckpointMessage.Role.ASSISTANT,
                            "",
                            request.id(),
                            request.name(),
                            request.arguments()));
                }
            }
            return;
        }
        if (message instanceof ToolExecutionResultMessage toolResult) {
            target.add(new AgentCheckpointMessage(
                    AgentCheckpointMessage.Role.TOOL,
                    toolResult.text(),
                    toolResult.id(),
                    toolResult.toolName(),
                    null));
        }
    }

    private AgentCheckpointMessage textMessage(AgentCheckpointMessage.Role role, String text) {
        return new AgentCheckpointMessage(role, text, null, null, null);
    }

    private ToolExecutionRequest toToolRequest(AgentCheckpointMessage message) {
        return ToolExecutionRequest.builder()
                .id(message.toolCallId())
                .name(message.toolName())
                .arguments(message.argumentsJson() == null ? "{}" : message.argumentsJson())
                .build();
    }

    private void flushAssistantCalls(List<ChatMessage> restored,
            List<ToolExecutionRequest> pendingAssistantCalls) {
        if (pendingAssistantCalls.isEmpty()) {
            return;
        }
        restored.add(AiMessage.builder()
                .toolExecutionRequests(List.copyOf(pendingAssistantCalls))
                .build());
        pendingAssistantCalls.clear();
    }
}
