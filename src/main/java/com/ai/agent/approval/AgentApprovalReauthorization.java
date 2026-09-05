package com.ai.agent.approval;

import com.ai.agent.tool.governance.AgentToolAuthorizationSnapshot;
import com.ai.agent.tool.governance.AgentToolDescriptor;

/**
 * 恢复前基于当前数据库状态重新计算的授权结论。
 *
 * @author data-agent
 */
public record AgentApprovalReauthorization(
        AgentApprovalGrant grant,
        AgentToolAuthorizationSnapshot ownerAuthorization,
        AgentToolDescriptor descriptor) {
}
