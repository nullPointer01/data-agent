package com.ai.agent.tool;

import com.ai.mcp.McpModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Chart-generation tool backed by the configured model provider.
 *
 * @author data-agent
 */
@Service
public class AgentChartToolService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentChartToolService.class);
    private static final String CHART_PREFIX = "CHART:";
    private static final String JSON_OBJECT_START = "{";
    private static final String CHART_JSON_BLOCK_PATTERN = "```json\\s*";
    private static final String MARKDOWN_BLOCK_PATTERN = "```\\s*";

    private final McpModelService mcpModelService;

    public AgentChartToolService(McpModelService mcpModelService) {
        this.mcpModelService = mcpModelService;
    }

    /**
     * Generates an ECharts option JSON from source data.
     *
     * @param chartType chart type, for example bar or line
     * @param dataJson source data as JSON text
     * @param title chart title
     * @return prefixed chart payload that the frontend can render
     */
    public String generateChart(String chartType, String dataJson, String title) {
        LOGGER.info("Agent generateChart: type={}, title={}", chartType, title);
        String prompt = buildChartPrompt(chartType, dataJson, title);
        try {
            String optionJson = mcpModelService.callModel(prompt, null);
            return CHART_PREFIX + normalizeJson(optionJson);
        } catch (Exception e) {
            return "图表生成失败: " + e.getMessage();
        }
    }

    private String buildChartPrompt(String chartType, String dataJson, String title) {
        return String.format("""
                请根据以下数据生成一个 ECharts 的 option 配置 JSON。

                图表类型: %s
                图表标题: %s
                数据: %s

                要求：
                1. 只输出纯 JSON，不要任何 markdown 标记或代码块
                2. JSON 必须是合法的 ECharts option 对象
                3. 包含 title, tooltip, series 等必要字段
                4. 如果是 bar/line 类型，包含 xAxis 和 yAxis
                5. 数据要正确映射到 series.data

                直接输出 JSON：""", chartType, title, dataJson);
    }

    private String normalizeJson(String value) {
        // LLMs may wrap JSON in markdown fences; strip those before handing it to the frontend.
        String json = value.replaceAll(CHART_JSON_BLOCK_PATTERN, "")
                .replaceAll(MARKDOWN_BLOCK_PATTERN, "")
                .trim();
        if (json.startsWith(JSON_OBJECT_START)) {
            return json;
        }
        int jsonStartIndex = json.indexOf(JSON_OBJECT_START);
        if (jsonStartIndex >= 0) {
            return json.substring(jsonStartIndex);
        }
        return json;
    }
}
