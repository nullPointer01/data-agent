package com.ai.agent.react;

import com.ai.model.AgentProfile;
import com.ai.model.AnalysisResponse;
import com.ai.service.connector.DataConnectorService;
import org.springframework.stereotype.Component;
import com.ai.agent.AgentExecutionRequest;
import com.ai.agent.AgentPromptComposer;
import com.ai.agent.AgentType;
import com.ai.agent.specialist.AgentSpecialist;

/**
 * 基于 ReAct 循环的 Agent 专家实现。
 *
 * <p>将 {@link AgentExecutionRequest} 转换为 ReAct Agent 的调用，
 * 使用 {@link AgentPromptComposer} 增强系统提示。</p>
 *
 * @author data-agent
 */
@Component
public class ReActAgentSpecialist implements AgentSpecialist {

    private final ReActAgent reActAgent;
    private final AgentPromptComposer promptComposer;
    private final DataConnectorService dataConnectorService;

    /**
     * 构造 ReAct 专家。
     *
     * @param reActAgent ReAct Agent 实例
     * @param promptComposer 提示词组合器
     * @param dataConnectorService 数据连接器服务
     */
    public ReActAgentSpecialist(ReActAgent reActAgent,
            AgentPromptComposer promptComposer,
            DataConnectorService dataConnectorService) {
        this.reActAgent = reActAgent;
        this.promptComposer = promptComposer;
        this.dataConnectorService = dataConnectorService;
    }

    /**
     * 返回 REACT 类型。
     *
     * @return Agent 类型
     */
    @Override
    public AgentType type() {
        return AgentType.REACT;
    }

    /**
     * 委托 ReAct Agent 执行请求。
     *
     * @param request 执行请求
     * @return 分析响应
     */
    @Override
    public AnalysisResponse execute(AgentExecutionRequest request) {
        String enhancedFileContent = buildEnhancedContent(request);
        return reActAgent.execute(request.request(), enhancedFileContent);
    }

    /**
     * 使用 prompt 组合器和上下文构建增强文件内容。
     */
    private String buildEnhancedContent(AgentExecutionRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.fileContent() != null && !request.fileContent().isBlank()) {
            sb.append(request.fileContent()).append('\n');
        }
        if (request.specialistTask() != null && !request.specialistTask().description().isBlank()) {
            sb.append("当前编排任务: ").append(request.specialistTask().description()).append('\n');
        }
        if (request.profile() != null && request.profile().getSystemPrompt() != null
                && !request.profile().getSystemPrompt().isBlank()) {
            sb.append("专家提示: ").append(request.profile().getSystemPrompt()).append('\n');
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
