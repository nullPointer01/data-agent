package com.ai.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 增强推理配置。
 *
 * @author data-agent
 */
@ConfigurationProperties(prefix = "app.agent.reasoning")
public class AgentReasoningProperties {

    /**
     * 自主模式总开关。开启后绕过确定性分类、快慢路闸门、预规划和并行预检，
     * 请求构建完上下文直接进入 ReAct 循环，把"用不用工具、用哪个、何时收尾"
     * 全部交给模型在循环里临场决策——这是更接近 Claude Code 的纯 agent 形态。
     *
     * <p>默认开启（模型优先）：强模型自身已能多步规划与工具决策，确定性编排层
     * 反成负债（问候触发重型循环、预检冗余、上下文污染等）。设为 false 可切回
     * 原编排式，用于对比。</p>
     */
    private boolean autonomousMode = true;

    private boolean fastPathEnabled = true;

    private boolean planningEnabled = true;

    private boolean parallelPrecheckEnabled = true;

    private boolean reflectionEnabled = true;

    private boolean workingMemoryEnabled = true;

    public boolean isAutonomousMode() {
        return autonomousMode;
    }

    public void setAutonomousMode(boolean autonomousMode) {
        this.autonomousMode = autonomousMode;
    }

    public boolean isFastPathEnabled() {
        return fastPathEnabled;
    }

    public void setFastPathEnabled(boolean fastPathEnabled) {
        this.fastPathEnabled = fastPathEnabled;
    }

    public boolean isPlanningEnabled() {
        return planningEnabled;
    }

    public void setPlanningEnabled(boolean planningEnabled) {
        this.planningEnabled = planningEnabled;
    }

    public boolean isParallelPrecheckEnabled() {
        return parallelPrecheckEnabled;
    }

    public void setParallelPrecheckEnabled(boolean parallelPrecheckEnabled) {
        this.parallelPrecheckEnabled = parallelPrecheckEnabled;
    }

    public boolean isReflectionEnabled() {
        return reflectionEnabled;
    }

    public void setReflectionEnabled(boolean reflectionEnabled) {
        this.reflectionEnabled = reflectionEnabled;
    }

    public boolean isWorkingMemoryEnabled() {
        return workingMemoryEnabled;
    }

    public void setWorkingMemoryEnabled(boolean workingMemoryEnabled) {
        this.workingMemoryEnabled = workingMemoryEnabled;
    }
}
