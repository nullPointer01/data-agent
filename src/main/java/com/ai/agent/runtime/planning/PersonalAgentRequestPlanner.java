package com.ai.agent.runtime.planning;

import com.ai.agent.AgentExecutionContext;
import com.ai.agent.capability.AgentCapabilityBindingSnapshot;
import com.ai.agent.capability.AgentCapabilityDescriptor;
import com.ai.agent.capability.AgentCapabilityService;
import com.ai.agent.capability.AgentCapabilityType;
import com.ai.agent.runtime.AgentExecutionMode;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 个人 Agent 的请求级规划器。
 *
 * <p>采用高确定性规则优先、低置信度保守回到 Chat 的分层策略。规划结果一次性决定
 * 模型、RAG、记忆和候选工具需求，避免执行链路中的组件各自重复猜测。</p>
 *
 * @author data-agent
 */
@Component
public class PersonalAgentRequestPlanner {

    private static final List<String> CAPABILITY_PATTERNS = List.of(
            "你是谁", "你能干什么", "你能做什么", "你可以做什么", "你会什么",
            "有什么能力", "有哪些能力", "有什么技能", "有哪些技能", "有哪些工具",
            "介绍一下你的能力", "介绍一下你自己");
    private static final List<String> GENERAL_KNOWLEDGE_PATTERNS = List.of(
            "什么是", "是什么", "是什么意思", "有什么区别", "为什么", "怎么", "如何",
            "解释一下", "介绍一下", "原理");
    private static final List<String> PERSONAL_KNOWLEDGE_PATTERNS = List.of(
            "我的知识库", "知识库", "公司", "内部", "制度", "规定", "政策", "手册",
            "文档中", "文档里", "资料中", "资料里", "根据文档", "项目中", "项目里", "规范");
    private static final List<String> ACCOUNT_CONTEXT_PATTERNS = List.of(
            "账号", "账户", "登录", "密码", "验证码", "手机号", "手机", "锁定", "风控");
    private static final List<String> POLICY_QUESTION_PATTERNS = List.of(
            "限制", "几次", "次数", "失败", "触发", "多久", "冻结", "解锁", "规则");
    private static final List<String> FILE_PATTERNS = List.of(
            "文件", "附件", "上传", "excel", "csv", "pdf", "表格");
    private static final List<String> FILE_ACTION_PATTERNS = List.of(
            "分析", "读取", "打开", "查看", "看看", "总结", "提取", "解析", "上传", "处理");
    private static final List<String> DATA_PATTERNS = List.of(
            "数据", "指标", "数据库", "sql", "schema", "字段", "订单", "销售", "利润", "gmv",
            "酒店", "入住率", "出租率", "revpar", "adr", "图表", "可视化", "报表");
    private static final List<String> DATA_REQUEST_PATTERNS = List.of(
            "查询", "查看", "统计", "分析", "比较", "计算", "生成", "多少", "趋势", "同比", "环比",
            "增长", "下降", "本月", "本周", "今天", "昨天");
    private static final List<String> STRONG_SIDE_EFFECT_PATTERNS = List.of(
            "删除", "提交", "发送", "发布", "调价", "价格改成", "付款", "支付", "退款", "下单", "执行操作");
    private static final List<String> CONDITIONAL_SIDE_EFFECT_PATTERNS = List.of(
            "修改", "更新", "新增", "创建", "设置", "变更");
    private static final List<String> OPERATIONAL_TARGET_PATTERNS = List.of(
            "酒店价格", "房价", "订单", "用户", "账号", "账户", "权限", "角色", "数据源", "数据库记录",
            "知识库", "文件", "表格", "邮件", "消息", "任务", "日程", "审批", "配置项");
    private static final List<String> FOLLOW_UP_PATTERNS = List.of(
            "刚才", "刚刚", "上面", "前面", "继续", "接着", "这个", "那个", "它", "他们");
    private static final List<String> TASK_VERBS = List.of(
            "查询", "搜索", "读取", "分析", "比较", "计算", "生成", "总结", "修改", "更新", "发送");

