package com.ai.agent.runtime;

import com.ai.agent.durable.AgentDurableRunService;
import com.ai.agent.runtime.dto.AgentRunCancellationResponse;
import com.ai.security.SecurityContextHelper;
import org.springframework.stereotype.Service;

/** 主库先登记取消，再协作停止本机控制器；其他实例通过租约围栏停止后续调用。 */
@Service
public class AgentRunCancellationService {
    private final AgentRunRegistry registry;
    private final SecurityContextHelper identity;
    private final AgentDurableRunService durable;

    public AgentRunCancellationService(AgentRunRegistry registry, SecurityContextHelper identity,
            AgentDurableRunService durable) {
        this.registry = registry; this.identity = identity; this.durable = durable;
    }

    public AgentRunCancellationResponse cancelOwned(String id) {
        try {
            var state = durable.cancelOwned(id);
            boolean cancelled = AgentRunStatus.CANCELLED.name().equals(state.status());
            if (cancelled) registry.cancelOwned(id, identity.getCurrentTenantId(), identity.getCurrentUserId(), "用户停止生成");
            return new AgentRunCancellationResponse(cancelled, id, state.status(), state.terminationReason(),
                    cancelled ? "已请求取消运行" : "运行已经结束");
        } catch (IllegalArgumentException missing) {
            return registry.cancelOwned(id, identity.getCurrentTenantId(), identity.getCurrentUserId(), "用户停止生成")
                    .map(snapshot -> new AgentRunCancellationResponse(true, id, snapshot.status().name(),
                            snapshot.terminationReason().name(), "已请求取消运行"))
                    .orElseGet(() -> new AgentRunCancellationResponse(false, id, "", "", "运行不存在或已结束"));
        }
    }
}
