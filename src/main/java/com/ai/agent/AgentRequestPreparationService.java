package com.ai.agent;

import com.ai.model.AnalysisRequest;
import com.ai.service.SessionManager;
import com.ai.service.file.FileProcessingService;
import org.springframework.stereotype.Service;

/** 同步调用与后台任务共用请求准入；执行时重新读取归属会话与文件权限。 */
@Service
public class AgentRequestPreparationService {
    private final SessionManager sessions;
    private final FileProcessingService files;

    public AgentRequestPreparationService(SessionManager sessions, FileProcessingService files) {
        this.sessions = sessions;
        this.files = files;
    }

    public AgentExecutionContext prepare(AnalysisRequest request) {
        if (request == null || request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new IllegalArgumentException("请输入问题");
        }
        if (request.getQuestion().length() > 32000) throw new IllegalArgumentException("问题超过长度限制");
        var session = request.hasSession() ? sessions.getSession(request.getSessionId()) : null;
        if (request.hasSession() && session == null) throw new IllegalArgumentException("会话不存在或无权限");
        String content = request.hasFile() ? files.getFileContent(request.getFileId()) : null;
        if (request.hasFile() && content == null) throw new IllegalArgumentException("文件不存在或内容为空");
        if (content != null && content.length() > 3000) {
            String prefix = content.substring(0, 3000);
            int newline = prefix.lastIndexOf('\n');
            if (newline > 2100) prefix = prefix.substring(0, newline);
            content = prefix + "\n...[文件已截断，共" + content.length() + "字符，仅展示前" + prefix.length() + "字符]";
        }
        return new AgentExecutionContext(request, session, content, AgentRunOrigin.INTERACTIVE);
    }
}
