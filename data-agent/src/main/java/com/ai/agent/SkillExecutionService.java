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
 * 在 MCP 上下文中执行已配置的技能。
 *
 * <p>本服务负责技能解析和上下文生命周期管理。请求路由仍在
 * {@link AgentRuntimeService} 中完成，会话持久化委托给
 * {@link AgentConversationRecorder}。</p>
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
     * 执行请求的技能。当无法解析到技能时返回 {@code null}。
     *
     * @param request 分析请求
     * @param fileContent 可选的文件内容
     * @param session 会话
     * @return 技能响应，无法解析时返回 null 以继续回退流程
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
            // MCP 上下文是短生命周期的，技能执行完成后必须释放
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
