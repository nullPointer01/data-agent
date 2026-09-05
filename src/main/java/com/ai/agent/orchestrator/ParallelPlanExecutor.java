package com.ai.agent.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import com.ai.agent.AgentReasoningProperties;
import com.ai.agent.tool.AgentToolInvoker;
import com.ai.agent.tool.governance.AgentToolExecutionResult;
import com.ai.agent.tool.governance.AgentToolInvocationContext;
import com.ai.agent.tool.governance.AgentToolInvocationContextFactory;

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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AgentToolInvoker toolInvoker;
    private final ParallelTaskExecutor parallelTaskExecutor;
    private final AgentReasoningProperties reasoningProperties;
    private final AgentToolInvocationContextFactory toolInvocationContextFactory;

    public ParallelPlanExecutor(AgentToolInvoker toolInvoker, ParallelTaskExecutor parallelTaskExecutor,
            AgentReasoningProperties reasoningProperties,
            AgentToolInvocationContextFactory toolInvocationContextFactory) {
        this.toolInvoker = toolInvoker;
        this.parallelTaskExecutor = parallelTaskExecutor;
        this.reasoningProperties = reasoningProperties;
        this.toolInvocationContextFactory = toolInvocationContextFactory;
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
                        .map(toolCall -> parallelTaskExecutor.supplyAsync(() -> invoke(step, toolCall,
                                toolInvocationContextFactory.orchestratorPrecheck())))
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

    private Optional<ToolExecutionRequest> buildToolCall(ExecutionPlanStep step, String userQuery) {
        String stepId = step.id().toLowerCase(Locale.ROOT);
        if (STEP_RETRIEVE_KNOWLEDGE.equals(stepId)) {
            return Optional.of(queryToolRequest(TOOL_SEARCH_KNOWLEDGE, userQuery));
        }
        if (STEP_INSPECT_DATA.equals(stepId)) {
            return Optional.of(ToolExecutionRequest.builder()
                    .name(TOOL_LIST_DATA_SOURCES)
                    .arguments("{}")
                    .build());
        }
        if (STEP_GATHER_CONTEXT.equals(stepId)) {
            return Optional.of(queryToolRequest(TOOL_SEARCH_MEMORY, userQuery));
        }
        return Optional.empty();
    }

    /**
     * 构建单 query 参数的工具请求，参数 JSON 与工具方法的参数名对齐。
     */
    private ToolExecutionRequest queryToolRequest(String toolName, String userQuery) {
        ObjectNode arguments = OBJECT_MAPPER.createObjectNode();
        arguments.put("query", userQuery);
        return ToolExecutionRequest.builder()
                .name(toolName)
                .arguments(arguments.toString())
                .build();
    }

    private ParallelPlanStepResult invoke(ExecutionPlanStep step, ToolExecutionRequest toolCall,
            AgentToolInvocationContext invocationContext) {
        try {
            AgentToolExecutionResult result = toolInvoker.invokeStructured(toolCall, invocationContext);
            if (!result.successful()) {
                return ParallelPlanStepResult.failure(step, toolCall.name(), result.toModelObservation());
            }
            return ParallelPlanStepResult.success(step, toolCall.name(), result.toModelObservation());
        } catch (Exception e) {
            return ParallelPlanStepResult.failure(step, toolCall.name(), "并行预检未完成");
        }
    }
}
