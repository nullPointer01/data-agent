package com.ai.agent.runtime;

import com.ai.agent.durable.AgentDurableRunService;
import com.ai.agent.durable.dto.AgentDurableRunResponse;
import com.ai.agent.runtime.dto.AgentRunCancellationResponse;
import com.ai.security.SecurityContextHelper;
import org.springframework.stereotype.Service;

/**
 * 按当前认证用户取消本 JVM 内的活跃 Agent Run。
 *
 * @author data-agent
 */
@Service
public class AgentRunCancellationService {

    private static final String NOT_FOUND_MESSAGE = "运行不存在或已结束";

    private final AgentRunRegistry runRegistry;
    private final SecurityContextHelper securityContextHelper;
    private final AgentDurableRunService durableRunService;

    public AgentRunCancellationService(AgentRunRegistry runRegistry,
            SecurityContextHelper securityContextHelper,
            AgentDurableRunService durableRunService) {
        this.runRegistry = runRegistry;
        this.securityContextHelper = securityContextHelper;
        this.durableRunService = durableRunService;
    }

    /**
     * 取消当前租户用户拥有的活跃 Run。
     *
     * @param runId 运行编号
     * @return 不泄露跨租户状态的取消结果
     */
    public AgentRunCancellationResponse cancelOwned(String runId) {
        return runRegistry.cancelOwned(
                        runId,
                        securityContextHelper.getCurrentTenantId(),
                        securityContextHelper.getCurrentUserId(),
                        "用户停止生成")
                .map(snapshot -> new AgentRunCancellationResponse(
                        true,
                        runId,
                        snapshot.status().name(),
                        snapshot.terminationReason().name(),
                        snapshot.status() == AgentRunStatus.CANCELLED
                                ? "已请求取消运行"
                                : "运行已经结束"))
                .orElseGet(() -> cancelDurableWaiting(runId));
    }

    private AgentRunCancellationResponse cancelDurableWaiting(String runId) {
        try {
            AgentDurableRunResponse response = durableRunService.cancelWaitingOwned(runId);
            boolean cancelled = AgentRunStatus.CANCELLED.name().equals(response.status());
            return new AgentRunCancellationResponse(
                    cancelled,
                    runId,
                    response.status(),
                    response.terminationReason(),
                    cancelled ? "已取消等待审批的运行" : "运行已经结束");
        } catch (IllegalArgumentException e) {
            return new AgentRunCancellationResponse(false, runId, "", "", NOT_FOUND_MESSAGE);
        }
    }
}
