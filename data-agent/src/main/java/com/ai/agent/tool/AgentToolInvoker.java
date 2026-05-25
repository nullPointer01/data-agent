package com.ai.agent.tool;

import dev.langchain4j.agent.tool.ToolSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import com.ai.agent.react.ReActToolCall;

/**
 * Invokes agent tools from parsed ReAct tool calls.
 *
 * @author data-agent
 */
@Component
public class AgentToolInvoker {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentToolInvoker.class);
    private static final String TOOL_LIST_AVAILABLE_SKILLS = "listAvailableSkills";
    private static final String TOOL_USE_SKILL = "useSkill";
    private static final String TOOL_GET_FILE_CONTENT = "getFileContent";
    private static final String TOOL_LIST_FILES = "listFiles";
    private static final String TOOL_GET_CONVERSATION_HISTORY = "getConversationHistory";
    private static final String TOOL_ASK_USER_FOR_INFO = "askUserForInfo";
    private static final String TOOL_SEARCH_MEMORY = "searchMemory";
    private static final String TOOL_CALCULATE = "calculate";
    private static final String TOOL_GET_CURRENT_TIME = "getCurrentTime";
    private static final String TOOL_ANALYZE_FILE_DATA = "analyzeFileData";
    private static final String TOOL_SEARCH_KNOWLEDGE = "searchKnowledge";
    private static final String TOOL_GET_MEMORY_STATS = "getMemoryStats";
    private static final String TOOL_LIST_DATA_SOURCES = "listDataSources";
    private static final String TOOL_GET_DATABASE_SCHEMA = "getDatabaseSchema";
    private static final String TOOL_EXECUTE_SQL = "executeSQL";
    private static final String TOOL_PREVIEW_DATA_SOURCE = "previewDataSource";
    private static final String TOOL_GENERATE_CHART = "generateChart";
    private static final String UNKNOWN_TOOL_PREFIX = "未知工具: ";
    private static final String TOOL_ERROR_PREFIX = "工具执行失败: ";
    private static final String ARGUMENT_NOT_ENOUGH_PREFIX = "参数不足: 需要";
    private static final int TWO_REQUIRED_ARGS = 2;
    private static final int THREE_REQUIRED_ARGS = 3;

    private final AgentTools agentTools;
    private final Map<String, AgentToolDefinition> toolDefinitions;

    public AgentToolInvoker(AgentTools agentTools) {
        this.agentTools = agentTools;
        this.toolDefinitions = buildDefinitions();
    }

    /**
     * Invokes one parsed ReAct tool call.
     *
     * @param toolCall parsed tool call
     * @return tool result
     */
    public String invoke(ReActToolCall toolCall) {
        LOGGER.info("Tool call: {}({})", toolCall.name(), toolCall.rawArguments());
        AgentToolDefinition definition = toolDefinitions.get(toolCall.name());
        if (definition == null) {
            return UNKNOWN_TOOL_PREFIX + toolCall.name();
        }
        try {
            return definition.executor().apply(toolCall);
        } catch (Exception e) {
            LOGGER.warn("Tool execution failed: {} - {}", toolCall.name(), e.getMessage());
            return TOOL_ERROR_PREFIX + e.getMessage();
        }
    }

    /**
     * Builds model-facing tool specifications in deterministic registration order.
     *
     * @return tool specifications
     */
    public List<ToolSpecification> buildToolSpecifications() {
        return toolDefinitions.values()
                .stream()
                .map(AgentToolDefinition::toSpecification)
                .toList();
    }

    private Map<String, AgentToolDefinition> buildDefinitions() {
        Map<String, AgentToolDefinition> definitions = new LinkedHashMap<>();
        register(definitions, TOOL_LIST_AVAILABLE_SKILLS, "列出所有可用的数据分析技能(Skill)",
                ignored -> agentTools.listAvailableSkills());
        register(definitions, TOOL_USE_SKILL, "使用指定技能分析问题。参数: skillName, query", this::invokeUseSkill);
        register(definitions, TOOL_GET_FILE_CONTENT, "获取已上传文件的内容。参数: fileId", this::invokeGetFileContent);
        register(definitions, TOOL_LIST_FILES, "列出所有已上传的文件", ignored -> agentTools.listFiles());
        register(definitions, TOOL_GET_CONVERSATION_HISTORY, "获取当前会话的对话历史。参数: sessionId",
                this::invokeGetConversationHistory);
        register(definitions, TOOL_ASK_USER_FOR_INFO, "向用户请求更多信息或数据。参数: message",
                this::invokeAskUserForInfo);
        register(definitions, TOOL_SEARCH_MEMORY, "搜索历史对话和文件中的相关信息。参数: query",
                this::invokeSearchMemory);
        register(definitions, TOOL_CALCULATE, "执行数学计算表达式，支持加减乘除括号等。参数: expression(如 '(100+200)*0.8')",
                this::invokeCalculate);
        register(definitions, TOOL_GET_CURRENT_TIME, "获取当前日期和时间", ignored -> agentTools.getCurrentTime());
        register(definitions, TOOL_ANALYZE_FILE_DATA, "对文件数据进行统计分析(行数、列数、均值、最大最小值等)。参数: fileId",
                this::invokeAnalyzeFileData);
        register(definitions, TOOL_SEARCH_KNOWLEDGE, "搜索知识库中的专业知识和文档内容。参数: query",
                this::invokeSearchKnowledge);
        register(definitions, TOOL_GET_MEMORY_STATS, "查看向量记忆库的状态和已索引数据统计",
                ignored -> agentTools.getMemoryStats());
        register(definitions, TOOL_LIST_DATA_SOURCES, "列出所有已配置的外部数据源（数据库连接）",
                ignored -> agentTools.listDataSources());
        register(definitions, TOOL_GET_DATABASE_SCHEMA,
                "获取指定数据源的数据库 Schema（所有表名、字段名、字段类型），在执行 SQL 前先调此工具了解表结构。参数: datasourceName",
                this::invokeGetDatabaseSchema);
        register(definitions, TOOL_EXECUTE_SQL, "在指定数据源上执行 SQL 查询（仅支持 SELECT）。参数: datasourceName, sql",
                this::invokeExecuteSql);
        register(definitions, TOOL_PREVIEW_DATA_SOURCE,
                "预览指定数据源的数据。支持数据库首表预览、HTTP/JSON/CSV/TEXT URL 预览。参数: datasourceName",
                this::invokePreviewDataSource);
        register(definitions, TOOL_GENERATE_CHART,
                "根据数据生成 ECharts 图表配置 JSON，前端直接渲染。参数: chartType(bar/line/pie/scatter), dataJson(数据JSON), title(图表标题)",
                this::invokeGenerateChart);
        return Collections.unmodifiableMap(definitions);
    }

    private void register(Map<String, AgentToolDefinition> definitions, String name, String description,
            Function<ReActToolCall, String> executor) {
        definitions.put(name, new AgentToolDefinition(name, description, executor));
    }

    private String invokeUseSkill(ReActToolCall toolCall) {
        if (!toolCall.hasArguments(TWO_REQUIRED_ARGS)) {
            return ARGUMENT_NOT_ENOUGH_PREFIX + "skillName和query";
        }
        return agentTools.useSkill(toolCall.argument(0), toolCall.argument(1));
    }

    private String invokeGetFileContent(ReActToolCall toolCall) {
        if (!toolCall.hasArguments(1)) {
            return ARGUMENT_NOT_ENOUGH_PREFIX + "fileId";
        }
        return agentTools.getFileContent(toolCall.argument(0));
    }

    private String invokeGetConversationHistory(ReActToolCall toolCall) {
        if (!toolCall.hasArguments(1)) {
            return ARGUMENT_NOT_ENOUGH_PREFIX + "sessionId";
        }
        return agentTools.getConversationHistory(toolCall.argument(0));
    }

    private String invokeAskUserForInfo(ReActToolCall toolCall) {
        if (!toolCall.hasArguments(1)) {
            return ARGUMENT_NOT_ENOUGH_PREFIX + "message";
        }
        return agentTools.askUserForInfo(toolCall.argument(0));
    }

    private String invokeSearchMemory(ReActToolCall toolCall) {
        if (!toolCall.hasArguments(1)) {
            return ARGUMENT_NOT_ENOUGH_PREFIX + "query";
        }
        return agentTools.searchMemory(toolCall.argument(0));
    }

    private String invokeCalculate(ReActToolCall toolCall) {
        if (!toolCall.hasArguments(1)) {
            return ARGUMENT_NOT_ENOUGH_PREFIX + "expression";
        }
        return agentTools.calculate(toolCall.argument(0));
    }

    private String invokeAnalyzeFileData(ReActToolCall toolCall) {
        if (!toolCall.hasArguments(1)) {
            return ARGUMENT_NOT_ENOUGH_PREFIX + "fileId";
        }
        return agentTools.analyzeFileData(toolCall.argument(0));
    }

    private String invokeSearchKnowledge(ReActToolCall toolCall) {
        if (!toolCall.hasArguments(1)) {
            return ARGUMENT_NOT_ENOUGH_PREFIX + "query";
        }
        return agentTools.searchKnowledge(toolCall.argument(0));
    }

    private String invokeGetDatabaseSchema(ReActToolCall toolCall) {
        if (!toolCall.hasArguments(1)) {
            return ARGUMENT_NOT_ENOUGH_PREFIX + "datasourceName";
        }
        return agentTools.getDatabaseSchema(toolCall.argument(0));
    }

    private String invokeExecuteSql(ReActToolCall toolCall) {
        if (!toolCall.hasArguments(TWO_REQUIRED_ARGS)) {
            return ARGUMENT_NOT_ENOUGH_PREFIX + "datasourceName和sql";
        }
        return agentTools.executeSql(toolCall.argument(0), toolCall.argument(1));
    }

    private String invokePreviewDataSource(ReActToolCall toolCall) {
        if (!toolCall.hasArguments(1)) {
            return ARGUMENT_NOT_ENOUGH_PREFIX + "datasourceName";
        }
        return agentTools.previewDataSource(toolCall.argument(0));
    }

    private String invokeGenerateChart(ReActToolCall toolCall) {
        if (toolCall.hasArguments(THREE_REQUIRED_ARGS)) {
            return agentTools.generateChart(toolCall.argument(0), toolCall.argument(1), toolCall.argument(2));
        }
        if (toolCall.hasArguments(TWO_REQUIRED_ARGS)) {
            return agentTools.generateChart(toolCall.argument(0), toolCall.argument(1), "");
        }
        return ARGUMENT_NOT_ENOUGH_PREFIX + "chartType和dataJson";
    }
}
