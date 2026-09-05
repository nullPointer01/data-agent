package com.ai.agent.react;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.TokenCountEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 为原生消息级模型调用准备 ReAct 消息窗口。
 *
 * <p>模型调用统一走 LangChain4j 原生协议：消息角色结构原样传给厂商，
 * 工具规格通过 Function Calling 协议字段单独下发，本类只负责上下文窗口截断。
 * 截断按 token 预算执行——单条消息可能是几十字也可能是数万字的工具结果，
 * 按条数截断控制不住上下文溢出。</p>
 *
 * @author data-agent
 */
@Component
public class ReActPromptBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReActPromptBuilder.class);
    private static final int MAX_CONTEXT_TOKENS = 8000;
    // 国产模型词表与 OpenAI 不同，此处计数是近似值；作为窗口控制（非计费）精度足够
    // 复用全局单例，避免重复加载 tiktoken BPE 词表
    private static final TokenCountEstimator TOKEN_ESTIMATOR = com.ai.config.SharedTokenizer.INSTANCE;

    /**
     * 为原生消息级调用准备消息：按 token 预算从最新消息往前保留，system 消息始终保留。
     *
     * @param messages 完整消息历史
     * @return 截断后的消息列表
     */
    public List<ChatMessage> prepareNativeMessages(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return messages;
        }
        SystemMessage systemMessage = messages.get(0) instanceof SystemMessage system ? system : null;
        int budget = MAX_CONTEXT_TOKENS
                - (systemMessage != null ? TOKEN_ESTIMATOR.estimateTokenCountInMessage(systemMessage) : 0);

        List<ChatMessage> kept = new ArrayList<>();
        int firstIndex = systemMessage != null ? 1 : 0;
        for (int i = messages.size() - 1; i >= firstIndex; i--) {
            ChatMessage message = messages.get(i);
            budget -= TOKEN_ESTIMATOR.estimateTokenCountInMessage(message);
            if (budget < 0 && !kept.isEmpty()) {
                break;
            }
            kept.add(message);
        }
        Collections.reverse(kept);

        List<ChatMessage> result = new ArrayList<>();
        if (systemMessage != null) {
            result.add(systemMessage);
        }
        result.addAll(kept);
        if (result.size() < messages.size()) {
            LOGGER.debug("ReAct 消息历史已按 token 预算截断: {} -> {} 条", messages.size(), result.size());
        }
        return result;
    }
}