    private final AgentCapabilityService capabilityService;
    private final AgentToolCandidateSelector toolSelector;

    public PersonalAgentRequestPlanner(AgentCapabilityService capabilityService,
            AgentToolCandidateSelector toolSelector) {
        this.capabilityService = capabilityService;
        this.toolSelector = toolSelector;
    }

    /**
     * 为配置 Agent 生成一次不可变执行计划。
     */
    public RequestExecutionPlan plan(AgentProfile profile, AgentExecutionContext executionContext) {
        if (profile == null || executionContext == null || executionContext.getRequest() == null) {
            throw new IllegalArgumentException("请求规划缺少 Agent 或执行上下文");
        }
        PlanningInput input = new PlanningInput(
                normalize(executionContext.getRequest().getQuestion()),
                executionContext.getRequest(),
                StringUtils.hasText(executionContext.getFileContent()),
                hasConversationHistory(executionContext));
        IntentDecision decision = matchDecision(input);
        AgentCapabilityBindingSnapshot prefetchedReactSnapshot = null;
        List<AgentCapabilityDescriptor> prefetchedSkills = List.of();
        if (decision.intent() == RequestIntent.GENERAL_CONVERSATION && hasConfiguredSkill(profile)) {
            prefetchedReactSnapshot = capabilityService.resolveRuntimeBindings(profile, AgentExecutionMode.REACT);
            prefetchedSkills = boundSkillDescriptors(profile, prefetchedReactSnapshot);
            if (toolSelector.shouldUseBoundSkill(input.question(), prefetchedSkills)) {
                decision = skillTask();
            }
        }
        AgentExecutionMode mode = resolveMode(profile, decision);

        if (!decision.modelRequired()) {
            return buildLocalPlan(profile, decision);
        }

        boolean conversationContextRequired = requiresConversationContext(input);
        AgentCapabilityBindingSnapshot snapshot = mode == AgentExecutionMode.CHAT
                ? null : mode == AgentExecutionMode.REACT && prefetchedReactSnapshot != null
                        ? prefetchedReactSnapshot
                        : capabilityService.resolveRuntimeBindings(profile, mode);
        List<AgentCapabilityDescriptor> boundSkills = snapshot == null || snapshot.skillIds().isEmpty()
                ? List.of()
                : !prefetchedSkills.isEmpty() ? prefetchedSkills : boundSkillDescriptors(profile, snapshot);
        AgentToolCandidateSelector.Selection selection = snapshot == null
                ? new AgentToolCandidateSelector.Selection(Set.of(), false, "Chat 模式不暴露工具")
                : toolSelector.select(
                        input.question(),
                        decision.intent(),
                        snapshot.toolNames(),
                        boundSkills,
                        !snapshot.subAgentIds().isEmpty());

        Set<String> candidateTools = mode == AgentExecutionMode.CHAT
                ? Set.of() : selection.candidateTools();
        AgentToolChoice initialToolChoice = decision.intent() == RequestIntent.ACTION
                && !selection.fallback()
                && candidateTools.size() == 1
                ? AgentToolChoice.REQUIRED
                : AgentToolChoice.AUTO;
        return new RequestExecutionPlan(
                RequestExecutionPlan.CURRENT_VERSION,
                decision.intent(),
                mode,
                true,
                decision.ragRequired(),
                conversationContextRequired,
                conversationContextRequired,
                candidateTools,
                mode != AgentExecutionMode.CHAT && selection.fallback(),
                initialToolChoice,
                decision.confidence(),
                decision.rule(),
                decision.reason() + "；" + selection.reason(),
                "");
    }

    private IntentDecision matchDecision(PlanningInput input) {
        List<IntentRule> rules = List.of(
                this::capabilityIntroduction,
                this::writeAction,
                this::fileAnalysis,
                this::multiStepTask,
                this::dataQuery,
                this::personalKnowledge,
                this::generalKnowledge,
                this::generalConversation);
        for (IntentRule rule : rules) {
            Optional<IntentDecision> decision = rule.match(input);
            if (decision.isPresent()) {
                return decision.get();
            }
        }
        throw new IllegalStateException("请求规划规则链缺少兜底规则");
    }

