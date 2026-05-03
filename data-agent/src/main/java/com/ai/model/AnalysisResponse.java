package com.ai.model;

public class AnalysisResponse {

    private boolean success;
    private String result;
    private String skillUsed;
    private String modelUsed;
    private long tokenConsumed;
    private String sessionId;
    private String error;
    private java.util.List<ThinkingStep> thinkingSteps;

    public AnalysisResponse() {
    }

    public static AnalysisResponse ok(String result) {
        AnalysisResponse response = new AnalysisResponse();
        response.success = true;
        response.result = result;
        return response;
    }

    public static AnalysisResponse fail(String error) {
        AnalysisResponse response = new AnalysisResponse();
        response.success = false;
        response.error = error;
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getSkillUsed() {
        return skillUsed;
    }

    public void setSkillUsed(String skillUsed) {
        this.skillUsed = skillUsed;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed) {
        this.modelUsed = modelUsed;
    }

    public long getTokenConsumed() {
        return tokenConsumed;
    }

    public void setTokenConsumed(long tokenConsumed) {
        this.tokenConsumed = tokenConsumed;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public java.util.List<ThinkingStep> getThinkingSteps() {
        return thinkingSteps;
    }

    public void setThinkingSteps(java.util.List<ThinkingStep> thinkingSteps) {
        this.thinkingSteps = thinkingSteps;
    }

    public static class ThinkingStep {
        private int step;
        private String type;
        private String content;
        private String toolName;
        private String toolResult;

        public ThinkingStep() {}

        public ThinkingStep(int step, String type, String content) {
            this.step = step;
            this.type = type;
            this.content = content;
        }

        public int getStep() { return step; }
        public void setStep(int step) { this.step = step; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        public String getToolResult() { return toolResult; }
        public void setToolResult(String toolResult) { this.toolResult = toolResult; }
    }
}
