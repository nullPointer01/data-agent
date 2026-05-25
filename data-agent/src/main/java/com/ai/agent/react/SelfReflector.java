package com.ai.agent.react;

import com.ai.model.AnalysisResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import com.ai.agent.AgentReasoningProperties;

/**
 * ReAct 循环中的自我反思器。
 *
 * <p>在工具观察出错时生成反思提示供模型自我修正，在最终答案生成后
 * 评估置信度和后续建议。</p>
 *
 * @author data-agent
 */
@Component
public class SelfReflector {

    private final AgentReasoningProperties properties;

    /**
     * 构造自我反思器。
     *
     * @param properties 推理配置
     */
    public SelfReflector(AgentReasoningProperties properties) {
        this.properties = properties;
    }

    /**
     * 根据工具观察结果生成自我反思提示。
     *
     * <p>包含工具名称、错误类型和当前尝试次数信息，指导模型修正。</p>
     *
     * @param outcome 步骤结果
     * @param attemptCount 当前尝试次数
     * @return 反思提示文本
     */
    public String reflect(ReActStepOutcome outcome, int attemptCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("[自我反思]\n");

        String toolName = outcome.toolName() == null ? "未知" : outcome.toolName();
        sb.append("工具: ").append(toolName).append('\n');

        ErrorRecoveryAdvice advice = outcome.recoveryAdvice();
        if (advice != null && advice.hasInstruction()) {
            sb.append("错误类型: ").append(advice.type()).append('\n');
            sb.append("恢复指令: ").append(advice.instruction()).append('\n');
            sb.append("尝试次数: ").append(attemptCount).append('/').append(advice.maxAttempts()).append('\n');
        }

        String toolResult = outcome.toolResult() == null ? "" : outcome.toolResult();
        if (!toolResult.isBlank()) {
            sb.append("工具结果: ").append(toolResult).append('\n');
        }

        sb.append("请根据以上信息调整策略后重试。");
        return sb.toString();
    }

    /**
     * 对最终答案进行质量反思。
     *
     * <p>评估答案置信度（基于是否有工具调用步骤）并提供后续建议。</p>
     *
     * @param answer 最终答案
     * @param thinkingSteps 思考步骤
     * @return 反思结果
     */
    public String reflectExhausted(ReActStepOutcome outcome) {
        StringBuilder sb = new StringBuilder();
        sb.append("[迭代耗尽反思]\n");
        sb.append("ReAct 循环已达到最大迭代次数，未能得出最终答案。\n");
        if (outcome != null && outcome.toolResult() != null) {
            sb.append("最后工具结果: ").append(outcome.toolResult()).append('\n');
        }
        sb.append("建议: 简化问题或提供更明确的上下文。");
        return sb.toString();
    }

    public String reflectFinalAnswer(String answer, List<AnalysisResponse.ThinkingStep> thinkingSteps) {
        StringBuilder sb = new StringBuilder();
        sb.append("[最终答案反思]\n");

        // 评估置信度
        boolean hasToolSteps = thinkingSteps != null && thinkingSteps.stream()
                .anyMatch(step -> "tool_call".equals(step.getType()));

        String confidence = hasToolSteps ? "高" : "中";
        sb.append("置信度: ").append(confidence).append('\n');

        if (hasToolSteps) {
            sb.append("依据: 答案基于工具调用结果生成，数据支撑充分。\n");
        } else {
            sb.append("依据: 答案基于模型知识生成，未经工具验证。\n");
        }

        // 后续建议
        sb.append("后续建议: ");
        if (hasToolSteps) {
            sb.append("可以进一步深入分析或生成可视化图表以增强理解。");
        } else {
            sb.append("建议提供更多上下文数据或上传文件以获得更精确的分析结果。");
        }

        return sb.toString();
    }
}