    private Optional<IntentDecision> capabilityIntroduction(PlanningInput input) {
        if (input.question().length() <= 40 && containsAny(input.question(), CAPABILITY_PATTERNS)) {
            return Optional.of(new IntentDecision(
                    RequestIntent.CAPABILITY_INTRODUCTION,
                    AgentExecutionMode.CHAT,
                    false,
                    false,
                    0.99D,
                    "capability-introduction",
                    "用户询问当前 Agent 的身份或能力"));
        }
        return Optional.empty();
    }

    private Optional<IntentDecision> fileAnalysis(PlanningInput input) {
        if (input.request().hasFile() || input.fileContentPresent()
                || (containsAny(input.question(), FILE_PATTERNS)
                && containsAny(input.question(), FILE_ACTION_PATTERNS))) {
            return Optional.of(new IntentDecision(
                    RequestIntent.FILE_ANALYSIS,
                    AgentExecutionMode.REACT,
                    true,
                    false,
                    0.94D,
                    "file-analysis",
                    "请求携带文件或明确要求处理文件"));
        }
        return Optional.empty();
    }

    private Optional<IntentDecision> writeAction(PlanningInput input) {
        if (isSideEffectAction(input.question())) {
            return Optional.of(new IntentDecision(
                    RequestIntent.ACTION,
                    AgentExecutionMode.REACT,
                    true,
                    false,
                    0.92D,
                    "side-effect-action",
                    "请求包含可能产生副作用的动作，交由受治理工具路径处理"));
        }
        return Optional.empty();
    }

    private Optional<IntentDecision> multiStepTask(PlanningInput input) {
        long taskVerbCount = TASK_VERBS.stream().filter(input.question()::contains).distinct().count();
        boolean sequenceSignal = containsAny(input.question(),
                List.of("然后", "接着", "最后", "同时", "分别", "并且", "并"));
        if (taskVerbCount >= 3 || (taskVerbCount >= 2 && sequenceSignal)) {
            return Optional.of(new IntentDecision(
                    RequestIntent.MULTI_STEP,
                    AgentExecutionMode.ORCHESTRATED,
                    true,
                    isPersonalKnowledgeQuestion(input.question()),
                    0.88D,
                    "multi-step-task",
                    "请求包含多个目标，需要分步执行或协作"));
        }
        return Optional.empty();
    }

    private Optional<IntentDecision> dataQuery(PlanningInput input) {
        boolean hasDataSignal = containsAny(input.question(), DATA_PATTERNS);
        boolean hasDataRequest = containsAny(input.question(), DATA_REQUEST_PATTERNS);
        boolean conceptOnly = containsAny(input.question(), GENERAL_KNOWLEDGE_PATTERNS) && !hasDataRequest;
        if (hasDataSignal && !conceptOnly) {
            return Optional.of(new IntentDecision(
                    RequestIntent.DATA_QUERY,
                    AgentExecutionMode.REACT,
                    true,
                    false,
                    0.90D,
                    "structured-data-query",
                    "请求依赖结构化数据、业务指标或图表能力"));
        }
        return Optional.empty();
    }

    private Optional<IntentDecision> personalKnowledge(PlanningInput input) {
        if (isPersonalKnowledgeQuestion(input.question())) {
            return Optional.of(new IntentDecision(
                    RequestIntent.PERSONAL_KNOWLEDGE,
                    AgentExecutionMode.CHAT,
                    true,
                    true,
                    0.89D,
                    "personal-knowledge-query",
                    "问题需要从个人知识库获取可引用证据"));
        }
        return Optional.empty();
    }

    private Optional<IntentDecision> generalKnowledge(PlanningInput input) {
        if (containsAny(input.question(), GENERAL_KNOWLEDGE_PATTERNS)) {
            return Optional.of(new IntentDecision(
                    RequestIntent.GENERAL_KNOWLEDGE,
                    AgentExecutionMode.CHAT,
                    true,
                    false,
                    0.84D,
                    "general-knowledge",
                    "通用概念问题不需要个人知识库或工具"));
        }
        return Optional.empty();
    }

