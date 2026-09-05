package com.ai.agent.specialist;

import com.ai.memory.dto.MemoryContext;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.ai.agent.AgentExecutionRequest;
import com.ai.agent.AgentType;

/**
 * 专家工厂，负责构建执行请求并根据 Agent 类型分派到对应专家。
 *
 * <p>该工厂在构建请求时会拷贝原始 {@link AnalysisRequest}，
 * 避免将 profile 的 modelId/agentId 污染到调用方持有的原始请求对象上。</p>
 *
 * @author data-agent
 */
@Component
public class SpecialistFactory {

    private final AgentSpecialistRegistry registry;

    /**
     * 构造专家工厂。
     *
     * @param registry 专家注册表
     */
    public SpecialistFactory(AgentSpecialistRegistry registry) {
        this.registry = registry;
    }

    /**
     * 构建专家执行请求。
     *
     * <p>创建原始请求的副本，并将 profile 中的 modelId 和 agentId 注入副本，
     * 不会修改原始请求。</p>
     *
     * @param profile Agent 配置
     * @param request 原始分析请求
     * @param fileContent 文件内容，可为空
     * @param specialistTask 编排器注入的任务上下文，可为空
     * @param memoryContext 聚合记忆上下文，可为空
     * @return 封装完整上下文的执行请求
     */
    public AgentExecutionRequest buildRequest(AgentProfile profile, AnalysisRequest request,
            String fileContent, SpecialistTask specialistTask, MemoryContext memoryContext) {
        AnalysisRequest copy = request.copy();
        if (profile != null) {
            if (StringUtils.hasText(profile.getModelId())) {
                copy.setModelId(profile.getModelId());
            }
            if (StringUtils.hasText(profile.getAgentId())) {
                copy.setAgentId(profile.getAgentId());
            }
        }
        return new AgentExecutionRequest(profile, copy, fileContent, specialistTask, memoryContext);
    }

    /**
     * 构建请求并执行对应专家。
     *
     * @param profile Agent 配置
     * @param request 原始分析请求
     * @param fileContent 文件内容，可为空
     * @param specialistTask 编排器注入的任务上下文，可为空
     * @param memoryContext 聚合记忆上下文，可为空
     * @return 专家执行结果
     */
    public AnalysisResponse execute(AgentProfile profile, AnalysisRequest request,
            String fileContent, SpecialistTask specialistTask, MemoryContext memoryContext) {
        AgentExecutionRequest executionRequest = buildRequest(profile, request, fileContent,
                specialistTask, memoryContext);
        AgentType type = profile != null ? profile.getType() : AgentType.REACT;
        AgentSpecialist specialist = registry.resolve(type);
        return specialist.execute(executionRequest);
    }

    /**
     * 要求获取指定类型的专家，未注册时抛出异常。
     *
     * @param type Agent 类型
     * @return 对应的专家实现
     * @throws IllegalArgumentException 如果指定类型未注册
     */
    public AgentSpecialist requireSpecialist(AgentType type) {
        return registry.findByType(type)
                .orElseThrow(() -> new IllegalArgumentException(
                        "未注册的专家类型: " + type));
    }
}
