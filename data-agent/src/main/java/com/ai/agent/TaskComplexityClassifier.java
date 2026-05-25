package com.ai.agent;

import com.ai.model.AnalysisRequest;
import com.ai.model.ConversationSession;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 在选择直答或完整 ReAct 执行前对用户请求做分类。
 *
 * <p>分类策略保持保守：只有问候和纯概念解释进入直答路径。业务分析、文件、数据源和
 * 依赖会话上下文的问题都保留在推理循环中。</p>
 *
 * @author data-agent
 */
@Component
public class TaskComplexityClassifier {

    private static final int MAX_FAST_PATH_QUERY_LENGTH = 80;
    private static final List<String> GREETING_PATTERNS = List.of(
            "你好", "您好", "hello", "hi", "在吗", "你是谁", "介绍一下你自己");
    private static final List<String> DIRECT_QUESTION_PREFIXES = List.of(
            "什么是", "解释一下", "简单介绍", "介绍一下", "帮我解释");
    private static final List<String> ACTION_REQUIRED_KEYWORDS = List.of(
            "分析", "统计", "计算", "图表", "画图", "查询", "执行", "运行", "报表", "趋势", "同比", "环比");
    private static final List<String> DATA_DEPENDENT_KEYWORDS = List.of(
            "文件", "上传", "公司", "客户", "订单", "销售", "利润", "gmv", "excel", "csv");

    /**
     * 结合运行时上下文对请求进行分类。
     *
     * @param request 分析请求
     * @param fileContent 可选的已加载文件内容
     * @param session 当前会话
     * @return 任务分类结果
     */
    public TaskClassification classify(AnalysisRequest request, String fileContent, ConversationSession session) {
        if (request == null || !StringUtils.hasText(request.getQuestion())) {
            return TaskClassification.complex("空问题不能直答");
        }
        if (request.hasFile() || StringUtils.hasText(fileContent)) {
            return TaskClassification.toolAssisted("包含文件上下文，需要文件工具处理");
        }
        if (request.hasSkill()) {
            return TaskClassification.toolAssisted("指定技能执行，需要技能工具处理");
        }
        if (request.hasAgent()) {
            return TaskClassification.toolAssisted("指定 Agent 执行，需要运行时路由");
        }
        if (request.isCommand()) {
            return TaskClassification.toolAssisted("命令请求需要专门处理");
        }
        if (session != null && !session.getHistory().isEmpty()) {
            return TaskClassification.complex("存在历史对话，需要上下文推理");
        }

        String question = normalize(request.getQuestion());
        if (question.length() > MAX_FAST_PATH_QUERY_LENGTH) {
            return TaskClassification.complex("问题较长，需要完整推理");
        }
        if (isGreeting(question)) {
            return TaskClassification.simple("问候或身份介绍请求");
        }
        if (startsWithDirectQuestionPrefix(question)) {
            return classifyDirectQuestion(question);
        }
        if (requiresToolOrData(question)) {
            return TaskClassification.toolAssisted("包含业务动作或数据依赖关键词");
        }
        return TaskClassification.complex("默认进入完整推理");
    }

    private TaskClassification classifyDirectQuestion(String question) {
        if (requiresToolOrData(question)) {
            return TaskClassification.toolAssisted("概念问题包含业务动作或数据依赖关键词");
        }
        return TaskClassification.simple("简单概念解释请求");
    }

    private boolean isGreeting(String question) {
        String lowerQuestion = question.toLowerCase(Locale.ROOT);
        return GREETING_PATTERNS.stream().anyMatch(lowerQuestion::contains);
    }

    private boolean startsWithDirectQuestionPrefix(String question) {
        return DIRECT_QUESTION_PREFIXES.stream().anyMatch(question::startsWith);
    }

    private boolean requiresToolOrData(String question) {
        String lowerQuestion = question.toLowerCase(Locale.ROOT);
        return ACTION_REQUIRED_KEYWORDS.stream().anyMatch(lowerQuestion::contains)
                || DATA_DEPENDENT_KEYWORDS.stream().anyMatch(lowerQuestion::contains);
    }

    private String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }
}
