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

    private final AgentRunOrigin origin;

    public AgentExecutionContext(AnalysisRequest request, ConversationSession session, String fileContent,
            AgentRunOrigin origin) {
        if (request == null) {
            throw new IllegalArgumentException("Agent 分析请求不能为空");
        }
        if (origin == null) {
            throw new IllegalArgumentException("Agent 运行来源不能为空");
        }
        this.request = request;
        this.session = session;
        this.fileContent = fileContent;
        this.origin = origin;
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

    public AgentRunOrigin getOrigin() {
        return origin;
    }
}
