package com.ai.agent.capability;

/**
 * Harness 可发现能力的统一类型。
 *
 * @author data-agent
 */
public enum AgentCapabilityType {

    TOOL("tool"),
    SKILL("skill"),
    SUB_AGENT("agent");

    private final String identityPrefix;

    AgentCapabilityType(String identityPrefix) {
        this.identityPrefix = identityPrefix;
    }

    /**
     * 构造不会因显示名称变化而漂移的能力标识。
     *
     * @param sourceId 原能力稳定编号
     * @return 带类型前缀的统一标识
     */
    public String identity(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("能力来源编号不能为空: " + name());
        }
        return identityPrefix + ":" + sourceId.trim();
    }

    /**
     * 判断统一标识是否属于当前能力类型。
     *
     * @param identity 统一能力标识
     * @return 类型前缀和来源编号均有效时返回 true
     */
    public boolean owns(String identity) {
        String prefix = prefix();
        return identity != null && identity.startsWith(prefix) && identity.length() > prefix.length();
    }

    /**
     * 从统一能力标识中提取原始稳定编号。
     *
     * @param identity 统一能力标识
     * @return 去掉类型前缀后的来源编号
     */
    public String sourceId(String identity) {
        if (!owns(identity)) {
            throw new IllegalArgumentException("能力标识与类型不一致: " + identity);
        }
        return identity.substring(prefix().length());
    }

    private String prefix() {
        return identityPrefix + ":";
    }
}
