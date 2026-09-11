package com.ai.agent.capability;

import com.ai.agent.runtime.AgentRunContext;
import com.ai.agent.runtime.AgentRunScope;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * 按根 Run 跟踪活动委派路径，阻止递归环路和过深的子 Agent 调用。
 *
 * @author data-agent
 */
@Component
public class AgentDelegationGuard {

    public static final int MAX_DELEGATION_DEPTH = 3;

    private final ConcurrentMap<String, ActiveRunPath> activeRuns = new ConcurrentHashMap<>();

    /**
     * 在受保护的父子 Agent 路径内执行委派。
     *
     * @param parentAgentId 当前能力作用域中的父 Agent
     * @param childAgentId 目标子 Agent
     * @param action 子 Agent 执行逻辑
     * @param <T> 返回类型
     * @return 子 Agent 执行结果
     */
    public <T> T call(String parentAgentId, String childAgentId, Supplier<T> action) {
        AgentRunContext runContext = AgentRunScope.current()
                .orElseThrow(() -> rejection("DELEGATION_RUN_SCOPE_MISSING", "子 Agent 委派缺少根 Run 上下文"));
        String parentId = requireId(parentAgentId, "DELEGATION_PARENT_MISSING", "父 Agent 编号不能为空");
        String childId = requireId(childAgentId, "DELEGATION_TARGET_MISSING", "子 Agent 编号不能为空");
        if (action == null) {
            throw rejection("DELEGATION_ACTION_MISSING", "子 Agent 执行逻辑不能为空");
        }
        if (parentId.equals(childId)) {
            throw rejection("DELEGATION_CYCLE", "检测到子 Agent 委派环路");
        }

        runContext.control().ensureActive();
        ActiveRunPath path = activeRuns.computeIfAbsent(runContext.runId(), ignored -> new ActiveRunPath());
        enter(runContext.runId(), path, parentId, childId);
        try {
            return action.get();
        } finally {
            leave(runContext.runId(), path, childId);
        }
    }

    private void enter(String runId, ActiveRunPath path, String parentId, String childId) {
        synchronized (path) {
            try {
                if (path.depthByAgent.isEmpty()) {
                    path.depthByAgent.put(parentId, 1);
                }
                Integer parentDepth = path.depthByAgent.get(parentId);
                if (parentDepth == null) {
                    throw rejection("DELEGATION_PARENT_INACTIVE", "父 Agent 不在当前活动委派路径中");
                }
                if (path.depthByAgent.containsKey(childId)) {
                    throw rejection("DELEGATION_CYCLE", "检测到子 Agent 委派环路");
                }
                int childDepth = parentDepth + 1;
                if (childDepth > MAX_DELEGATION_DEPTH) {
                    throw rejection("DELEGATION_DEPTH_LIMIT", "子 Agent 委派最多允许三层");
                }
                path.depthByAgent.put(childId, childDepth);
                path.activeDelegations++;
            } catch (RuntimeException exception) {
                if (path.activeDelegations == 0) {
                    path.depthByAgent.clear();
                    activeRuns.remove(runId, path);
                }
                throw exception;
            }
        }
    }

    private void leave(String runId, ActiveRunPath path, String childId) {
        synchronized (path) {
            path.depthByAgent.remove(childId);
            path.activeDelegations = Math.max(0, path.activeDelegations - 1);
            if (path.activeDelegations == 0) {
                path.depthByAgent.clear();
                activeRuns.remove(runId, path);
            }
        }
    }

    private String requireId(String value, String reasonCode, String message) {
        if (value == null || value.isBlank()) {
            throw rejection(reasonCode, message);
        }
        return value.trim();
    }

    private DelegationRejectedException rejection(String reasonCode, String safeMessage) {
        return new DelegationRejectedException(reasonCode, safeMessage);
    }

    private static final class ActiveRunPath {
        private final Map<String, Integer> depthByAgent = new LinkedHashMap<>();
        private int activeDelegations;
    }

    /** 只携带稳定原因码和安全说明的委派拒绝。 */
    public static final class DelegationRejectedException extends IllegalStateException {

        private final String reasonCode;

        public DelegationRejectedException(String reasonCode, String safeMessage) {
            super(safeMessage);
            this.reasonCode = reasonCode;
        }

        public String reasonCode() {
            return reasonCode;
        }

        public String safeMessage() {
            return "委派被拒绝 [" + reasonCode + "]: " + getMessage();
        }
    }
}