    private Optional<IntentDecision> generalConversation(PlanningInput input) {
        return Optional.of(new IntentDecision(
                RequestIntent.GENERAL_CONVERSATION,
                AgentExecutionMode.CHAT,
                true,
                false,
                input.historyPresent() ? 0.78D : 0.70D,
                "default-chat",
                "没有可靠的工具或私有知识信号，保守使用普通对话"));
    }

    private IntentDecision skillTask() {
        return new IntentDecision(
                RequestIntent.SKILL_TASK,
                AgentExecutionMode.REACT,
                true,
                false,
                0.86D,
                "bound-skill-match",
                "请求命中当前 Agent 已绑定且可用的 Skill");
    }

    private AgentExecutionMode resolveMode(AgentProfile profile, IntentDecision decision) {
        if (!decision.modelRequired()) {
            return AgentExecutionMode.CHAT;
        }
        if (StringUtils.hasText(profile.getExecutionMode()) && !profile.isAutoMode()) {
            return AgentExecutionMode.fromCode(profile.getExecutionMode());
        }
        if (decision.preferredMode() != AgentExecutionMode.ORCHESTRATED) {
            return decision.preferredMode();
        }
        boolean hasUsableSubAgent = !capabilityService
                .resolveRuntimeBindings(profile, AgentExecutionMode.ORCHESTRATED)
                .subAgentIds()
                .isEmpty();
        return hasUsableSubAgent ? AgentExecutionMode.ORCHESTRATED : AgentExecutionMode.REACT;
    }

    private List<AgentCapabilityDescriptor> boundSkillDescriptors(
            AgentProfile profile, AgentCapabilityBindingSnapshot snapshot) {
        Set<String> boundIdentities = snapshot.skillIds().stream()
                .map(AgentCapabilityType.SKILL::identity)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return capabilityService.runtimeDirectory(profile).capabilities().stream()
                .filter(descriptor -> descriptor.type() == AgentCapabilityType.SKILL)
                .filter(descriptor -> boundIdentities.contains(descriptor.identity()))
                .toList();
    }

    private RequestExecutionPlan buildLocalPlan(AgentProfile profile, IntentDecision decision) {
        return new RequestExecutionPlan(
                RequestExecutionPlan.CURRENT_VERSION,
                decision.intent(),
                AgentExecutionMode.CHAT,
                false,
                false,
                false,
                false,
                Set.of(),
                false,
                AgentToolChoice.AUTO,
                decision.confidence(),
                decision.rule(),
                decision.reason(),
                buildCapabilityIntroduction(profile));
    }

    private String buildCapabilityIntroduction(AgentProfile profile) {
        AgentCapabilityBindingSnapshot snapshot = capabilityService
                .resolveRuntimeBindings(profile, AgentExecutionMode.ORCHESTRATED);
        Map<String, AgentCapabilityDescriptor> directory = new LinkedHashMap<>();
        capabilityService.runtimeDirectory(profile).capabilities()
                .forEach(descriptor -> directory.put(descriptor.identity(), descriptor));

        List<String> capabilities = new ArrayList<>();
        capabilities.add("基于你的个人知识库回答，并给出可核对的引用");
        appendToolCapabilitySummary(capabilities, snapshot.toolNames());
        appendNamedCapabilities(capabilities, "使用已绑定技能：", snapshot.skillIds(),
                AgentCapabilityType.SKILL, directory);
        appendNamedCapabilities(capabilities, "将复杂任务委派给：", snapshot.subAgentIds(),
                AgentCapabilityType.SUB_AGENT, directory);

        StringBuilder answer = new StringBuilder("我是 ")
                .append(textOrDefault(profile.getName(), "你的个人 Agent"))
                .append("。");
        if (StringUtils.hasText(profile.getDescription())) {
            answer.append(shorten(profile.getDescription(), 120)).append("\n");
        } else {
            answer.append("\n");
        }
        answer.append("\n我目前可以：\n");
        capabilities.forEach(item -> answer.append("- ").append(item).append("\n"));
        answer.append("\n我会按问题选择最小必要的知识、记忆和工具；涉及高风险动作时，会先进入管理员审批。");
        return answer.toString();
    }

