package com.ai.mcp;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.TokenCountEstimator;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能和直接模型调用期间使用的模型上下文。
 *
 * <p>对话历史由 LangChain4j {@link ChatMemory} 维护（按 token 预算自动滚动淘汰，
 * 单条超长消息不会撑爆上下文），以消息对象形式参与模型调用，保留角色结构。</p>
 *
 * @author data-agent
 */
public class McpContext {

    private static final int MAX_HISTORY_TOKENS = 4000;
    // 国产模型词表与 OpenAI 不同，此处计数是近似值；作为窗口控制（非计费）精度足够
    // 复用全局单例，避免重复加载 tiktoken BPE 词表
    private static final TokenCountEstimator TOKEN_ESTIMATOR = com.ai.config.SharedTokenizer.INSTANCE;
    private static final String ROLE_ASSISTANT = "assistant";

    private final String contextId;
    private final String skillId;
    private final String modelType;
    private final Date createTime;
    private Date lastAccessTime;
    private final Map<String, Object> metadata;
    private final ChatMemory chatMemory;

    public McpContext(String contextId, String skillId, String modelType) {
        this.contextId = contextId;
        this.skillId = skillId;
        this.modelType = modelType;
        this.createTime = new Date();
        this.lastAccessTime = new Date();
        this.metadata = new ConcurrentHashMap<>();
        this.chatMemory = TokenWindowChatMemory.withMaxTokens(MAX_HISTORY_TOKENS, TOKEN_ESTIMATOR);
    }

    public String getContextId() {
        return contextId;
    }

    public String getSkillId() {
        return skillId;
    }

    public String getModelType() {
        return modelType;
    }

    public Date getCreateTime() {
        return new Date(createTime.getTime());
    }

    public Date getLastAccessTime() {
        return new Date(lastAccessTime.getTime());
    }

    public void setLastAccessTime(Date lastAccessTime) {
        this.lastAccessTime = new Date(lastAccessTime.getTime());
    }

    public Map<String, Object> getMetadata() {
        return metadata;
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
        return chatMemory.messages();
    }

    @Override
    public String toString() {
        return "McpContext{" +
                "contextId='" + contextId + '\'' +
                ", skillId='" + skillId + '\'' +
                ", modelType='" + modelType + '\'' +
                ", createTime=" + createTime +
                ", lastAccessTime=" + lastAccessTime +
                ", metadata=" + metadata +
                '}';
    }
}
