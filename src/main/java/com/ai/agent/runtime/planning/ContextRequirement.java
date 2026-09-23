package com.ai.agent.runtime.planning;

/**
 * 请求对某类上下文来源的使用要求。
 *
 * <p>REQUIRED 在首次模型调用前加载，OPTIONAL 仅允许 Agent 通过受治理工具按需检索，
 * OFF 表示本次运行既不预加载也不开放对应检索能力。</p>
 *
 * @author data-agent
 */
public enum ContextRequirement {

    /** 首次模型调用前必须尝试加载。 */
    REQUIRED,

    /** 不预加载，但允许通过精确授权的工具按需检索。 */
    OPTIONAL,

    /** 本次运行不使用该来源。 */
    OFF;

    /**
     * 判断是否需要在首次模型调用前加载。
     *
     * @return REQUIRED 时返回 true
     */
    public boolean shouldPreload() {
        return this == REQUIRED;
    }

    /**
     * 判断是否允许在 Agent Loop 中按需检索。
     *
     * @return OPTIONAL 时返回 true
     */
    public boolean allowsOnDemandRetrieval() {
        return this == OPTIONAL;
    }
}
