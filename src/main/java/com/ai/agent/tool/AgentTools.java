package com.ai.agent.tool;

import com.ai.agent.tool.governance.AgentToolPolicy;
import com.ai.agent.tool.governance.AgentToolRiskLevel;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * [正式功能] 供 Agent 模型调用的统一工具门面。
 *
 * <p>本类只负责声明工具语义和转发调用，实际执行还会经过能力绑定、RBAC、风险策略、
 * 预算、超时和审批等工具治理。本类中除“酒店演示工具”分组外，其他方法均连接项目内真实服务。</p>
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

    /** 判断工具是否为受控子 Agent 委派适配器。 */
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

    // ==================== [正式功能] Skill 与子 Agent 委派 ====================

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

    // ==================== [正式功能] 文件、会话与记忆 ====================

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

    // ==================== [正式功能] 通用计算与时间 ====================

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

    // ==================== [正式功能] 文件分析、知识检索与统计 ====================

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

    // ==================== [正式功能] 外部数据源访问 ====================

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

    // ==================== [正式功能] 图表生成 ====================

    @Tool("根据数据生成 ECharts 图表配置，前端直接渲染")
    @AgentToolPolicy(risk = AgentToolRiskLevel.LOW, readOnly = true, idempotent = true, retryable = false,
            timeoutMs = 10000, maxAttempts = 1, requiredPermission = "app:use", maxResultLength = 20000)
    public String generateChart(@P("图表类型: bar/line/pie/scatter") String chartType,
            @P("数据JSON字符串") String dataJson, @P("图表标题") String title) {
        return agentChartToolService.generateChart(chartType, dataJson, title);
    }

    // ==================== [演示功能] 酒店业务验收 ====================

    /**
     * [演示功能][隔离写入] 验证高风险工具的申请、审批、恢复执行和幂等链路。
     *
     * <p>审批和持久化恢复是真实功能；最终只写入 {@code hotel_rate_sandbox}，
     * 不调用真实酒店改价接口，不得解读为生产改价能力。</p>
     */
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

}
