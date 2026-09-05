package com.ai.agent;

import com.ai.model.AnalysisRequest;
import com.ai.model.ConversationSession;

/**
 * 单次 Agent 执行前准备好的不可变上下文。
 *
 * @author data-agent
 */
public class AgentExecutionContext {

    private final AnalysisRequest request;

    private final ConversationSession session;

    private final String fileContent;

    public AgentExecutionContext(AnalysisRequest request, ConversationSession session, String fileContent) {
        this.request = request;
        this.session = session;
        this.fileContent = fileContent;
    }

    public AnalysisRequest getRequest() {
        return request;
    }

    public ConversationSession getSession() {
        return session;
    }

    public String getFileContent() {
        return fileContent;
    }
}
