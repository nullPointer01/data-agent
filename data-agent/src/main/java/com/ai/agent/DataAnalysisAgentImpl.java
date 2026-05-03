package com.ai.agent;

import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.model.ConversationSession;
import com.ai.service.FileProcessingService;
import com.ai.service.SessionManager;
import com.ai.skill.DynamicSkill;
import com.ai.skill.Skill;
import com.ai.skill.SkillManager;
import com.ai.mcp.MCPModelService;
import com.ai.mcp.MCPContextManager;
import com.ai.mcp.TokenMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DataAnalysisAgentImpl implements DataAnalysisAgent {

    private static final Logger log = LoggerFactory.getLogger(DataAnalysisAgentImpl.class);
    private static final int MAX_FILE_CHARS = 3000;

    private final SkillManager skillManager;
    private final FileProcessingService fileProcessingService;
    private final MCPModelService mcpModelService;
    private final MCPContextManager mcpContextManager;
    private final TokenMonitor tokenMonitor;
    private final SessionManager sessionManager;
    private final ReActAgent reActAgent;

    public DataAnalysisAgentImpl(SkillManager skillManager, FileProcessingService fileProcessingService,
            MCPModelService mcpModelService, MCPContextManager mcpContextManager,
            TokenMonitor tokenMonitor, SessionManager sessionManager, ReActAgent reActAgent) {
        this.skillManager = skillManager;
        this.fileProcessingService = fileProcessingService;
        this.mcpModelService = mcpModelService;
        this.mcpContextManager = mcpContextManager;
        this.tokenMonitor = tokenMonitor;
        this.sessionManager = sessionManager;
        this.reActAgent = reActAgent;
    }

    @Override
    public AnalysisResponse analyze(AnalysisRequest request) {
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            return AnalysisResponse.fail("请输入问题");
        }

        ConversationSession session = null;
        if (request.hasSession()) {
            session = sessionManager.getSession(request.getSessionId());
            if (session == null) {
                return AnalysisResponse.fail("会话不存在或已过期，请创建新会话");
            }
        }

        String fileContent = null;
        if (request.hasFile()) {
            fileContent = fileProcessingService.getFileContent(request.getFileId());
            if (fileContent == null) {
                return AnalysisResponse.fail("文件不存在或内容为空，请先上传文件");
            }
            fileContent = truncateFile(fileContent);
        }

        if (request.isCommand()) {
            return handleCommand(request, fileContent, session);
        }

        if (request.hasSkill()) {
            return handleWithSkill(request, fileContent, session);
        }

        return reActAgent.execute(request, fileContent);
    }

    private AnalysisResponse handleCommand(AnalysisRequest request, String fileContent, ConversationSession session) {
        String commandResult = skillManager.processWithCommand(request.getQuestion(), fileContent);
        if (commandResult != null) {
            if (session != null) {
                session.addUserMessage(request.getQuestion());
                session.addAssistantMessage(commandResult);
                persistMessages(session.getSessionId(), request.getQuestion(), commandResult, "command", null);
            }
            AnalysisResponse response = AnalysisResponse.ok(commandResult);
            response.setSkillUsed("command");
            if (session != null)
                response.setSessionId(session.getSessionId());
            return response;
        }
        return reActAgent.execute(request, fileContent);
    }

    private AnalysisResponse handleWithSkill(AnalysisRequest request, String fileContent, ConversationSession session) {
        String skillId = request.getSkillId();
        Skill skill = skillManager.findSkillByName(skillId);
        if (skill == null) {
            skill = skillManager.findSkill(skillId);
        }
        if (skill == null) {
            skill = skillManager.getDefaultSkill();
        }
        if (skill == null) {
            return reActAgent.execute(request, fileContent);
        }

        String contextId = mcpContextManager.createContext(skill.getName(),
                request.hasModel() ? request.getModelId() : "default");
        try {
            String modelId = request.hasModel() ? request.getModelId() : null;
            String conversationContext = null;
            if (session != null && !session.getHistory().isEmpty()) {
                conversationContext = session.buildSkillContext(request.getQuestion());
            }

            String result;
            if (skill instanceof DynamicSkill) {
                result = ((DynamicSkill) skill).processWithContext(
                        request.getQuestion(), fileContent, contextId, mcpModelService, modelId, conversationContext);
            } else {
                result = skill.processWithContext(request.getQuestion(), fileContent, contextId, mcpModelService,
                        modelId);
            }

            if (session != null) {
                session.addUserMessage(request.getQuestion());
                session.addAssistantMessage(result);
                persistMessages(session.getSessionId(), request.getQuestion(), result, skill.getName(),
                        request.getModelId());
            }

            AnalysisResponse response = AnalysisResponse.ok(result);
            response.setSkillUsed(skill.getName());
            if (request.hasModel())
                response.setModelUsed(request.getModelId());
            if (session != null)
                response.setSessionId(session.getSessionId());
            return response;
        } finally {
            mcpContextManager.destroyContext(contextId);
        }
    }

    private String truncateFile(String content) {
        if (content == null || content.length() <= MAX_FILE_CHARS)
            return content;
        String truncated = content.substring(0, MAX_FILE_CHARS);
        int lastNewline = truncated.lastIndexOf('\n');
        if (lastNewline > MAX_FILE_CHARS * 0.7) {
            truncated = truncated.substring(0, lastNewline);
        }
        return truncated + "\n...[文件已截断，共" + content.length() + "字符，仅展示前" + truncated.length() + "字符]";
    }

    private void persistMessages(String sessionId, String question, String result, String skillUsed, String modelUsed) {
        try {
            sessionManager.saveMessage(sessionId, "user", question, null, null, tokenMonitor.estimateTokens(question));
            sessionManager.saveMessage(sessionId, "assistant", result, skillUsed, modelUsed,
                    tokenMonitor.estimateTokens(result));
        } catch (Exception e) {
            log.warn("Failed to persist messages for session: {}", sessionId, e);
        }
    }
}
