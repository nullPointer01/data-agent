package com.ai.agent;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ai.agent.orchestrator.OrchestratorDecision;
import com.ai.agent.orchestrator.OrchestratorExecutionResult;

/**
 * 结果整合器，负责将多专家协作的执行结果整合为最终用户可见的回答。
 *
 * <p>整合优先级：
 * <ol>
 *     <li>执行结果中的主回答（executionResult.response().getResult()）</li>
 *     <li>共享上下文中的 final_answer</li>
 *     <li>共享上下文中的 summary</li>
 *     <li>当无任何答案时，生成包含查询和意图信息的诊断文本</li>
 * </ol>
 *
 * @author data-agent
 */
@Service
public class ResultIntegrator {

    /**
     * 整合最终回答。
     *
     * @param query 用户原始查询
     * @param decision 编排器路由决策，可为空
     * @param executionResult 专家执行结果，可为空
     * @param sharedContext 共享上下文
     * @return 最终回答文本
     */
    public String integrate(String query, OrchestratorDecision decision,
            OrchestratorExecutionResult executionResult, Map<String, Object> sharedContext) {
        String primaryAnswer = extractPrimaryAnswer(executionResult);
        String contextAnswer = extractContextAnswer(sharedContext);
        String finalAnswer;

        // 优先使用主回答
        if (StringUtils.hasText(primaryAnswer)) {
            finalAnswer = primaryAnswer;
        } else if (StringUtils.hasText(contextAnswer)) {
            finalAnswer = contextAnswer;
        } else {
            // 无回答时生成诊断文本
            finalAnswer = buildNoAnswerResponse(query, decision);
        }

        // 追加失败诊断
        String diagnostics = buildDiagnostics(sharedContext);
        if (StringUtils.hasText(diagnostics)) {
            finalAnswer = finalAnswer + diagnostics;
        }

        return finalAnswer;
    }

    /**
     * 构建整合摘要，供执行追踪和质量分析使用。
     *
     * @param executionResult 专家执行结果，可为空
     * @param sharedContext 共享上下文
     * @return 整合摘要 Map
     */
    public Map<String, Object> buildIntegrationSummary(OrchestratorExecutionResult executionResult,
            Map<String, Object> sharedContext) {
        Map<String, Object> summary = new LinkedHashMap<>();

        // 判断是否有可用答案
        String primaryAnswer = extractPrimaryAnswer(executionResult);
        summary.put("answerAvailable", StringUtils.hasText(primaryAnswer));

        // 从质量评估中提取 score 和 riskLevel
        Object qualityObj = sharedContext.get("quality");
        if (qualityObj instanceof Map<?, ?> quality) {
            Object score = quality.get("score");
            if (score != null) {
                summary.put("score", score);
            }
            Object riskLevel = quality.get("riskLevel");
            if (riskLevel != null) {
                summary.put("riskLevel", riskLevel);
            }
        }

        // 从协作汇总中提取 totalTasks
        Object collaborationObj = sharedContext.get("collaborationSummary");
        if (collaborationObj instanceof Map<?, ?> collaborationSummary) {
            Object totalTasks = collaborationSummary.get("totalTasks");
            if (totalTasks != null) {
                summary.put("totalTasks", totalTasks);
            }
        }

        return summary;
    }

    /**
     * 从执行结果中提取主回答。
     */
    private String extractPrimaryAnswer(OrchestratorExecutionResult executionResult) {
        if (executionResult == null || executionResult.response() == null) {
            return null;
        }
        return executionResult.response().getResult();
    }

    /**
     * 从共享上下文中按优先级提取回答。
     */
    private String extractContextAnswer(Map<String, Object> sharedContext) {
        if (sharedContext == null) {
            return null;
        }
        // 优先 final_answer
        Object finalAnswer = sharedContext.get("final_answer");
        if (finalAnswer instanceof String text && StringUtils.hasText(text)) {
            return text;
        }
        // 其次 summary
        Object summaryObj = sharedContext.get("summary");
        if (summaryObj instanceof String text && StringUtils.hasText(text)) {
            return text;
        }
        return null;
    }

    /**
     * 当无任何回答时，生成包含查询和意图信息的诊断文本。
     */
    private String buildNoAnswerResponse(String query, OrchestratorDecision decision) {
        StringBuilder builder = new StringBuilder();
        builder.append("未能生成有效回答。");
        if (StringUtils.hasText(query)) {
            builder.append("\n原始查询: ").append(query);
        }
        if (decision != null && decision.intentResult() != null) {
            builder.append("\n识别意图: ").append(decision.intentResult().intent());
        }
        return builder.toString();
    }

    /**
     * 构建失败和跳过任务的诊断文本。
     */
    @SuppressWarnings("unchecked")
    private String buildDiagnostics(Map<String, Object> sharedContext) {
        if (sharedContext == null) {
            return null;
        }
        StringBuilder diagnostics = new StringBuilder();

        Object failedObj = sharedContext.get("failedTasks");
        if (failedObj instanceof List<?> failedTasks && !failedTasks.isEmpty()) {
            for (Object task : failedTasks) {
                diagnostics.append("\n失败任务 ").append(task);
            }
        }

        Object skippedObj = sharedContext.get("skippedTasks");
        if (skippedObj instanceof List<?> skippedTasks && !skippedTasks.isEmpty()) {
            for (Object task : skippedTasks) {
                diagnostics.append("\n跳过任务 ").append(task);
            }
        }

        return diagnostics.isEmpty() ? null : diagnostics.toString();
    }
}
