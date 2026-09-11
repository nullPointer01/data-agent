package com.ai.agent.tool;

import com.ai.agent.tool.governance.AgentToolPolicy;
import com.ai.agent.tool.governance.AgentToolRiskLevel;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 供 ReAct Agent 调用的工具门面。
 *
 * @author data-agent
 */
@Component
public class AgentTools {

    public static final String LIST_AVAILABLE_SKILLS_TOOL = "listAvailableSkills";
    public static final String USE_SKILL_TOOL = "useSkill";
    public static final String DELEGATE_TO_AGENT_TOOL = "delegateToAgent";
    private static final Set<String> CAPABILITY_ADAPTER_TOOLS = Set.of(
            LIST_AVAILABLE_SKILLS_TOOL,
            USE_SKILL_TOOL,
            DELEGATE_TO_AGENT_TOOL);

    /**
     * 判断工具是否属于 Harness 根据能力绑定自动暴露的内部适配器。
     *
     * @param toolName 工具名称
     * @return 内部能力适配器返回 true
     */
    public static boolean isCapabilityAdapter(String toolName) {
        return toolName != null && CAPABILITY_ADAPTER_TOOLS.contains(toolName);
    }

    public static boolean isDelegationAdapter(String toolName) {
        return DELEGATE_TO_AGENT_TOOL.equals(toolName);
    }

    private final AgentSkillToolService agentSkillToolService;
    private final AgentDelegationToolService agentDelegationToolService;
    private final AgentFileToolService agentFileToolService;
    private final AgentKnowledgeToolService agentKnowledgeToolService;
    private final AgentConversationToolService agentConversationToolService;
    private final AgentDataSourceToolService agentDataSourceToolService;
    private final AgentUtilityToolService agentUtilityToolService;
    private final AgentChartToolService agentChartToolService;
    private final AgentSandboxToolService agentSandboxToolService;

    public AgentTools(AgentSkillToolService agentSkillToolService,
            AgentDelegationToolService agentDelegationToolService,
            AgentFileToolService agentFileToolService,
            AgentKnowledgeToolService agentKnowledgeToolService,
            AgentConversationToolService agentConversationToolService,
            AgentDataSourceToolService agentDataSourceToolService,
            AgentUtilityToolService agentUtilityToolService,
            AgentChartToolService agentChartToolService,
            AgentSandboxToolService agentSandboxToolService) {
        this.agentSkillToolService = agentSkillToolService;
        this.agentDelegationToolService = agentDelegationToolService;
        this.agentFileToolService = agentFileToolService;
        this.agentKnowledgeToolService = agentKnowledgeToolService;
        this.agentConversationToolService = agentConversationToolService;
        this.agentDataSourceToolService = agentDataSourceToolService;
        this.agentUtilityToolService = agentUtilityToolService;
        this.agentChartToolService = agentChartToolService;
        this.agentSandboxToolService = agentSandboxToolService;
    }

    @Tool("列出当前 Agent 已绑定且本次运行可用的 Skill，返回稳定ID、名称和描述")
    @AgentToolPolicy(risk = AgentToolRiskLevel.LOW, readOnly = true, idempotent = true, retryable = true,
            timeoutMs = 10000, maxAttempts = 2, requiredPermission = "app:use", maxResultLength = 8000)
    public String listAvailableSkills() {
        return agentSkillToolService.listAvailableSkills();
    }

    @Tool("使用当前 Agent 已绑定的指定 Skill 分析问题；skillId 必须来自 listAvailableSkills")
    @AgentToolPolicy(risk = AgentToolRiskLevel.MEDIUM, readOnly = true, idempotent = false, retryable = false,
            timeoutMs = 60000, maxAttempts = 1, requiredPermission = "app:use", maxResultLength = 16000)
    public String useSkill(@P("Skill稳定ID") String skillId, @P("要分析的问题") String query) {
        return agentSkillToolService.useSkill(skillId, query);
    }

    @Tool("将明确任务委派给当前 Agent 已绑定的子 Agent；agentId 必须来自当前系统提示中的稳定ID")
    @AgentToolPolicy(risk = AgentToolRiskLevel.MEDIUM, readOnly = false, idempotent = false, retryable = false,
            timeoutMs = 120000, maxAttempts = 1, requiredPermission = "app:use", maxResultLength = 20000)
    public String delegateToAgent(@P("子Agent稳定ID") String agentId, @P("委派给子Agent的明确任务") String task) {
        return agentDelegationToolService.delegateToAgent(agentId, task);
    }

