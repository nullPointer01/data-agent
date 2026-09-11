package com.ai.agent.runtime.planning;

/**
 * 请求计划对模型工具选择的约束。
 *
 * <p>该约束只决定模型本轮是否必须产生工具调用，不代表工具获得执行权限；
 * 工具仍需通过服务端 Schema、RBAC、Agent 白名单、风险策略和审批管道。</p>
 *
 * @author data-agent
 */
public enum AgentToolChoice {

    /** 模型可自行决定是否调用已暴露工具。 */
    AUTO,

    /** 模型必须从已暴露工具中产生至少一个调用。 */
    REQUIRED
}
