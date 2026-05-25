package com.ai.agent.specialist;

import com.ai.mcp.McpModelService;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import com.ai.agent.AgentExecutionRequest;
import com.ai.agent.AgentPromptComposer;
import com.ai.agent.AgentType;

/**
 * 报告专家，根据用户问题和文件内容生成结构化 Markdown 报告。
 *
 * <p>执行元数据中包含 reportReady 标志和报告摘要（首个 Markdown 标题行）。</p>
 *
 * @author data-agent
 */
@Component
public class ReportExpertSpecialist implements AgentSpecialist {

    private final McpModelService modelService;
    private final AgentPromptComposer promptComposer;

    /**
     * 构造报告专家。
     *
     * @param modelService 模型调用服务
     * @param promptComposer Prompt 组装器
     */
    public ReportExpertSpecialist(McpModelService modelService,
            AgentPromptComposer promptComposer) {
        this.modelService = modelService;
        this.promptComposer = promptComposer;
    }

    @Override
    public AgentType type() {
        return AgentType.REPORT;
    }

    /**
     * 执行报告生成请求。
     *
     * <p>组装包含"请输出 Markdown 报告"指令的 Prompt，调用模型生成报告。
     * 从生成结果中提取首个 Markdown 标题作为摘要。</p>
     *
     * @param request 执行请求
     * @return 包含报告内容和元数据的分析结果
     */
    @Override
    public AnalysisResponse execute(AgentExecutionRequest request) {
        String question = request.request().getQuestion();
        String fileContent = request.fileContent();

        // 构建报告生成 Prompt
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("请输出 Markdown 报告。\n\n");
        if (StringUtils.hasText(question)) {
            promptBuilder.append("## 报告主题\n").append(question).append("\n\n");
        }
        if (StringUtils.hasText(fileContent)) {
            promptBuilder.append("## 参考数据\n").append(fileContent).append("\n\n");
        }

        String prompt = promptBuilder.toString();
        String result = modelService.callModel(prompt, null);

        // 提取首个 Markdown 标题作为摘要
        String summary = extractFirstHeading(result);

        // 构建响应及元数据
        AnalysisResponse response = AnalysisResponse.ok(result);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("reportReady", true);
        metadata.put("summary", summary);
        response.setExecutionMetadata(metadata);
        return response;
    }

    /**
     * 从 Markdown 文本中提取首个标题行。
     *
     * @param text Markdown 文本
     * @return 首个标题行（含 # 前缀），未找到时返回空字符串
     */
    private String extractFirstHeading(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String[] lines = text.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                return trimmed;
            }
        }
        return "";
    }
}