    @Tool("获取已上传文件的内容")
    @AgentToolPolicy(risk = AgentToolRiskLevel.MEDIUM, readOnly = true, idempotent = true, retryable = true,
            timeoutMs = 15000, maxAttempts = 2, requiredPermission = "app:use", maxResultLength = 20000)
    public String getFileContent(@P("文件ID") String fileId) {
        return agentFileToolService.getFileContent(fileId);
    }

    @Tool("列出所有已上传的文件，返回文件ID、名称和类型")
    @AgentToolPolicy(risk = AgentToolRiskLevel.LOW, readOnly = true, idempotent = true, retryable = true,
            timeoutMs = 10000, maxAttempts = 2, requiredPermission = "app:use", maxResultLength = 8000)
    public String listFiles() {
        return agentFileToolService.listFiles();
    }

    @Tool("获取当前会话的对话历史摘要")
    @AgentToolPolicy(risk = AgentToolRiskLevel.MEDIUM, readOnly = true, idempotent = true, retryable = true,
            timeoutMs = 10000, maxAttempts = 2, requiredPermission = "app:use", maxResultLength = 12000)
    public String getConversationHistory(@P("会话ID") String sessionId) {
        return agentConversationToolService.getConversationHistory(sessionId);
    }

    @Tool("当无法完成用户请求时，向用户请求更多信息或数据")
    @AgentToolPolicy(risk = AgentToolRiskLevel.LOW, readOnly = true, idempotent = true, retryable = false,
            timeoutMs = 5000, maxAttempts = 1, requiredPermission = "app:use", maxResultLength = 4000)
    public String askUserForInfo(@P("向用户展示的请求信息") String message) {
        return agentConversationToolService.askUserForInfo(message);
    }

    @Tool("搜索相关的历史对话、文件内容和知识，实现长期记忆")
    @AgentToolPolicy(risk = AgentToolRiskLevel.MEDIUM, readOnly = true, idempotent = true, retryable = true,
            timeoutMs = 20000, maxAttempts = 2, requiredPermission = "app:use", maxResultLength = 12000)
    public String searchMemory(@P("搜索关键词") String query) {
        return agentKnowledgeToolService.searchMemory(query);
    }

    @Tool("执行数学计算表达式。支持加减乘除、括号、求幂等")
    @AgentToolPolicy(risk = AgentToolRiskLevel.LOW, readOnly = true, idempotent = true, retryable = false,
            timeoutMs = 5000, maxAttempts = 1, requiredPermission = "app:use", maxResultLength = 4000)
    public String calculate(@P("数学表达式，如 (100+200)*0.8") String expression) {
        return agentUtilityToolService.calculate(expression);
    }

    @Tool("获取当前时间和日期信息。无需参数。")
    @AgentToolPolicy(risk = AgentToolRiskLevel.LOW, readOnly = true, idempotent = false, retryable = false,
            timeoutMs = 5000, maxAttempts = 1, requiredPermission = "app:use", maxResultLength = 4000)
    public String getCurrentTime() {
        return agentUtilityToolService.getCurrentTime();
    }

    @Tool("对文件数据进行统计分析摘要(行数、列数、数值列的均值/最大/最小/求和)")
    @AgentToolPolicy(risk = AgentToolRiskLevel.MEDIUM, readOnly = true, idempotent = true, retryable = true,
            timeoutMs = 30000, maxAttempts = 2, requiredPermission = "app:use", maxResultLength = 16000)
    public String analyzeFileData(@P("文件ID") String fileId) {
        return agentFileToolService.analyzeFileData(fileId);
    }

    @Tool("搜索知识库中的专业知识和文档。与 searchMemory 不同，此工具专注于搜索已索引的知识文档")
    @AgentToolPolicy(risk = AgentToolRiskLevel.MEDIUM, readOnly = true, idempotent = true, retryable = true,
            timeoutMs = 20000, maxAttempts = 2, requiredPermission = "app:use", maxResultLength = 12000)
    public String searchKnowledge(@P("搜索内容") String query) {
        return agentKnowledgeToolService.searchKnowledge(query);
    }

    @Tool("查看向量记忆库的状态统计，了解已索引的数据量和类型分布。无需参数。")
    @AgentToolPolicy(risk = AgentToolRiskLevel.LOW, readOnly = true, idempotent = true, retryable = true,
            timeoutMs = 10000, maxAttempts = 2, requiredPermission = "app:use", maxResultLength = 8000)
    public String getMemoryStats() {
        return agentKnowledgeToolService.getMemoryStats();
    }

