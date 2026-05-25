package com.ai.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Redis-friendly in-memory conversation context.
 *
 * @author data-agent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConversationSession {

    private static final int MAX_CONTEXT_MESSAGES = 6;
    private static final int MAX_SKILL_CONTEXT_MESSAGES = 2;
    private static final int MAX_MESSAGE_CHARS = 500;
    private static final String ROLE_USER = "user";
    private static final String USER_LABEL = "用户";
    private static final String ASSISTANT_LABEL = "助手";
    private static final String QUESTION_LABEL = "Q";
    private static final String ANSWER_LABEL = "A";

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

    @JsonCreator
    public ConversationSession(
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("createTime") Date createTime,
            @JsonProperty("lastAccessTime") Date lastAccessTime,
            @JsonProperty("history") List<Message> history,
            @JsonProperty("modelId") String modelId,
            @JsonProperty("skillId") String skillId) {
        this.sessionId = sessionId;
        this.createTime = createTime != null ? createTime : new Date();
        this.lastAccessTime = lastAccessTime != null ? lastAccessTime : new Date();
        this.history = history != null ? history : new ArrayList<>();
        this.modelId = modelId;
        this.skillId = skillId;
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
        history.add(new Message(ROLE_USER, content));
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
            String truncated = truncateMessage(msg.getContent());
            sb.append(ROLE_USER.equals(msg.getRole()) ? USER_LABEL : ASSISTANT_LABEL).append(": ");
            sb.append(truncated).append("\n");
        }
        sb.append(USER_LABEL).append(": ").append(currentQuestion);
        return sb.toString();
    }

    public String buildSkillContext(String currentQuestion) {
        if (history.isEmpty()) {
            return currentQuestion;
        }

        int start = Math.max(0, history.size() - MAX_SKILL_CONTEXT_MESSAGES);
        List<Message> recentHistory = history.subList(start, history.size());

        StringBuilder sb = new StringBuilder();
        for (Message msg : recentHistory) {
            String truncated = truncateMessage(msg.getContent());
            sb.append(ROLE_USER.equals(msg.getRole()) ? QUESTION_LABEL : ANSWER_LABEL).append(": ");
            sb.append(truncated).append("\n");
        }
        sb.append(QUESTION_LABEL).append(": ").append(currentQuestion);
        return sb.toString();
    }

    private String truncateMessage(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() <= MAX_MESSAGE_CHARS) {
            return content;
        }
        return content.substring(0, MAX_MESSAGE_CHARS) + "...";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        private final String role;
        private final String content;
        private final Date timestamp;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
            this.timestamp = new Date();
        }

        @JsonCreator
        public Message(
                @JsonProperty("role") String role,
                @JsonProperty("content") String content,
                @JsonProperty("timestamp") Date timestamp) {
            this.role = role;
            this.content = content;
            this.timestamp = timestamp != null ? timestamp : new Date();
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
