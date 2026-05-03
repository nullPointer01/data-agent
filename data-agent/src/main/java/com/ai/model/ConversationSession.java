package com.ai.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ConversationSession {

    private static final int MAX_CONTEXT_MESSAGES = 6;
    private static final int MAX_MESSAGE_CHARS = 500;

    private final String sessionId;
    private final Date createTime;
    private Date lastAccessTime;
    private final List<Message> history;
    private String modelId;
    private String skillId;

    public ConversationSession(String sessionId) {
        this.sessionId = sessionId;
        this.createTime = new Date();
        this.lastAccessTime = new Date();
        this.history = new ArrayList<>();
    }

    public String getSessionId() {
        return sessionId;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public Date getLastAccessTime() {
        return lastAccessTime;
    }

    public void setLastAccessTime(Date lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }

    public List<Message> getHistory() {
        return history;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }

    public void addUserMessage(String content) {
        history.add(new Message("user", content));
        this.lastAccessTime = new Date();
    }

    public void addAssistantMessage(String content) {
        history.add(new Message("assistant", content));
        this.lastAccessTime = new Date();
    }

    public String buildContextPrompt(String currentQuestion) {
        if (history.isEmpty()) {
            return currentQuestion;
        }

        int start = Math.max(0, history.size() - MAX_CONTEXT_MESSAGES);
        List<Message> recentHistory = history.subList(start, history.size());

        StringBuilder sb = new StringBuilder();
        for (Message msg : recentHistory) {
            String truncated = truncateMessage(msg.content);
            sb.append(msg.role.equals("user") ? "用户" : "助手").append(": ");
            sb.append(truncated).append("\n");
        }
        sb.append("用户: ").append(currentQuestion);
        return sb.toString();
    }

    public String buildSkillContext(String currentQuestion) {
        if (history.isEmpty()) {
            return currentQuestion;
        }

        int start = Math.max(0, history.size() - 2);
        List<Message> recentHistory = history.subList(start, history.size());

        StringBuilder sb = new StringBuilder();
        for (Message msg : recentHistory) {
            String truncated = truncateMessage(msg.content);
            sb.append(msg.role.equals("user") ? "Q" : "A").append(": ");
            sb.append(truncated).append("\n");
        }
        sb.append("Q: ").append(currentQuestion);
        return sb.toString();
    }

    private String truncateMessage(String content) {
        if (content == null) return "";
        if (content.length() <= MAX_MESSAGE_CHARS) return content;
        return content.substring(0, MAX_MESSAGE_CHARS) + "...";
    }

    public static class Message {
        private final String role;
        private final String content;
        private final Date timestamp;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
            this.timestamp = new Date();
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }

        public Date getTimestamp() {
            return timestamp;
        }
    }
}
