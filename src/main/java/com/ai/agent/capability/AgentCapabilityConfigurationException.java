package com.ai.agent.capability;

/**
 * Agent 能力持久化配置无法被安全解释。
 *
 * <p>异常只携带定位所需的字段和 Agent 编号，不保留原始 JSON，
 * 避免配置内容进入 API 或日志。</p>
 *
 * @author data-agent
 */
public class AgentCapabilityConfigurationException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public static final String ERROR_CODE = "AGENT_CAPABILITY_CONFIGURATION_INVALID";

    private final String field;
    private final String agentId;
    private final String reasonCode;

    public AgentCapabilityConfigurationException(
            String field, String agentId, String reasonCode, Throwable cause) {
        super(buildMessage(field, agentId, reasonCode), cause);
        this.field = field;
        this.agentId = agentId;
        this.reasonCode = reasonCode;
    }

    public String getField() {
        return field;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    private static String buildMessage(String field, String agentId, String reasonCode) {
        return "Agent 能力配置不可用: field=" + safe(field, "unknown")
                + ", agentId=" + safe(agentId, "unknown")
                + ", reason=" + safe(reasonCode, "UNKNOWN");
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
