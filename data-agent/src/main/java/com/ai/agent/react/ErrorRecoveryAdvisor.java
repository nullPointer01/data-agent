package com.ai.agent.react;

import org.springframework.stereotype.Component;

import java.util.Locale;
import com.ai.agent.AgentReasoningProperties;

/**
 * 工具观察后的错误恢复建议器。
 *
 * <p>根据工具名称和错误信息判定恢复类型，生成恢复指令供模型自我修正。
 * 同时支持在普通工具结果中追加恢复提示（当结果包含"无数据"类信号时）。</p>
 *
 * @author data-agent
 */
@Component
public class ErrorRecoveryAdvisor {

    private static final String UNKNOWN_TOOL_MARKER = "未知工具";
    private static final String TOOL_FAILURE_MARKER = "工具执行失败";
    private static final String SQL_TOOL_NAME = "executeSQL";
    private static final String NO_DATA_MARKER_1 = "没有";
    private static final String NO_DATA_MARKER_2 = "不存在";

    private final AgentReasoningProperties properties;

    /**
     * 构造错误恢复建议器。
     *
     * @param properties 推理配置
     */
    public ErrorRecoveryAdvisor(AgentReasoningProperties properties) {
        this.properties = properties;
    }

    /**
     * 根据工具名称和错误信息生成恢复建议。
     *
     * @param toolName 工具名称
     * @param errorMessage 错误信息
     * @return 恢复建议
     */
    public ErrorRecoveryAdvice advise(String toolName, String errorMessage) {
        String msg = errorMessage == null ? "" : errorMessage;
        String name = toolName == null ? "" : toolName;

        // 未知工具
        if (msg.contains(UNKNOWN_TOOL_MARKER) || isUnknownTool(name)) {
            return new ErrorRecoveryAdvice(ErrorRecoveryType.UNKNOWN_TOOL,
                    "该工具不存在。请检查可用工具列表，选择正确的工具名称后重试。", 1);
        }

        // SQL 错误优先判定
        if (SQL_TOOL_NAME.equalsIgnoreCase(name) || msg.toLowerCase(Locale.ROOT).contains("sql")) {
            return new ErrorRecoveryAdvice(ErrorRecoveryType.SQL_ERROR,
                    "SQL 执行失败。请先使用 Schema 查看表结构，确认表名和字段名无误后简化查询重试。", 2);
        }

        // 通用工具失败
        if (msg.contains(TOOL_FAILURE_MARKER)) {
            return new ErrorRecoveryAdvice(ErrorRecoveryType.TOOL_FAILURE,
                    "工具执行出错。请检查参数格式和输入数据是否正确，然后重试。", 2);
        }

        // 无数据
        if (msg.contains(NO_DATA_MARKER_1) || msg.contains(NO_DATA_MARKER_2)) {
            return new ErrorRecoveryAdvice(ErrorRecoveryType.NO_DATA,
                    "未查询到数据。请确认查询条件是否正确，或尝试扩大查询范围。", 1);
        }

        return ErrorRecoveryAdvice.none();
    }

    /**
     * 对工具结果追加恢复建议。
     *
     * <p>正常结果原样返回；包含"无数据"信号的结果会追加恢复提示段落。</p>
     *
     * @param toolName 工具名称
     * @param result 工具结果
     * @return 可能追加了恢复建议的结果
     */
    public String appendAdvice(String toolName, String result) {
        if (result == null) {
            return result;
        }
        if (result.contains(NO_DATA_MARKER_1) || result.contains(NO_DATA_MARKER_2)) {
            return result + "\n\n---\n错误恢复建议: 当前结果为空或不存在。请检查输入参数是否正确，"
                    + "或尝试换一种方式查询。如果确认数据不可用，请据实回答用户。";
        }
        return result;
    }

    /**
     * 判断工具名是否为未知工具（不在已注册列表中的占位判定）。
     */
    private boolean isUnknownTool(String toolName) {
        return toolName != null && toolName.startsWith("missing");
    }
}
