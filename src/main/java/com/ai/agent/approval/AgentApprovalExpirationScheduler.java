package com.ai.agent.approval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 有界扫描到期审批，CAS 失败表示决定已由其他请求完成。
 *
 * @author data-agent
 */
@Component
public class AgentApprovalExpirationScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentApprovalExpirationScheduler.class);

    private final AgentApprovalService approvalService;

    public AgentApprovalExpirationScheduler(AgentApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @Scheduled(fixedDelayString = "${app.agent.durable.scan-interval:PT15S}")
    public void expirePendingApprovals() {
        try {
            int expired = approvalService.expireDue();
            if (expired > 0) {
                LOGGER.info("已过期 {} 个 Agent 工具审批", expired);
            }
        } catch (Exception e) {
            LOGGER.warn("Agent 工具审批过期扫描失败: {}", e.getMessage());
        }
    }
}
