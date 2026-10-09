package com.ai.agent.agentic;

import com.ai.agent.runtime.AgentRunScope;
import com.ai.agent.runtime.event.AgentEvent;
import com.ai.agent.runtime.event.AgentEventType;
import com.ai.logging.StructuredLogger;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 官方 Agent 生命周期钩子：框架自动通知节点开始、完成和失败，允许向子工作流继承。
 * 当前 beta 的独立顶层 sequence 只通知其工作流，内部 Java 步骤的回调边界见框架集成文档。
 * 只记录安全标识；不记录 Agent 输入、输出或异常原文，不承担权限、预算或审批决策。
 */
public final class AgentLifecycleListener implements AgentListener {

    private final StructuredLogger logger;
    private final String engine;
    private final Map<String, String> executors;

    public AgentLifecycleListener(StructuredLogger logger, String engine, Map<String, String> executors) {
        this.logger = logger;
        this.engine = engine;
        this.executors = Map.copyOf(executors);
    }

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        record(request.agentName(), "STARTED", null);
        // 仅为外层执行节点发送旧 SSE 进度；Supervisor 内部节点只记录日志，避免重复刷新前端步骤。
        String executorId = executors.get(request.agentName());
        if (executorId != null) {
            AgentRunScope.current().ifPresent(run -> run.eventSink().emit(AgentEvent.of(run,
                    AgentEventType.THINKING_START, Map.of("iteration", run.snapshot().iterations(),
                            "engine", engine, "stage", "executor", "agentId", executorId))));
        }
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        record(response.agentName(), "COMPLETED", null);
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
        record(error.agentName(), "FAILED", error.error().getClass().getSimpleName());
    }

    @Override
    public boolean inheritedBySubagents() {
        return true;
    }

    private void record(String name, String status, String errorType) {
        AgentRunScope.current().ifPresent(run -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("runId", run.runId());
            data.put("engine", engine);
            data.put("node", name);
            data.put("executorId", executors.get(name));
            data.put("status", status);
            data.put("errorType", errorType);
            logger.logAgentStep(run.sessionId(), "AGENT_NODE", data);
        });
    }
}
