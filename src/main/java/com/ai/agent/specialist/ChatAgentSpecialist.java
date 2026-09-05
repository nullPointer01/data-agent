package com.ai.agent.specialist;

import com.ai.mcp.McpModelService;
import com.ai.model.AnalysisResponse;
import org.springframework.stereotype.Component;
import com.ai.agent.AgentExecutionRequest;
import com.ai.agent.AgentPromptComposer;
import com.ai.agent.AgentType;

/**
 * 对话专家，使用 profile 系统提示词与用户直接对话。
 *
 * <p>适合轻量问答、角色化助手和无需工具调用的直接回复场景。</p>
 *
 * @author data-agent
 */
@Component
public class ChatAgentSpecialist implements AgentSpecialist {

    private final McpModelService modelService;
    private final AgentPromptComposer promptComposer;

    /**
     * 构造对话专家。
     *
     * @param modelService 模型调用服务
     * @param promptComposer Prompt 组装器
     */
    public ChatAgentSpecialist(McpModelService modelService, AgentPromptComposer promptComposer) {
        this.modelService = modelService;
        this.promptComposer = promptComposer;
    }

    @Override
    public AgentType type() {
        return AgentType.CHAT;
    }

    /**
     * 执行对话请求。
     *
     * <p>将 profile 的系统提示词、用户问题和文件内容组装成完整 Prompt，
     * 调用模型服务获取回复。</p>
     *
     * @param request 执行请求
     * @return 模型回复结果
     */
    @Override
    public AnalysisResponse execute(AgentExecutionRequest request) {
        String question = request.request().getQuestion();
        String fileContent = request.fileContent();
        String modelId = request.request().getModelId();

        String prompt = promptComposer.buildPrompt(request.profile(), question, fileContent);
        String result = modelService.callModel(prompt, modelId);
        return AnalysisResponse.ok(result);
    }
}
