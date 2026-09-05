package com.ai.agent.react;

import com.ai.memory.MemoryContextPromptFormatter;
import com.ai.memory.dto.MemoryContext;
import com.ai.model.AnalysisResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ai.agent.AgentReasoningProperties;
import com.ai.agent.TaskClassification;
import com.ai.agent.orchestrator.ExecutionPlan;
import com.ai.agent.orchestrator.ParallelPlanExecutionResult;

/**
 * 构建 ReAct 执行元数据，供 AnalysisResponse 消费。
 *
 * @author data-agent
 */
@Component
class ReActMetadataBuilder {

    private static final String METADATA_ENGINE = "engine";
    private static final String METADATA_MODE = "mode";
    private static final String METADATA_CLASSIFICATION = "classification";
    private static final String METADATA_REASONING_FEATURES = "reasoningFeatures";
    private static final String METADATA_EXECUTION_PLAN = "executionPlan";
    private static final String METADATA_PARALLEL_PRECHECK = "parallelPrecheck";
    private static final String METADATA_ITERATIONS = "iterations";
    private static final String METADATA_THINKING_STEPS = "thinkingSteps";
    private static final String METADATA_RAG_CONTEXT = "ragContext";
    private static final String METADATA_RAG_HAS_CONTEXT = "hasContext";
    private static final String METADATA_RAG_HIT_COUNT = "hitCount";
    private static final String METADATA_RAG_REWRITTEN_QUERY = "rewrittenQuery";
    private static final String METADATA_RAG_QUERY_TYPE = "queryType";
    private static final String METADATA_RAG_KEYWORDS = "keywords";
    private static final String METADATA_RAG_CITATIONS = "citations";
    private static final String METADATA_MEMORY_CONTEXT = "memoryContext";
    private static final String ENGINE_REACT = "react";
    private static final String MODE_FAST_PATH = "fast_path";
    private static final String MODE_REASONING = "reasoning";

    private final AgentReasoningProperties reasoningProperties;
    private final MemoryContextPromptFormatter memoryContextPromptFormatter;

    ReActMetadataBuilder(AgentReasoningProperties reasoningProperties,
            MemoryContextPromptFormatter memoryContextPromptFormatter) {
        this.reasoningProperties = reasoningProperties;
        this.memoryContextPromptFormatter = memoryContextPromptFormatter;
    }

    Map<String, Object> buildFastPathMetadata(TaskClassification classification,
            List<AnalysisResponse.ThinkingStep> thinkingSteps,
            MemoryContext memoryContext) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(METADATA_ENGINE, ENGINE_REACT);
        metadata.put(METADATA_MODE, MODE_FAST_PATH);
        metadata.put(METADATA_CLASSIFICATION, classification);
        metadata.put(METADATA_REASONING_FEATURES, buildReasoningFeatureMetadata());
        metadata.put(METADATA_ITERATIONS, 0);
        metadata.put(METADATA_THINKING_STEPS, List.copyOf(thinkingSteps));
        metadata.put(METADATA_MEMORY_CONTEXT, memoryContextPromptFormatter.toMetadata(memoryContext));
        return metadata;
    }

    Map<String, Object> buildReasoningMetadata(TaskClassification classification,
            ExecutionPlan executionPlan,
            ParallelPlanExecutionResult preExecutionResult,
            ReActExecutionResult executionResult,
            ReActRequestContext requestContext,
            List<AnalysisResponse.ThinkingStep> thinkingSteps) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(METADATA_ENGINE, ENGINE_REACT);
        metadata.put(METADATA_MODE, MODE_REASONING);
        metadata.put(METADATA_CLASSIFICATION, classification);
        metadata.put(METADATA_REASONING_FEATURES, buildReasoningFeatureMetadata());
        metadata.put(METADATA_EXECUTION_PLAN, executionPlan);
        metadata.put(METADATA_PARALLEL_PRECHECK,
                preExecutionResult == null ? new ParallelPlanExecutionResult(List.of()) : preExecutionResult);
        metadata.put(METADATA_ITERATIONS, executionResult == null ? 0 : executionResult.iterations());
        metadata.put(METADATA_THINKING_STEPS, List.copyOf(thinkingSteps));
        metadata.put(METADATA_RAG_CONTEXT, buildRagContextMetadata(requestContext));
        metadata.put(METADATA_MEMORY_CONTEXT, memoryContextPromptFormatter.toMetadata(
                requestContext == null ? MemoryContext.empty() : requestContext.memoryContext()));
        return metadata;
    }

    private Map<String, Object> buildRagContextMetadata(ReActRequestContext requestContext) {
        if (requestContext == null || requestContext.ragContext() == null) {
            return Map.of(METADATA_RAG_HAS_CONTEXT, false, METADATA_RAG_HIT_COUNT, 0);
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(METADATA_RAG_HAS_CONTEXT, requestContext.ragContext().hasContext());
        metadata.put(METADATA_RAG_HIT_COUNT, requestContext.ragContext().getHitCount());
        metadata.put(METADATA_RAG_REWRITTEN_QUERY, requestContext.ragContext().getRewrittenQuery());
        metadata.put(METADATA_RAG_QUERY_TYPE, requestContext.ragContext().getQueryType());
        metadata.put(METADATA_RAG_KEYWORDS, requestContext.ragContext().getKeywords());
        metadata.put(METADATA_RAG_CITATIONS, requestContext.ragContext().getCitations());
        return metadata;
    }

    private Map<String, Object> buildReasoningFeatureMetadata() {
        return Map.of(
                "fastPathEnabled", reasoningProperties.isFastPathEnabled(),
                "planningEnabled", reasoningProperties.isPlanningEnabled(),
                "parallelPrecheckEnabled", reasoningProperties.isParallelPrecheckEnabled(),
                "reflectionEnabled", reasoningProperties.isReflectionEnabled(),
                "workingMemoryEnabled", reasoningProperties.isWorkingMemoryEnabled());
    }
}