    @Tool("获取指定数据源的数据库 Schema（所有表名、字段名、字段类型），执行 SQL 前应先调用此工具了解表结构")
    @AgentToolPolicy(risk = AgentToolRiskLevel.MEDIUM, readOnly = true, idempotent = true, retryable = true,
            timeoutMs = 20000, maxAttempts = 2, requiredPermission = "app:use", maxResultLength = 16000)
    public String getDatabaseSchema(@P("数据源名称") String datasourceName) {
        return agentDataSourceToolService.getDatabaseSchema(datasourceName);
    }

    @Tool("列出所有可用的外部数据源（数据库连接）。无需参数。")
    @AgentToolPolicy(risk = AgentToolRiskLevel.MEDIUM, readOnly = true, idempotent = true, retryable = true,
            timeoutMs = 10000, maxAttempts = 2, requiredPermission = "app:use", maxResultLength = 8000)
    public String listDataSources() {
        return agentDataSourceToolService.listDataSources();
    }

    @Tool("在指定数据源上执行 SQL 查询（仅支持 SELECT）")
    @AgentToolPolicy(risk = AgentToolRiskLevel.HIGH, readOnly = true, idempotent = true, retryable = false,
            timeoutMs = 30000, maxAttempts = 1, requiredPermission = "app:use", maxResultLength = 20000)
    public String executeSql(@P("数据源名称") String datasourceName, @P("SELECT SQL 语句") String sql) {
        return agentDataSourceToolService.executeSql(datasourceName, sql);
    }

    @Tool("预览指定数据源的数据。支持数据库首表预览、HTTP/JSON/CSV/TEXT URL 预览")
    @AgentToolPolicy(risk = AgentToolRiskLevel.MEDIUM, readOnly = true, idempotent = false, retryable = false,
            timeoutMs = 30000, maxAttempts = 1, requiredPermission = "app:use", maxResultLength = 16000)
    public String previewDataSource(@P("数据源名称或ID") String datasourceName) {
        return agentDataSourceToolService.previewDataSource(datasourceName);
    }

    @Tool("根据数据生成 ECharts 图表配置，前端直接渲染")
    @AgentToolPolicy(risk = AgentToolRiskLevel.LOW, readOnly = true, idempotent = true, retryable = false,
            timeoutMs = 10000, maxAttempts = 1, requiredPermission = "app:use", maxResultLength = 20000)
    public String generateChart(@P("图表类型: bar/line/pie/scatter") String chartType,
            @P("数据JSON字符串") String dataJson, @P("图表标题") String title) {
        return agentChartToolService.generateChart(chartType, dataJson, title);
    }

    @Tool("在隔离演示沙箱中修改指定酒店房型日期的价格。用户明确要求执行且参数齐全时直接调用；"
            + "服务端会自动创建管理员审批，不要在聊天中要求用户再次确认。该动作不连接真实酒店系统")
    @AgentToolPolicy(risk = AgentToolRiskLevel.HIGH, readOnly = false, idempotent = true, retryable = false,
            timeoutMs = 10000, maxAttempts = 1, requiredPermission = "app:use",
            approvalRequired = true, approvalPermission = "agent:approval:review", maxResultLength = 8000)
    public String updateHotelPrice(@P("演示酒店ID") String hotelId,
            @P("演示房型") String roomType,
            @P("入住日期，格式 YYYY-MM-DD") String stayDate,
            @P("新价格，单位人民币元") double newPrice) {
        return agentSandboxToolService.updateHotelPrice(hotelId, roomType, stayDate, newPrice);
    }

