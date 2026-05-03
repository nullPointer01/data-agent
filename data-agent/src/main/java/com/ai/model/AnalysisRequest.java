package com.ai.model;

public class AnalysisRequest {

    private String question;
    private String fileId;
    private String skillId;
    private String modelId;
    private String sessionId;

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

    public boolean isCommand() {
        return question != null && question.startsWith("/");
    }
}
