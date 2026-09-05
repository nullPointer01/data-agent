package com.ai.agent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 不访问应用状态的通用确定性工具。
 *
 * @author data-agent
 */
@Service
public class AgentUtilityToolService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentUtilityToolService.class);
    private static final DateTimeFormatter CURRENT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (EEEE)");
    private static final String UNSUPPORTED_EXPRESSION_PATTERN = "[^0-9+\\-*/().,%^ ]";
    private static final String POWER_OPERATOR = "^";
    private static final String INTERNAL_POWER_OPERATOR = "**";
    private static final Pattern PERCENT_NUMBER_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)%");
    private static final String PERCENT_NUMBER_REPLACEMENT = "($1/100.0)";

    /**
     * 计算经过安全清洗的数学表达式。
     *
     * @param expression 模型传入的数学表达式
     * @return 格式化后的计算结果
     */
    public String calculate(String expression) {
        LOGGER.info("Agent calculating: expressionLength={}", expression == null ? 0 : expression.length());
        try {
            String sanitized = sanitizeExpression(expression);
            if (sanitized.isEmpty()) {
                return "无效的数学表达式";
            }

            double result = new MathExpressionEvaluator(sanitized).evaluate();
            if (result == Math.floor(result) && !Double.isInfinite(result)) {
                return expression + " = " + String.format("%.0f", result);
            }
            return expression + " = " + String.format("%.4f", result);
        } catch (Exception e) {
            throw new IllegalArgumentException("数学表达式格式错误", e);
        }
    }

    /**
     * 获取模型易读的当前本地时间。
     *
     * @return 当前时间文本
     */
    public String getCurrentTime() {
        LocalDateTime now = LocalDateTime.now();
        return "当前时间: " + now.format(CURRENT_TIME_FORMATTER);
    }

    private String sanitizeExpression(String expression) {
        if (expression == null) {
            return "";
        }
        // 只保留数学运算 token，避免模型传入表达式外的内容。
        String arithmeticOnly = expression.replaceAll(UNSUPPORTED_EXPRESSION_PATTERN, "")
                .replace(POWER_OPERATOR, INTERNAL_POWER_OPERATOR)
                .trim();
        Matcher matcher = PERCENT_NUMBER_PATTERN.matcher(arithmeticOnly);
        return matcher.replaceAll(PERCENT_NUMBER_REPLACEMENT);
    }
}
