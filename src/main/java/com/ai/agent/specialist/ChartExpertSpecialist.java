package com.ai.agent.specialist;

import com.ai.model.AnalysisResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import com.ai.agent.AgentExecutionRequest;
import com.ai.agent.AgentPromptComposer;
import com.ai.agent.AgentType;
import com.ai.agent.tool.AgentChartToolService;

/**
 * 图表专家，根据用户问题中的关键词检测图表类型，调用图表生成服务输出 ECharts 配置。
 *
 * <p>支持的图表类型包括 bar（柱状图）、pie（饼图）、line（折线图）等，
 * 默认为 line 折线图。</p>
 *
 * @author data-agent
 */
@Component
public class ChartExpertSpecialist implements AgentSpecialist {

    private final AgentChartToolService chartToolService;
    private final AgentPromptComposer promptComposer;

    /**
     * 构造图表专家。
     *
     * @param chartToolService 图表生成工具服务
     * @param promptComposer Prompt 组装器
     */
    public ChartExpertSpecialist(AgentChartToolService chartToolService,
            AgentPromptComposer promptComposer) {
        this.chartToolService = chartToolService;
        this.promptComposer = promptComposer;
    }

    @Override
    public AgentType type() {
        return AgentType.CHART;
    }

    /**
     * 执行图表生成。
     *
     * <p>从用户问题中检测图表类型，调用 chartToolService 生成 ECharts 配置 JSON，
     * 并在执行元数据中记录图表类型。</p>
     *
     * @param request 执行请求
     * @return 包含图表配置的分析结果
     */
    @Override
    public AnalysisResponse execute(AgentExecutionRequest request) {
        String question = request.request().getQuestion();
        String fileContent = request.fileContent();
        String chartType = detectChartType(question);

        String chartResult = chartToolService.generateChart(chartType, fileContent, question);

        AnalysisResponse response = AnalysisResponse.ok(chartResult);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("chartType", chartType);
        response.setExecutionMetadata(metadata);
        return response;
    }

    /**
     * 从问题文本中检测图表类型。
     *
     * @param question 用户问题
     * @return 检测到的图表类型，默认 line
     */
    private String detectChartType(String question) {
        if (!StringUtils.hasText(question)) {
            return "line";
        }
        String lowerQuestion = question.toLowerCase();
        if (lowerQuestion.contains("柱") || lowerQuestion.contains("bar")) {
            return "bar";
        }
        if (lowerQuestion.contains("饼") || lowerQuestion.contains("pie")) {
            return "pie";
        }
        if (lowerQuestion.contains("散点") || lowerQuestion.contains("scatter")) {
            return "scatter";
        }
        if (lowerQuestion.contains("雷达") || lowerQuestion.contains("radar")) {
            return "radar";
        }
        if (lowerQuestion.contains("折线") || lowerQuestion.contains("line")) {
            return "line";
        }
        return "line";
    }
}
