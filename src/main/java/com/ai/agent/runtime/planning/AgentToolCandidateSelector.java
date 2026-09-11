package com.ai.agent.runtime.planning;

import com.ai.agent.capability.AgentCapabilityDescriptor;
import com.ai.agent.tool.AgentTools;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 根据请求目标和工具路由元数据缩小模型可见的工具集合。
 *
 * <p>该选择器只在当前 Agent 已授权的工具集合内选择；低置信度时显式返回 fallback，
 * 由执行器回退到能力绑定，而不是静默丢失可用能力。</p>
 *
 * @author data-agent
 */
@Component
public class AgentToolCandidateSelector {

    private static final int MAX_CANDIDATES = 5;

    private static final List<ToolRoute> ROUTES = List.of(
            route("listFiles", "文件列表", "有哪些文件", "列出文件", "已上传文件"),
            route("getFileContent", "读取文件", "文件内容", "打开文件", "这个文件", "上传文件"),
            route("analyzeFileData", "分析文件", "表格分析", "excel", "csv", "行数", "列数"),
            route("getConversationHistory", "对话历史", "聊天记录", "刚才说", "前面说"),
            route("searchMemory", "记忆", "历史信息", "以前提到", "之前聊"),
            route("calculate", "计算", "算一下", "等于多少", "加减", "乘除", "公式"),
            route("getCurrentTime", "当前时间", "现在几点", "今天日期", "今天几号"),
            route("getDatabaseSchema", "数据库结构", "数据库 schema", "表结构", "字段类型"),
            route("listDataSources", "数据源列表", "有哪些数据源", "列出数据源"),
            route("executeSql", "执行 sql", "select ", "数据库查询", "查数据库"),
            route("previewDataSource", "预览数据源", "数据预览", "看看数据源"),
            route("generateChart", "生成图表", "画图", "柱状图", "折线图", "饼图", "散点图"),
            route("updateHotelPrice", "修改酒店价格", "更新酒店价格", "酒店调价", "房价改", "价格改成"),
            route("queryHotelOccupancy", "酒店入住率", "酒店出租率", "revpar", "adr", "酒店经营", "预订趋势"));

    /**
     * 从已授权能力中选择本次值得发送给模型的候选工具。
     */
    public Selection select(String query,
            RequestIntent intent,
            Set<String> allowedTools,
            List<AgentCapabilityDescriptor> boundSkills,
            boolean subAgentsAvailable) {
        Set<String> safeAllowed = allowedTools == null ? Set.of() : allowedTools;
        List<AgentCapabilityDescriptor> safeSkills = boundSkills == null ? List.of() : boundSkills;
        String normalized = normalize(query);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();

        boolean skillSelected = !safeSkills.isEmpty()
                && (intent == RequestIntent.MULTI_STEP
                || intent == RequestIntent.SKILL_TASK
                || shouldUseBoundSkill(normalized, safeSkills));
        if (skillSelected) {
            candidates.add(AgentTools.LIST_AVAILABLE_SKILLS_TOOL);
            candidates.add(AgentTools.USE_SKILL_TOOL);
        }
        if (subAgentsAvailable && intent == RequestIntent.MULTI_STEP) {
            candidates.add(AgentTools.DELEGATE_TO_AGENT_TOOL);
        }

        ROUTES.stream()
                .filter(route -> safeAllowed.contains(route.toolName()))
                .filter(route -> route.matches(normalized))
                .map(ToolRoute::toolName)
                .forEach(candidates::add);

        addIntentDefaults(intent, normalized, safeAllowed, candidates);

        boolean toolIntent = intent == RequestIntent.FILE_ANALYSIS
                || intent == RequestIntent.DATA_QUERY
                || intent == RequestIntent.SKILL_TASK
                || intent == RequestIntent.ACTION
                || intent == RequestIntent.MULTI_STEP;
        boolean hasBoundCallableCapability = !safeAllowed.isEmpty()
                || !safeSkills.isEmpty()
                || subAgentsAvailable;
        boolean fallback = toolIntent && candidates.isEmpty() && hasBoundCallableCapability;
        if (fallback) {
            return new Selection(Set.of(), true, "未找到高置信度候选能力，回退到 Agent 已授权能力");
        }
        return new Selection(limit(candidates), false,
                candidates.isEmpty() ? "本次请求不需要工具" : selectionReason(skillSelected));
    }

