package com.ai.agent.runtime;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 保存当前 JVM 内仍在执行的 Agent Run。
 *
 * @author data-agent
 */
@Component
public class AgentRunRegistry {

    private final ConcurrentMap<String, AgentRunContext> activeRuns = new ConcurrentHashMap<>();

    /**
     * 注册一个新运行。
     *
     * @param context 运行上下文
     */
    public void register(AgentRunContext context) {
        AgentRunContext previous = activeRuns.putIfAbsent(context.runId(), context);
        if (previous != null) {
            throw new IllegalStateException("Agent Run ID 已存在: " + context.runId());
        }
    }

    /**
     * 从活跃注册表移除运行。
     *
     * @param context 运行上下文
     */
    public void remove(AgentRunContext context) {
        if (context != null) {
            activeRuns.remove(context.runId(), context);
        }
    }

    /**
     * 由系统生命周期事件取消运行，不执行用户身份判断。
     *
     * @param runId 运行编号
     * @param detail 取消说明
     * @return 当前运行快照
     */
    public Optional<AgentRunSnapshot> cancelSystem(String runId, String detail) {
        AgentRunContext context = activeRuns.get(runId);
        if (context == null) {
            return Optional.empty();
        }
        context.control().cancel(detail);
        return Optional.of(context.snapshot());
    }

    /**
     * 取消当前租户用户拥有的运行。
     *
     * @param runId 运行编号
     * @param tenantId 当前租户
     * @param userId 当前用户
     * @param detail 取消说明
     * @return 当前运行快照；身份不匹配时为空
     */
    public Optional<AgentRunSnapshot> cancelOwned(String runId, String tenantId, String userId, String detail) {
        if (!StringUtils.hasText(runId) || !StringUtils.hasText(tenantId) || !StringUtils.hasText(userId)) {
            return Optional.empty();
        }
        AgentRunContext context = activeRuns.get(runId);
        if (context == null
                || !Objects.equals(tenantId, context.tenantId())
                || !Objects.equals(userId, context.userId())) {
            return Optional.empty();
        }
        context.control().cancel(detail);
        return Optional.of(context.snapshot());
    }

    /**
     * 获取活跃运行，只供运行时内部关联生命周期。
     *
     * @param runId 运行编号
     * @return 活跃运行
     */
    public Optional<AgentRunContext> findActive(String runId) {
        return Optional.ofNullable(activeRuns.get(runId));
    }
}
