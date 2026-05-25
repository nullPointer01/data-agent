package com.ai.agent.tool;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * 供 ReAct Agent 调用的工具门面。
 *
 * @author data-agent
 */
@Component
public class AgentTools {

    private final AgentSkillToolService agentSkillToolService;
    private final AgentFileToolService agentFileToolService;
    private final AgentKnowledgeToolService agentKnowledgeToolService;
    private final AgentConversationToolService agentConversationToolService;
    private final AgentDataSourceToolService agentDataSourceToolService;
    private final AgentUtilityToolService agentUtilityToolService;
    private final AgentChartToolService agentChartToolService;

    public AgentTools(AgentSkillToolService agentSkillToolService,
            AgentFileToolService agentFileToolService,
            AgentKnowledgeToolService agentKnowledgeToolService,
            AgentConversationToolService agentConversationToolService,
            AgentDataSourceToolService agentDataSourceToolService,
            AgentUtilityToolService agentUtilityToolService,
            AgentChartToolService agentChartToolService) {
        this.agentSkillToolService = agentSkillToolService;
        this.agentFileToolService = agentFileToolService;
        this.agentKnowledgeToolService = agentKnowledgeToolService;
        this.agentConversationToolService = agentConversationToolService;
        this.agentDataSourceToolService = agentDataSourceToolService;
        this.agentUtilityToolService = agentUtilityToolService;
        this.agentChartToolService = agentChartToolService;
    }

    @Tool("列出所有可用的数据分析技能(Skill)，返回技能名称和描述")
    public String listAvailableSkills() {
        return agentSkillToolService.listAvailableSkills();
    }

    @Tool("使用指定技能分析问题。参数: skillName(技能名称), query(分析问题)")
    public String useSkill(String skillName, String query) {
        return agentSkillToolService.useSkill(skillName, query);
    }

    @Tool("获取已上传文件的内容。参数: fileId(文件ID)")
    public String getFileContent(String fileId) {
        return agentFileToolService.getFileContent(fileId);
    }

    @Tool("列出所有已上传的文件，返回文件ID、名称和类型")
    public String listFiles() {
        return agentFileToolService.listFiles();
    }

    @Tool("获取当前会话的对话历史摘要")
    public String getConversationHistory(String sessionId) {
        return agentConversationToolService.getConversationHistory(sessionId);
    }

    @Tool("当无法完成用户请求时，向用户请求更多信息或数据。参数: message(请求信息)")
    public String askUserForInfo(String message) {
        return agentConversationToolService.askUserForInfo(message);
    }

    @Tool("搜索相关的历史对话、文件内容和知识，实现长期记忆。参数: query(搜索关键词)")
    public String searchMemory(String query) {
        return agentKnowledgeToolService.searchMemory(query);
    }

    @Tool("执行数学计算表达式。支持加减乘除、括号、求幂等。参数: expression(数学表达式，如 '(100+200)*0.8')")
    public String calculate(String expression) {
        return agentUtilityToolService.calculate(expression);
    }

    @Tool("获取当前时间和日期信息。无需参数。")
    public String getCurrentTime() {
        return agentUtilityToolService.getCurrentTime();
    }

    @Tool("对文件数据进行统计分析摘要(行数、列数、数值列的均值/最大/最小/求和)。参数: fileId(文件ID)")
    public String analyzeFileData(String fileId) {
        return agentFileToolService.analyzeFileData(fileId);
    }

    @Tool("搜索知识库中的专业知识和文档。与 searchMemory 不同，此工具专注于搜索已索引的知识文档。参数: query(搜索内容)")
    public String searchKnowledge(String query) {
        return agentKnowledgeToolService.searchKnowledge(query);
    }

    @Tool("查看向量记忆库的状态统计，了解已索引的数据量和类型分布。无需参数。")
    public String getMemoryStats() {
        return agentKnowledgeToolService.getMemoryStats();
    }

    @Tool("获取指定数据源的数据库 Schema（所有表名、字段名、字段类型）。参数: datasourceName(数据源名称)")
    public String getDatabaseSchema(String datasourceName) {
        return agentDataSourceToolService.getDatabaseSchema(datasourceName);
    }

    @Tool("列出所有可用的外部数据源（数据库连接）。无需参数。")
    public String listDataSources() {
        return agentDataSourceToolService.listDataSources();
    }

    @Tool("在指定数据源上执行 SQL 查询（仅支持 SELECT）。参数: datasourceName(数据源名称), sql(SQL语句)")
    public String executeSql(String datasourceName, String sql) {
        return agentDataSourceToolService.executeSql(datasourceName, sql);
    }

    @Tool("预览指定数据源的数据。支持数据库首表预览、HTTP/JSON/CSV/TEXT URL 预览。参数: datasourceName(数据源名称或ID)")
    public String previewDataSource(String datasourceName) {
        return agentDataSourceToolService.previewDataSource(datasourceName);
    }

    @Tool("根据数据生成 ECharts 图表配置。参数: chartType(bar/line/pie/scatter), dataJson(数据JSON字符串), title(图表标题)")
    public String generateChart(String chartType, String dataJson, String title) {
        return agentChartToolService.generateChart(chartType, dataJson, title);
    }

}
