package com.ai.agent.react;

import com.ai.mcp.TokenMonitor;
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

/**
 * 构建 ReAct 模型调用使用的提示词。
 *
 * @author data-agent
 */
@Component
public class ReActPromptBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReActPromptBuilder.class);
    private static final int MAX_CONTEXT_MESSAGES = 12;
    private static final int MAX_PROMPT_TOKENS = 8000;
    private static final int PROMPT_TOKEN_CHAR_RATIO = 3;
    private static final int PROMPT_TRUNCATE_DENOMINATOR = 3;
    private static final int PROMPT_RETAIN_TAIL_CHARS = 200;
    private static final String HISTORY_TRUNCATED_MARK = "\n\n[历史对话已截断]\n\n";
    private static final String CONTENT_TRUNCATED_MARK = "\n...[内容截断]";

    private final TokenMonitor tokenMonitor;

    public ReActPromptBuilder(TokenMonitor tokenMonitor) {
        this.tokenMonitor = tokenMonitor;
    }

    public String buildPromptForModel(List<ChatMessage> messages, List<ToolSpecification> toolSpecs) {
        String toolsPrompt = buildToolsPrompt(toolSpecs);
        List<ChatMessage> truncated = truncateMessages(messages);
        List<ChatMessage> enhancedMessages = new ArrayList<>(truncated);
        if (!enhancedMessages.isEmpty() && enhancedMessages.get(0) instanceof SystemMessage) {
            String originalSystem = ((SystemMessage) enhancedMessages.get(0)).text();
            enhancedMessages.set(0, SystemMessage.from(originalSystem + "\n\n" + toolsPrompt));
        } else {
            enhancedMessages.add(0, SystemMessage.from(toolsPrompt));
        }

        String prompt = buildPromptFromMessages(enhancedMessages);
        long estimatedTokens = tokenMonitor.estimateTokens(prompt);
        if (estimatedTokens > MAX_PROMPT_TOKENS) {
            LOGGER.warn("ReAct 提示词估算 token 数 {} 超过预算 {}，执行强截断",
                    estimatedTokens, MAX_PROMPT_TOKENS);
            return aggressiveTruncatePrompt(prompt);
        }
        return prompt;
    }

    public String buildSummaryPrompt(List<ChatMessage> messages) {
        return buildPromptFromMessages(truncateMessages(messages));
    }

    private List<ChatMessage> truncateMessages(List<ChatMessage> messages) {
        if (messages.size() <= MAX_CONTEXT_MESSAGES) {
            return messages;
        }
        List<ChatMessage> result = new ArrayList<>();
        if (!messages.isEmpty() && messages.get(0) instanceof SystemMessage) {
            result.add(messages.get(0));
        }
        int keep = MAX_CONTEXT_MESSAGES - result.size();
        result.addAll(messages.subList(messages.size() - keep, messages.size()));
        LOGGER.debug("ReAct 消息历史已截断: {} -> {}", messages.size(), result.size());
        return result;
    }

    private String aggressiveTruncatePrompt(String prompt) {
        int cutoff = prompt.lastIndexOf("\n\n", prompt.length() * 2 / PROMPT_TRUNCATE_DENOMINATOR);
        if (cutoff > prompt.length() / PROMPT_TRUNCATE_DENOMINATOR) {
            int tailStart = prompt.lastIndexOf("\n\n", prompt.length() - PROMPT_RETAIN_TAIL_CHARS);
            return prompt.substring(0, cutoff) + HISTORY_TRUNCATED_MARK + prompt.substring(tailStart);
        }
        int retainedLength = Math.min(prompt.length(), MAX_PROMPT_TOKENS * PROMPT_TOKEN_CHAR_RATIO);
        return prompt.substring(0, retainedLength) + CONTENT_TRUNCATED_MARK;
    }

    private String buildToolsPrompt(List<ToolSpecification> toolSpecs) {
        StringBuilder sb = new StringBuilder("\n\n# 可用工具\n");
        for (ToolSpecification spec : toolSpecs) {
            sb.append("- ").append(spec.name()).append(": ");
            if (spec.description() != null) {
                sb.append(spec.description());
            }
            sb.append("\n");
        }
        sb.append("\n工具调用只能使用 JSON 格式: {\"tool\":\"工具名\",\"arguments\":[\"参数1\",\"参数2\"]}\n");
        sb.append("例如: {\"tool\":\"listAvailableSkills\",\"arguments\":[]}\n");
        sb.append("例如: {\"tool\":\"useSkill\",\"arguments\":[\"销售分析\",\"分析销售趋势\"]}\n");
        sb.append("不要使用 Markdown 代码块包裹工具调用，不要输出旧的 CALL 格式。\n");
        sb.append("不调用工具时直接回答。\n");
        return sb.toString();
    }

    private String buildPromptFromMessages(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage message : messages) {
            appendMessage(sb, message);
        }
        return sb.toString();
    }

    private void appendMessage(StringBuilder sb, ChatMessage message) {
        if (message instanceof SystemMessage) {
            sb.append("[系统] ").append(((SystemMessage) message).text()).append("\n\n");
            return;
        }
        if (message instanceof UserMessage) {
            sb.append("[用户] ").append(((UserMessage) message).singleText()).append("\n\n");
            return;
        }
        if (message instanceof AiMessage) {
            sb.append("[助手] ").append(((AiMessage) message).text()).append("\n\n");
        }
    }
}