    // [Day5 学习] 自定义工具：查询酒店出租率（mock 数据）。
    // description 是模型选工具的唯一依据，故写清"做什么 + 何时用 + 涉及哪些指标关键词"，
    // 让模型在用户问"某城市某时间的出租率/入住率"时能语义匹配到本工具。
    @Tool("查询并分析指定城市、指定日期的酒店经营表现。当用户询问某地某时间的酒店出租率、"
            + "入住率、RevPAR、ADR、预订趋势、经营诊断、是否需要调价等酒店经营指标时使用此工具")
    @AgentToolPolicy(risk = AgentToolRiskLevel.LOW, readOnly = true, idempotent = true, retryable = false,
            timeoutMs = 5000, maxAttempts = 1, requiredPermission = "app:use", maxResultLength = 8000)
    public String queryHotelOccupancy(@P("城市名称，如 杭州") String city,
            @P("日期或时间范围，如 上周 / 2026-06-10") String date) {
        HotelKpiSnapshot current = buildHotelKpiSnapshot(city, date);
        HotelKpiSnapshot previous = current.previousPeriod();
        double occupancyChange = current.occupancy() - previous.occupancy();
        double revparChange = current.revpar() - previous.revpar();
        String diagnosis = diagnoseHotelPerformance(current, previous);
        return String.format(
                "【%s · %s 酒店经营分析(mock)】%n"
                        + "经营问题: 该城市该时间段酒店表现如何，是否需要调价或促销？%n%n"
                        + "核心 KPI%n"
                        + "- 出租率(Occupancy): %.1f%%，环比%+.1fpp%n"
                        + "- 平均房价(ADR): %.0f 元，环比%+.0f 元%n"
                        + "- 每可售房收入(RevPAR): %.1f 元，环比%+.1f 元%n"
                        + "- 预订提前期: %.1f 天，取消率: %.1f%%%n%n"
                        + "KPI 定义%n"
                        + "- 出租率 = 已售间夜 / 可售间夜，衡量客房卖出去的比例%n"
                        + "- ADR = 房费收入 / 已售间夜，衡量卖出去客房的平均价格%n"
                        + "- RevPAR = ADR * 出租率，衡量所有可售客房的综合收益能力%n%n"
                        + "领域知识%n"
                        + "- 出租率高但 ADR 低: 可能价格偏保守，应评估提价空间%n"
                        + "- ADR 高但出租率低: 可能价格压制需求，应观察竞品和促销弹性%n"
                        + "- RevPAR 下滑: 需要同时拆解价格、入住、取消率和提前期%n%n"
                        + "诊断结论: %s%n"
                        + "建议动作: %s%n"
                        + "数据来源: 模拟经营数据，用于 Day14 酒店分析 Agent 能力演示",
                current.city(), current.date(), current.occupancy(), occupancyChange,
                current.adr(), current.adr() - previous.adr(), current.revpar(), revparChange,
                current.bookingLeadDays(), current.cancelRate(), diagnosis,
                recommendHotelAction(current, previous));
    }

    private HotelKpiSnapshot buildHotelKpiSnapshot(String city, String date) {
        String normalizedCity = city == null || city.isBlank() ? "未知城市" : city.trim();
        String normalizedDate = date == null || date.isBlank() ? "最近7天" : date.trim();
        int seed = Math.abs((normalizedCity + normalizedDate).hashCode());
        double occupancy = 62.0 + seed % 240 / 10.0;
        double adr = 360.0 + seed % 180;
        double bookingLeadDays = 2.0 + seed % 45 / 10.0;
        double cancelRate = 4.0 + seed % 90 / 10.0;
        return new HotelKpiSnapshot(normalizedCity, normalizedDate, occupancy, adr, bookingLeadDays, cancelRate);
    }

    private String diagnoseHotelPerformance(HotelKpiSnapshot current, HotelKpiSnapshot previous) {
        if (current.revpar() >= previous.revpar() && current.occupancy() >= previous.occupancy()) {
            return "RevPAR 与出租率同步提升，需求健康，当前经营节奏偏积极。";
        }
        if (current.occupancy() >= 78.0 && current.adr() < previous.adr()) {
            return "出租率较高但 ADR 走弱，可能存在低价换量，收益仍有优化空间。";
        }
        if (current.occupancy() < 68.0 && current.adr() > previous.adr()) {
            return "ADR 较高但出租率不足，价格可能压制了转化，需要关注竞品价差。";
        }
        return "核心指标有分化，需要结合流量、竞品价格和取消率继续拆解。";
    }

    private String recommendHotelAction(HotelKpiSnapshot current, HotelKpiSnapshot previous) {
        if (current.occupancy() >= 78.0 && current.revpar() >= previous.revpar()) {
            return "保留基础促销，优先测试小幅提价 3%-5%，并监控转化率变化。";
        }
        if (current.occupancy() < 68.0) {
            return "针对低入住日期加限时券或套餐权益，先拉升出租率，再评估 ADR 修复。";
        }
        if (current.cancelRate() >= 10.0) {
            return "检查取消政策和预付产品占比，降低高取消订单对 RevPAR 的扰动。";
        }
        return "维持当前价格带，按入住提前期滚动观察未来 7 天预订趋势。";
    }

    private record HotelKpiSnapshot(String city, String date, double occupancy, double adr,
            double bookingLeadDays, double cancelRate) {

        private double revpar() {
            return occupancy / 100 * adr;
        }

        private HotelKpiSnapshot previousPeriod() {
            return new HotelKpiSnapshot(city, "上一周期", Math.max(45.0, occupancy - 3.8),
                    Math.max(260.0, adr - 18.0), Math.max(1.0, bookingLeadDays - 0.6),
                    Math.max(2.0, cancelRate - 1.2));
        }
    }

}
