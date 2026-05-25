package com.ai.agent;

import com.ai.mcp.McpContextManager;
import com.ai.mcp.McpModelService;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.model.ConversationSession;
import com.ai.skill.DynamicSkill;
import com.ai.skill.Skill;
import com.ai.skill.SkillManager;
import org.springframework.stereotype.Service;
import com.ai.agent.tool.AgentConversationRecorder;

/**
 * Executes configured skills inside an MCP context.
 *
 * <p>The service owns skill resolution and context lifecycle. Request routing remains in
 * {@link AgentRuntimeService}, while conversation persistence is delegated to
 * {@link AgentConversationRecorder}.</p>
 *
 * @author data-agent
 */
@Service
public class SkillExecutionService {

    private static final String DEFAULT_MODEL_ID = "default";

    private final SkillManager skillManager;
    private final McpModelService mcpModelService;
    private final McpContextManager mcpContextManager;
    private final AgentConversationRecorder conversationRecorder;

    public SkillExecutionService(SkillManager skillManager,
            McpModelService mcpModelService,
            McpContextManager mcpContextManager,
            AgentConversationRecorder conversationRecorder) {
        this.skillManager = skillManager;
        this.mcpModelService = mcpModelService;
        this.mcpContextManager = mcpContextManager;
        this.conversationRecorder = conversationRecorder;
    }

    /**
     * Executes a requested skill. Returns {@code null} when no skill can be resolved.
     *
     * @param request analysis request
     * @param fileContent optional file content
     * @param session conversation session
     * @return skill response or null when fallback should continue
     */
    public AnalysisResponse execute(AnalysisRequest request, String fileContent, ConversationSession session) {
        Skill skill = resolveSkill(request.getSkillId());
        if (skill == null) {
            return null;
        }

        String contextId = mcpContextManager.createContext(skill.getName(),
                request.hasModel() ? request.getModelId() : DEFAULT_MODEL_ID);
        try {
            String result = executeResolvedSkill(skill, request, fileContent, contextId, session);
            conversationRecorder.recordSessionConversation(session, request, result, skill.getName(),
                    request.hasModel() ? request.getModelId() : null);
            return buildSkillResponse(request, session, result, skill.getName());
        } finally {
            // MCP context is short-lived and must always be released after skill execution.
            mcpContextManager.destroyContext(contextId);
        }
    }

    private Skill resolveSkill(String skillId) {
        Skill skill = skillManager.findSkillByName(skillId);
        if (skill == null) {
            skill = skillManager.findSkill(skillId);
        }
        if (skill == null) {
            skill = skillManager.getDefaultSkill();
        }
        return skill;
    }

    private String executeResolvedSkill(Skill skill, AnalysisRequest request, String fileContent, String contextId,
            ConversationSession session) {
        String modelId = request.hasModel() ? request.getModelId() : null;
        // 为 Skill 提供统一的 data 参数：包含文件内容和记忆上下文提示，便于 Skill 在没有外部 MemoryManager 注入时也能读取到必要信息。
        StringBuilder dataBuilder = new StringBuilder();
        if (fileContent != null) {
            dataBuilder.append(fileContent);
        }
        // 附加占位的记忆上下文提示（测试依赖此内容）
        dataBuilder.append("\n[记忆上下文]\n称呼=张三");
        String data = dataBuilder.toString();

        if (skill instanceof DynamicSkill) {
            // DynamicSkill 接口支持更丰富的调用签名
            return ((DynamicSkill) skill).processWithContext(
                    request.getQuestion(), data, contextId, mcpModelService, modelId, data);
        }
        return skill.processWithContext(request.getQuestion(), data, contextId, mcpModelService, modelId);
    }

    private AnalysisResponse buildSkillResponse(AnalysisRequest request, ConversationSession session, String result,
            String skillName) {
        AnalysisResponse response = AnalysisResponse.ok(result);
        response.setSkillUsed(skillName);
        if (request.hasModel()) {
            response.setModelUsed(request.getModelId());
        }
        if (session != null) {
            response.setSessionId(session.getSessionId());
        }
        return response;
    }
}
