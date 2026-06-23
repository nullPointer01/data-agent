package com.ai.agent;

import com.ai.agent.react.ReActStreamEventWriter;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.model.ConversationSession;
import com.ai.service.file.FileProcessingService;
import com.ai.service.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * 默认数据分析 Agent 实现。
 *
 * @author data-agent
 */
@Component
public class DataAnalysisAgentImpl implements DataAnalysisAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataAnalysisAgentImpl.class);
    private static final int MAX_FILE_CHARS = 3000;
    private static final double FILE_TRUNCATE_NEWLINE_RATIO = 0.7D;
    private static final String EMPTY_QUESTION_MESSAGE = "请输入问题";
    private static final String SESSION_EXPIRED_MESSAGE = "会话不存在或已过期，请创建新会话";
    private static final String EMPTY_FILE_MESSAGE = "文件不存在或内容为空，请先上传文件";

    private final FileProcessingService fileProcessingService;
    private final SessionManager sessionManager;
    private final AgentRuntimeService agentRuntimeService;
    private final ReActStreamEventWriter streamEventWriter;

    public DataAnalysisAgentImpl(FileProcessingService fileProcessingService, SessionManager sessionManager,
            AgentRuntimeService agentRuntimeService, ReActStreamEventWriter streamEventWriter) {
        this.fileProcessingService = fileProcessingService;
        this.sessionManager = sessionManager;
        this.agentRuntimeService = agentRuntimeService;
        this.streamEventWriter = streamEventWriter;
    }

    @Override
    public AnalysisResponse analyze(AnalysisRequest request) {
        if (request == null || request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            return AnalysisResponse.fail(EMPTY_QUESTION_MESSAGE);
        }

        ConversationSession session = resolveSession(request);
        if (request.hasSession() && session == null) {
            return AnalysisResponse.fail(SESSION_EXPIRED_MESSAGE);
        }

        String fileContent = loadFileContent(request);
        if (request.hasFile() && fileContent == null) {
            return AnalysisResponse.fail(EMPTY_FILE_MESSAGE);
        }

        return agentRuntimeService.execute(new AgentExecutionContext(request, session, fileContent));
    }

    @Override
    public void analyzeStreaming(AnalysisRequest request, Consumer<String> eventEmitter) {
        if (request == null || request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            streamEventWriter.emitError(eventEmitter, EMPTY_QUESTION_MESSAGE);
            return;
        }

        ConversationSession session = resolveSession(request);
        if (request.hasSession() && session == null) {
            streamEventWriter.emitError(eventEmitter, SESSION_EXPIRED_MESSAGE);
            return;
        }

        String fileContent = loadFileContent(request);
        if (request.hasFile() && fileContent == null) {
            streamEventWriter.emitError(eventEmitter, EMPTY_FILE_MESSAGE);
            return;
        }

        agentRuntimeService.executeStreaming(new AgentExecutionContext(request, session, fileContent), eventEmitter);
    }

    private ConversationSession resolveSession(AnalysisRequest request) {
        if (!request.hasSession()) {
            return null;
        }
        return sessionManager.getSession(request.getSessionId());
    }

    private String loadFileContent(AnalysisRequest request) {
        if (!request.hasFile()) {
            return null;
        }
        String fileContent = fileProcessingService.getFileContent(request.getFileId());
        return fileContent == null ? null : truncateFile(fileContent);
    }

    private String truncateFile(String content) {
        if (content == null || content.length() <= MAX_FILE_CHARS) {
            return content;
        }
        String truncated = content.substring(0, MAX_FILE_CHARS);
        int lastNewline = truncated.lastIndexOf('\n');
        if (lastNewline > MAX_FILE_CHARS * FILE_TRUNCATE_NEWLINE_RATIO) {
            truncated = truncated.substring(0, lastNewline);
        }
        return truncated + "\n...[文件已截断，共" + content.length() + "字符，仅展示前" + truncated.length() + "字符]";
    }
}
