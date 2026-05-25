package com.ai.agent;

import com.ai.memory.dto.MemoryContext;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisRequest;
import com.ai.agent.specialist.SpecialistTask;

/**
 * 封装专家执行所需的完整上下文。
 *
 * @param profile Agent 配置
 * @param request 原始分析请求（已注入 modelId/agentId 的副本）
 * @param fileContent 关联的文件内容，可为空
 * @param specialistTask 编排器注入的任务上下文，可为空
 * @param memoryContext 聚合记忆上下文，可为空
 * @author data-agent
 */
public record AgentExecutionRequest(
        AgentProfile profile,
        AnalysisRequest request,
        String fileContent,
        SpecialistTask specialistTask,
        MemoryContext memoryContext) {

    /**
     * 不带任务和记忆上下文的简便构造。
     *
     * @param profile Agent 配置
     * @param request 原始分析请求
     * @param fileContent 文件内容
     */
    public AgentExecutionRequest(AgentProfile profile, AnalysisRequest request, String fileContent) {
        this(profile, request, fileContent, null, null);
    }

    /**
     * 不带记忆上下文的简便构造。
     *
     * @param profile Agent 配置
     * @param request 原始分析请求
     * @param fileContent 文件内容
     * @param specialistTask 任务上下文
     */
    public AgentExecutionRequest(AgentProfile profile, AnalysisRequest request, String fileContent,
            SpecialistTask specialistTask) {
        this(profile, request, fileContent, specialistTask, null);
    }
}