    private void addIntentDefaults(RequestIntent intent,
            String query,
            Set<String> allowed,
            LinkedHashSet<String> candidates) {
        if (intent == RequestIntent.FILE_ANALYSIS) {
            addIfAllowed(allowed, candidates, "getFileContent");
            addIfAllowed(allowed, candidates, "analyzeFileData");
        }
        if (intent == RequestIntent.DATA_QUERY && containsAny(query,
                "酒店", "入住率", "出租率", "revpar", "adr")) {
            addIfAllowed(allowed, candidates, "queryHotelOccupancy");
        }
        if (intent == RequestIntent.DATA_QUERY && containsAny(query,
                "数据库", "sql", "数据源", "表结构", "字段")) {
            addIfAllowed(allowed, candidates, "listDataSources");
            addIfAllowed(allowed, candidates, "getDatabaseSchema");
            addIfAllowed(allowed, candidates, "executeSql");
        }
        if (intent == RequestIntent.ACTION && containsAny(query, "酒店", "房价", "调价")) {
            addIfAllowed(allowed, candidates, "updateHotelPrice");
        }
        if (containsAny(query, "图表", "画图", "可视化", "柱状图", "折线图", "饼图")) {
            addIfAllowed(allowed, candidates, "generateChart");
        }
    }

    private void addIfAllowed(Set<String> allowed, Set<String> candidates, String toolName) {
        if (allowed.contains(toolName)) {
            candidates.add(toolName);
        }
    }

    private Set<String> limit(Set<String> values) {
        if (values.size() <= MAX_CANDIDATES) {
            return Set.copyOf(values);
        }
        return Set.copyOf(new ArrayList<>(values).subList(0, MAX_CANDIDATES));
    }

    private boolean mentionsSkill(String query) {
        return containsAny(query, "skill", "技能", "专项能力");
    }

    /**
     * 判断一个普通请求是否明确指向已绑定 Skill。
     */
    public boolean shouldUseBoundSkill(String query, List<AgentCapabilityDescriptor> skills) {
        String normalized = normalize(query);
        List<AgentCapabilityDescriptor> safeSkills = skills == null ? List.of() : skills;
        return !safeSkills.isEmpty() && (mentionsSkill(normalized)
                || safeSkills.stream().anyMatch(skill -> matchesSkill(normalized, skill)));
    }

    private boolean matchesSkill(String query, AgentCapabilityDescriptor skill) {
        String name = normalize(skill.name());
        String description = normalize(skill.description());
        if (!name.isEmpty() && query.contains(name)) {
            return true;
        }
        return query.length() >= 4 && !description.isEmpty() && description.contains(query);
    }

    private String selectionReason(boolean skillSelected) {
        return skillSelected
                ? "按请求信号筛选候选工具，并命中已绑定 Skill"
                : "按请求信号筛选候选工具";
    }

    private static ToolRoute route(String toolName, String... signals) {
        return new ToolRoute(toolName, List.of(signals));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static boolean containsAny(String value, String... signals) {
        for (String signal : signals) {
            if (value.contains(signal)) {
                return true;
            }
        }
        return false;
    }

    private record ToolRoute(String toolName, List<String> signals) {

        private boolean matches(String query) {
            return signals.stream().anyMatch(query::contains);
        }
    }

    /**
     * @param candidateTools 候选工具；fallback=true 时为空
     * @param fallback 是否需要使用 Agent 已授权工具兜底
     * @param reason 安全选择原因
     */
    public record Selection(Set<String> candidateTools, boolean fallback, String reason) {

        public Selection {
            candidateTools = candidateTools == null ? Set.of() : Set.copyOf(candidateTools);
            reason = reason == null ? "" : reason;
        }
    }
}
