package com.ai.model;

import com.ai.agent.outcome.AgentTaskContract;

/**
 * 分析 Agent 执行请求。
 *
 * @author data-agent
 */
public class AnalysisRequest {

    private String question;
    private String fileId;
    private String skillId;
    private String modelId;
    private String sessionId;
    private String agentId;
    private AgentTaskContract taskContract;

    public AnalysisRequest() {
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public AgentTaskContract getTaskContract() {
        return taskContract;
    }

    public void setTaskContract(AgentTaskContract taskContract) {
        this.taskContract = taskContract;
    }

    /**
     * 创建当前请求的浅拷贝，供运行时追加默认配置时隔离副作用。
     *
     * @return 请求副本
     */
    public AnalysisRequest copy() {
        AnalysisRequest copiedRequest = new AnalysisRequest();
        copiedRequest.setQuestion(question);
        copiedRequest.setFileId(fileId);
        copiedRequest.setSkillId(skillId);
        copiedRequest.setModelId(modelId);
        copiedRequest.setSessionId(sessionId);
        copiedRequest.setAgentId(agentId);
        copiedRequest.setTaskContract(taskContract);
        return copiedRequest;
    }

    public boolean hasFile() {
        return fileId != null && !fileId.isEmpty();
    }

    public boolean hasSkill() {
        return skillId != null && !skillId.isEmpty();
    }

    public boolean hasModel() {
        return modelId != null && !modelId.isEmpty();
    }

    public boolean hasSession() {
        return sessionId != null && !sessionId.isEmpty();
    }

    public boolean hasAgent() {
        return agentId != null && !agentId.isEmpty();
    }

    public boolean hasTaskContract() {
        return taskContract != null;
    }

    public boolean isCommand() {
        return question != null && question.startsWith("/");
    }
}