    private void appendToolCapabilitySummary(List<String> summaries, Set<String> tools) {
        if (tools == null || tools.isEmpty()) {
            return;
        }
        if (containsAny(tools, Set.of("getFileContent", "listFiles", "analyzeFileData"))) {
            summaries.add("读取并分析你上传的文件");
        }
        if (containsAny(tools, Set.of("getDatabaseSchema", "listDataSources", "executeSql",
                "previewDataSource", "queryHotelOccupancy"))) {
            summaries.add("查询和分析结构化数据与业务指标");
        }
        if (tools.contains("generateChart")) {
            summaries.add("根据分析结果生成图表");
        }
        if (containsAny(tools, Set.of("calculate", "getCurrentTime", "searchMemory"))) {
            summaries.add("执行计算、时间查询和历史上下文检索");
        }
        if (tools.contains("updateHotelPrice")) {
            summaries.add("执行已授权的酒店调价演示，动作前需要审批");
        }
    }

    private void appendNamedCapabilities(List<String> summaries,
            String prefix,
            Set<String> sourceIds,
            AgentCapabilityType type,
            Map<String, AgentCapabilityDescriptor> directory) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return;
        }
        List<String> names = sourceIds.stream()
                .map(type::identity)
                .map(directory::get)
                .filter(java.util.Objects::nonNull)
                .map(AgentCapabilityDescriptor::name)
                .filter(StringUtils::hasText)
                .limit(4)
                .toList();
        if (!names.isEmpty()) {
            summaries.add(prefix + String.join("、", names));
        }
    }

    private boolean requiresConversationContext(PlanningInput input) {
        return (input.historyPresent() && containsAny(input.question(), FOLLOW_UP_PATTERNS))
                || (input.historyPresent() && input.question().length() <= 20
                && input.question().matches(".*[这那它他她].*"));
    }

    private boolean isPersonalKnowledgeQuestion(String question) {
        return containsAny(question, PERSONAL_KNOWLEDGE_PATTERNS)
                || (containsAny(question, ACCOUNT_CONTEXT_PATTERNS)
                && containsAny(question, POLICY_QUESTION_PATTERNS));
    }

    private boolean isSideEffectAction(String question) {
        if (containsAny(question, GENERAL_KNOWLEDGE_PATTERNS)) {
            return false;
        }
        return containsAny(question, STRONG_SIDE_EFFECT_PATTERNS)
                || (containsAny(question, CONDITIONAL_SIDE_EFFECT_PATTERNS)
                && containsAny(question, OPERATIONAL_TARGET_PATTERNS));
    }

    private boolean hasConfiguredSkill(AgentProfile profile) {
        return capabilityService.hasConfiguredSkill(profile);
    }

    private boolean hasConversationHistory(AgentExecutionContext context) {
        return context.getSession() != null
                && context.getSession().getHistory() != null
                && !context.getSession().getHistory().isEmpty();
    }

    private static boolean containsAny(String value, List<String> patterns) {
        return patterns.stream().anyMatch(value::contains);
    }

    private static boolean containsAny(Set<String> values, Set<String> expected) {
        return values.stream().anyMatch(expected::contains);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String textOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static String shorten(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }

    @FunctionalInterface
    private interface IntentRule {
        Optional<IntentDecision> match(PlanningInput input);
    }

    private record PlanningInput(
            String question,
            AnalysisRequest request,
            boolean fileContentPresent,
            boolean historyPresent) {
    }

    private record IntentDecision(
            RequestIntent intent,
            AgentExecutionMode preferredMode,
            boolean modelRequired,
            boolean ragRequired,
            double confidence,
            String rule,
            String reason) {
    }
}
