package com.ai.agent.runtime;

import com.ai.agent.ConfigurableAgentExecutor;
import com.ai.agent.runtime.event.AgentRunEventBridge;
import com.ai.agent.runtime.planning.RequestContextPlan;
import com.ai.memory.MemoryManager;
import com.ai.memory.dto.MemoryContext;
import com.ai.memory.dto.UserMemoryProfileSnapshotResponse;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 仅消费 Coordinator 已解析路线的配置化 Agent 执行内核。
 *
 * <p>该组件保持包级可见，业务服务和 Controller 不能绕过
 * {@link AgentRunCoordinator} 直接发起顶层 Agent Run。</p>
 *
 * @author data-agent
 */
@Component
final class ConfiguredAgentExecutionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfiguredAgentExecutionService.class);
    private static final String AGENT_SKILL_PREFIX = "agent:";

    private final ConfigurableAgentExecutor configurableAgentExecutor;
    private final MemoryManager memoryManager;

    ConfiguredAgentExecutionService(ConfigurableAgentExecutor configurableAgentExecutor,
            MemoryManager memoryManager) {
        this.configurableAgentExecutor = configurableAgentExecutor;
        this.memoryManager = memoryManager;
    }

    AnalysisResponse execute(AgentRunContext context, AgentRunRoute route) {
        validateResolvedRoute(context, route);
        AgentProfile profile = route.profile();
        AnalysisRequest request = context.executionContext().getRequest();
        MemoryContext memoryContext = buildMemoryContext(context, route);

        AnalysisResponse response;
        if (context.eventSink().isStreaming()) {
            response = configurableAgentExecutor.executeStreaming(
                    profile,
                    request,
                    context.executionContext().getFileContent(),
                    memoryContext,
                    new AgentRunEventBridge(context),
                    route.mode(),
                    route.requestPlan());
        } else {
            response = configurableAgentExecutor.execute(
                    profile,
                    request,
                    context.executionContext().getFileContent(),
                    memoryContext,
                    route.mode(),
                    route.requestPlan());
        }
        if (response == null) {
            throw new IllegalStateException("配置化 Agent 未返回执行结果");
        }
        response.setSkillUsed(AGENT_SKILL_PREFIX + profile.getName());
        return response;
    }

    private void validateResolvedRoute(AgentRunContext context, AgentRunRoute route) {
        if (context == null || route == null) {
            throw new IllegalArgumentException("Agent Run 上下文和路线不能为空");
        }
        if (route.target() != AgentRunRoute.Target.CONFIGURED_AGENT || route.profile() == null) {
            throw new IllegalArgumentException("配置化执行内核只接受已解析的 AgentProfile 路线");
        }
        if (context.mode() != route.mode()) {
            throw new IllegalArgumentException("Agent Run 模式与已解析路线不一致");
        }
    }

    private MemoryContext buildMemoryContext(AgentRunContext context, AgentRunRoute route) {
        RequestContextPlan plan = route.requestPlan().contextPlan();
        String history = "";
        String working = "";
        // 当前会话状态由 Session 决定，不再跟随“需要跨会话记忆”一起开关。
        var session = context.executionContext().getSession();
        if (plan.shouldPreloadConversationHistory() && session != null) {
            history = session.buildRecentHistory();
            working = memoryManager.loadWorkingMemory(context.sessionId());
        }
        UserMemoryProfileSnapshotResponse profile = plan.shouldPreloadUserProfile()
                ? memoryManager.loadUserProfile() : UserMemoryProfileSnapshotResponse.empty();
        return new MemoryContext(history, working, loadSemanticMemory(plan), profile);
    }

    /**
     * 跨会话记忆与当前会话历史独立降级，外部记忆故障不能清空已经可用的对话上下文。
     */
    private String loadSemanticMemory(RequestContextPlan plan) {
        if (!plan.shouldPreloadSemanticMemory()) {
            return "";
        }
        try {
            return memoryManager.recallSemanticMemory(plan.retrievalQuery());
        } catch (Exception e) {
            LOGGER.warn("构建 Agent 记忆上下文失败: {}", e.getMessage());
            return "";
        }
    }
}
