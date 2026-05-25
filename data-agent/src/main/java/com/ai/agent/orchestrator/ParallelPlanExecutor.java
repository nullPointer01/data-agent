package com.ai.agent.orchestrator;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import com.ai.agent.AgentReasoningProperties;
import com.ai.agent.react.ReActToolCall;
import com.ai.agent.tool.AgentToolInvoker;
import com.ai.agent.tool.ToolResult;

/**
 * 在完整 ReAct 循环前并行执行安全的计划步骤。
 *
 * <p>这里仅执行显式注册的只读预检工具。高成本或可能产生副作用的动作仍留在
 * 标准 ReAct 循环中，由模型基于观察结果继续推理。</p>
 *
 * @author data-agent
 */
@Component
public class ParallelPlanExecutor {

    private static final String STEP_RETRIEVE_KNOWLEDGE = "retrieve-knowledge";
    private static final String STEP_INSPECT_DATA = "inspect-data";
    private static final String STEP_GATHER_CONTEXT = "gather-context";
    private static final String TOOL_SEARCH_KNOWLEDGE = "searchKnowledge";
    private static final String TOOL_LIST_DATA_SOURCES = "listDataSources";
    private static final String TOOL_SEARCH_MEMORY = "searchMemory";
    private static final String TOOL_FAILURE_PREFIX = "工具执行失败:";
    private static final String UNKNOWN_TOOL_PREFIX = "未知工具:";

    private final AgentToolInvoker toolInvoker;
    private final ParallelTaskExecutor parallelTaskExecutor;
    private final AgentReasoningProperties reasoningProperties;

    public ParallelPlanExecutor(AgentToolInvoker toolInvoker, ParallelTaskExecutor parallelTaskExecutor,
            AgentReasoningProperties reasoningProperties) {
        this.toolInvoker = toolInvoker;
        this.parallelTaskExecutor = parallelTaskExecutor;
        this.reasoningProperties = reasoningProperties;
    }

    /**
     * 执行当前计划里的安全并行步骤。
     *
     * @param plan 执行计划
     * @param userQuery 原始用户问题
     * @return 聚合执行结果
     */
    public ParallelPlanExecutionResult execute(ExecutionPlan plan, String userQuery) {
        if (!reasoningProperties.isParallelPrecheckEnabled() || plan == null || !plan.hasSteps()) {
            return new ParallelPlanExecutionResult(List.of());
        }
        List<CompletableFuture<ParallelPlanStepResult>> futures = plan.steps().stream()
                .filter(ExecutionPlanStep::parallelizable)
                .map(step -> buildToolCall(step, userQuery)
                        .map(toolCall -> parallelTaskExecutor.supplyAsync(() -> invoke(step, toolCall)))
                        .orElse(null))
                .filter(future -> future != null)
                .toList();
        if (futures.isEmpty()) {
            return new ParallelPlanExecutionResult(List.of());
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        List<ParallelPlanStepResult> results = new ArrayList<>();
        for (CompletableFuture<ParallelPlanStepResult> future : futures) {
            results.add(future.join());
        }
        return new ParallelPlanExecutionResult(results);
    }

    private Optional<ReActToolCall> buildToolCall(ExecutionPlanStep step, String userQuery) {
        String stepId = step.id().toLowerCase(Locale.ROOT);
        if (STEP_RETRIEVE_KNOWLEDGE.equals(stepId)) {
            return Optional.of(new ReActToolCall(TOOL_SEARCH_KNOWLEDGE, userQuery, List.of(userQuery)));
        }
        if (STEP_INSPECT_DATA.equals(stepId)) {
            return Optional.of(new ReActToolCall(TOOL_LIST_DATA_SOURCES, "", List.of()));
        }
        if (STEP_GATHER_CONTEXT.equals(stepId)) {
            return Optional.of(new ReActToolCall(TOOL_SEARCH_MEMORY, userQuery, List.of(userQuery)));
        }
        return Optional.empty();
    }

    private ParallelPlanStepResult invoke(ExecutionPlanStep step, ReActToolCall toolCall) {
        try {
            String result = toolInvoker.invoke(toolCall);
            if (isFailureResult(result)) {
                return ParallelPlanStepResult.failure(step, toolCall.name(), result);
            }
            return ParallelPlanStepResult.success(step, toolCall.name(), result);
        } catch (Exception e) {
            return ParallelPlanStepResult.failure(step, toolCall.name(), e.getMessage());
        }
    }

    private boolean isFailureResult(String result) {
        return result != null && (result.startsWith(TOOL_FAILURE_PREFIX) || result.startsWith(UNKNOWN_TOOL_PREFIX));
    }
}
