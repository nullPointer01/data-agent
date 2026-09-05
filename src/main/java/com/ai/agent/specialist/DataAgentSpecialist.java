package com.ai.agent.specialist;

import com.ai.mcp.McpModelService;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisResponse;
import com.ai.service.connector.DataConnectorService;
import org.springframework.stereotype.Component;
import com.ai.agent.AgentExecutionRequest;
import com.ai.agent.AgentPromptComposer;
import com.ai.agent.AgentType;

/**
 * 数据源专家，绑定数据源配置进行数据分析。
 *
 * <p>执行时先通过 {@link DataConnectorService} 获取数据源预览，
 * 再与文件内容和记忆上下文合并后调用模型进行分析。
 * 如果 Agent 未配置数据源，直接返回失败。</p>
 *
 * @author data-agent
 */
@Component
public class DataAgentSpecialist implements AgentSpecialist {

    private static final int PREVIEW_LIMIT = 50;

    private final McpModelService modelService;
    private final DataConnectorService dataConnectorService;
    private final AgentPromptComposer promptComposer;

    /**
     * 构造数据源专家。
     *
     * @param modelService 模型调用服务
     * @param dataConnectorService 数据源连接服务
     * @param promptComposer Prompt 组装器
     */
    public DataAgentSpecialist(McpModelService modelService,
            DataConnectorService dataConnectorService,
            AgentPromptComposer promptComposer) {
        this.modelService = modelService;
        this.dataConnectorService = dataConnectorService;
        this.promptComposer = promptComposer;
    }

    @Override
    public AgentType type() {
        return AgentType.DATA;
    }

    /**
     * 执行数据分析请求。
     *
     * <p>处理流程：
     * <ol>
     *   <li>检查 profile 是否配置了 datasourceId，未配置则返回失败</li>
     *   <li>调用数据源预览获取样本数据</li>
     *   <li>合并文件内容与数据源预览</li>
     *   <li>合并记忆上下文</li>
     *   <li>组装完整 Prompt 并调用模型</li>
     * </ol>
     * </p>
     *
     * @param request 执行请求
     * @return 数据分析结果
     */
    @Override
    public AnalysisResponse execute(AgentExecutionRequest request) {
        AgentProfile profile = request.profile();
        String datasourceId = profile != null ? profile.getDatasourceId() : null;

        if (datasourceId == null || datasourceId.isBlank()) {
            return AnalysisResponse.fail("Data Agent 未配置数据源");
        }

        String question = request.request().getQuestion();
        String fileContent = request.fileContent();
        String modelId = request.request().getModelId();

        // 获取数据源预览
        String preview = dataConnectorService.preview(datasourceId, PREVIEW_LIMIT);

        // 合并文件内容与数据源预览
        String mergedContent = promptComposer.mergeFileContent(fileContent, preview);

        // 合并记忆上下文
        mergedContent = promptComposer.mergeMemoryContext(mergedContent, request.memoryContext());

        // 组装 Prompt 并调用模型
        String prompt = promptComposer.buildPrompt(profile, question, mergedContent);
        String result = modelService.callModel(prompt, modelId);

        return AnalysisResponse.ok(result);
    }
}
