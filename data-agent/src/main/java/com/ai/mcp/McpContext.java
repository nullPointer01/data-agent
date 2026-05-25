package com.ai.mcp;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Model context used during skill and direct model calls.
 *
 * @author data-agent
 */
public class McpContext {

    private static final int HISTORY_ROLE_FACTOR = 2;
    private static final String EMPTY_PROMPT = "";
    private static final String HISTORY_HEADER = "\n## 上下文历史\n";

    private final String contextId;
    private final String skillId;
    private final String modelType;
    private final Date createTime;
    private Date lastAccessTime;
    private final Map<String, Object> metadata;
    private final List<String> conversationHistory;

    public McpContext(String contextId, String skillId, String modelType) {
        this.contextId = contextId;
        this.skillId = skillId;
        this.modelType = modelType;
        this.createTime = new Date();
        this.lastAccessTime = new Date();
        this.metadata = new ConcurrentHashMap<>();
        this.conversationHistory = new ArrayList<>();
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

    public List<String> getConversationHistory() {
        return List.copyOf(conversationHistory);
    }

    public void addHistory(String role, String content) {
        conversationHistory.add("[" + role + "] " + content);
    }

    public String buildHistoryPrompt(int maxTurns) {
        if (conversationHistory.isEmpty()) {
            return EMPTY_PROMPT;
        }
        int start = Math.max(0, conversationHistory.size() - maxTurns * HISTORY_ROLE_FACTOR);
        StringBuilder sb = new StringBuilder(HISTORY_HEADER);
        for (int i = start; i < conversationHistory.size(); i++) {
            sb.append(conversationHistory.get(i)).append("\n");
        }
        return sb.toString();
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
