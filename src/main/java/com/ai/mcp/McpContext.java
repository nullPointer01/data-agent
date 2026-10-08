package com.ai.mcp;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.TokenCountEstimator;

import java.util.Date;
import java.util.List;

/**
 * 单次 Skill 执行期间使用的短期模型对话窗口。
 *
 * <p>对话历史由 LangChain4j {@link ChatMemory} 维护（按 token 预算自动滚动淘汰，
 * 单条超长消息不会撑爆上下文），以消息对象形式参与模型调用，保留角色结构。</p>
 *
 * @author data-agent
 */
public class McpContext {

    private static final int MAX_HISTORY_TOKENS = 4000;
    private static final String ROLE_ASSISTANT = "assistant";

    private final String contextId;
    private Date lastAccessTime;
    private final ChatMemory chatMemory;

    public McpContext(String contextId, TokenCountEstimator estimator) {
        this.contextId = contextId;
        this.lastAccessTime = new Date();
        this.chatMemory = TokenWindowChatMemory.withMaxTokens(MAX_HISTORY_TOKENS, estimator);
    }

    public String getContextId() {
        return contextId;
    }

    public Date getLastAccessTime() {
        return new Date(lastAccessTime.getTime());
    }

    public void setLastAccessTime(Date lastAccessTime) {
        this.lastAccessTime = new Date(lastAccessTime.getTime());
    }

    /**
     * 追加一轮对话到消息窗口。
     *
     * @param role 角色，assistant 之外一律按 user 处理
     * @param content 消息内容
     */
    public void addHistory(String role, String content) {
        if (ROLE_ASSISTANT.equals(role)) {
            chatMemory.add(AiMessage.from(content));
        } else {
            chatMemory.add(UserMessage.from(content));
        }
    }

    /**
     * 返回窗口内的历史消息（含最近一次追加的消息）。
     *
     * @return 消息列表
     */
    public List<ChatMessage> historyMessages() {
        // 框架可能按单条淘汰旧消息；不把失去用户输入的开头回答交给下一次模型调用。
        List<ChatMessage> messages = chatMemory.messages();
        int start = 0;
        while (start < messages.size() && !(messages.get(start) instanceof UserMessage)) start++;
        return List.copyOf(messages.subList(start, messages.size()));
    }
}
