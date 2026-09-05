package com.ai.agent.react;

import org.springframework.stereotype.Component;

import com.ai.agent.tool.governance.AgentToolExecutionResult;
import com.ai.agent.tool.governance.AgentToolExecutionStatus;

/**
 * 工具观察后的错误恢复建议器。
 *
 * <p>只根据稳定状态码判定恢复类型，展示文本不参与控制流。</p>
 *
 * @author data-agent
 */
@Component
public class ErrorRecoveryAdvisor {

    private static final String SQL_TOOL_NAME = "executeSql";

    /**
     * 根据工具名称和错误信息生成恢复建议。
     *
     * @param toolName 工具名称
     * @param result 结构化工具结果
     * @return 恢复建议
     */
    public ErrorRecoveryAdvice advise(String toolName, AgentToolExecutionResult result) {
        String name = toolName == null ? "" : toolName;
        AgentToolExecutionStatus status = result == null
                ? AgentToolExecutionStatus.EXECUTION_FAILED
                : result.status();
        return switch (status) {
            case SUCCESS, APPROVAL_REQUIRED -> ErrorRecoveryAdvice.none();
            case UNKNOWN_TOOL -> new ErrorRecoveryAdvice(ErrorRecoveryType.UNKNOWN_TOOL,
                    "该工具不存在，请只从本轮可用工具列表中重新选择。", 1);
            case INVALID_ARGUMENTS -> new ErrorRecoveryAdvice(ErrorRecoveryType.ARGUMENT_ERROR,
                    "工具参数不符合 Schema，请修正字段名、必填项和字段类型后重试。", 2);
            case TIMEOUT -> new ErrorRecoveryAdvice(ErrorRecoveryType.TIMEOUT,
                    "工具调用超时，请缩小查询范围或改用成本更低的工具。", 2);
            case EXECUTION_FAILED -> SQL_TOOL_NAME.equalsIgnoreCase(name)
                    ? new ErrorRecoveryAdvice(ErrorRecoveryType.SQL_ERROR,
                            "SQL 执行失败，请先查看 Schema 并简化查询后重试。", 2)
                    : new ErrorRecoveryAdvice(ErrorRecoveryType.TOOL_FAILURE,
                            "工具执行失败，请检查参数和依赖状态后重试。", 2);
            case UNAUTHORIZED, POLICY_DENIED, RUN_TERMINATED -> new ErrorRecoveryAdvice(
                    ErrorRecoveryType.TOOL_FAILURE, "服务端策略不允许继续执行该工具，请基于已有信息回答。", 0);
        };
    }

    /**
     * 对工具结果追加恢复建议。
     *
     * <p>成功结果原样返回；失败结果按稳定状态码追加恢复提示。</p>
     *
     * @param toolName 工具名称
     * @param result 工具结果
     * @return 可能追加了恢复建议的结果
     */
    public String appendAdvice(String toolName, AgentToolExecutionResult result) {
        String observation = result == null ? "" : result.toModelObservation();
        ErrorRecoveryAdvice advice = advise(toolName, result);
        return advice.hasInstruction()
                ? observation + "\n\n---\n错误恢复建议: " + advice.instruction()
                : observation;
    }
}
