package com.ai.agent.tool;

import dev.langchain4j.agent.tool.P;
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

    @Tool("使用指定技能分析问题")
    public String useSkill(@P("技能名称") String skillName, @P("要分析的问题") String query) {
        return agentSkillToolService.useSkill(skillName, query);
    }

    @Tool("获取已上传文件的内容")
    public String getFileContent(@P("文件ID") String fileId) {
        return agentFileToolService.getFileContent(fileId);
    }

    @Tool("列出所有已上传的文件，返回文件ID、名称和类型")
    public String listFiles() {
        return agentFileToolService.listFiles();
    }

    @Tool("获取当前会话的对话历史摘要")
    public String getConversationHistory(@P("会话ID") String sessionId) {
        return agentConversationToolService.getConversationHistory(sessionId);
    }

    @Tool("当无法完成用户请求时，向用户请求更多信息或数据")
    public String askUserForInfo(@P("向用户展示的请求信息") String message) {
        return agentConversationToolService.askUserForInfo(message);
    }

    @Tool("搜索相关的历史对话、文件内容和知识，实现长期记忆")
    public String searchMemory(@P("搜索关键词") String query) {
        return agentKnowledgeToolService.searchMemory(query);
    }

    @Tool("执行数学计算表达式。支持加减乘除、括号、求幂等")
    public String calculate(@P("数学表达式，如 (100+200)*0.8") String expression) {
        return agentUtilityToolService.calculate(expression);
    }

    @Tool("获取当前时间和日期信息。无需参数。")
    public String getCurrentTime() {
        return agentUtilityToolService.getCurrentTime();
    }

    @Tool("对文件数据进行统计分析摘要(行数、列数、数值列的均值/最大/最小/求和)")
    public String analyzeFileData(@P("文件ID") String fileId) {
        return agentFileToolService.analyzeFileData(fileId);
    }

    @Tool("搜索知识库中的专业知识和文档。与 searchMemory 不同，此工具专注于搜索已索引的知识文档")
    public String searchKnowledge(@P("搜索内容") String query) {
        return agentKnowledgeToolService.searchKnowledge(query);
    }

    @Tool("查看向量记忆库的状态统计，了解已索引的数据量和类型分布。无需参数。")
    public String getMemoryStats() {
        return agentKnowledgeToolService.getMemoryStats();
    }

    @Tool("获取指定数据源的数据库 Schema（所有表名、字段名、字段类型），执行 SQL 前应先调用此工具了解表结构")
    public String getDatabaseSchema(@P("数据源名称") String datasourceName) {
        return agentDataSourceToolService.getDatabaseSchema(datasourceName);
    }

    @Tool("列出所有可用的外部数据源（数据库连接）。无需参数。")
    public String listDataSources() {
        return agentDataSourceToolService.listDataSources();
    }

    @Tool("在指定数据源上执行 SQL 查询（仅支持 SELECT）")
    public String executeSql(@P("数据源名称") String datasourceName, @P("SELECT SQL 语句") String sql) {
        return agentDataSourceToolService.executeSql(datasourceName, sql);
    }

    @Tool("预览指定数据源的数据。支持数据库首表预览、HTTP/JSON/CSV/TEXT URL 预览")
    public String previewDataSource(@P("数据源名称或ID") String datasourceName) {
        return agentDataSourceToolService.previewDataSource(datasourceName);
    }

    @Tool("根据数据生成 ECharts 图表配置，前端直接渲染")
    public String generateChart(@P("图表类型: bar/line/pie/scatter") String chartType,
            @P("数据JSON字符串") String dataJson, @P("图表标题") String title) {
        return agentChartToolService.generateChart(chartType, dataJson, title);
    }

    // [Day5 学习] 自定义工具：查询酒店出租率（mock 数据）。
    // description 是模型选工具的唯一依据，故写清"做什么 + 何时用 + 涉及哪些指标关键词"，
    // 让模型在用户问"某城市某时间的出租率/入住率"时能语义匹配到本工具。
    @Tool("查询指定城市、指定日期的酒店出租率(入住率)数据。当用户询问某地某时间的酒店出租率、"
            + "入住率、RevPAR、ADR 等经营指标时使用此工具")
    public String queryHotelOccupancy(@P("城市名称，如 杭州") String city,
            @P("日期或时间范围，如 上周 / 2026-06-10") String date) {
        // mock：真实场景应查 BI/数据库，这里返回固定示例数据用于演示工具调用
        double occupancy = 72.5;
        double adr = 458.0;
        double revpar = occupancy / 100 * adr;
        return String.format(
                "【%s · %s 酒店经营数据(mock)】%n出租率(入住率): %.1f%%%n平均房价(ADR): %.0f 元%n"
                        + "每可售房收入(RevPAR): %.1f 元%n数据来源: 模拟数据，仅用于演示",
                city, date, occupancy, adr, revpar);
    }

}
