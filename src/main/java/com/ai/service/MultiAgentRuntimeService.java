package com.ai.service;

import com.ai.agent.ConfigurableAgentExecutor;
import com.ai.agent.specialist.SpecialistFactory;
import com.ai.memory.MemoryManager;
import com.ai.memory.dto.MemoryContext;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.function.Consumer;

/**
 * 应用级 Agent 运行时分发服务。
 *
 * <p>优先使用 {@link ConfigurableAgentExecutor} 按 executionMode 执行；
 * 仅在 profile 未设置 executionMode 时回退到 {@link SpecialistFactory}。</p>
 *
 * @author data-agent
 */
@Service
public class MultiAgentRuntimeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiAgentRuntimeService.class);
    private static final String AGENT_SKILL_PREFIX = "agent:";

    private final AgentProfileService agentProfileService;
    private final ConfigurableAgentExecutor configurableExecutor;
    private final SpecialistFactory specialistFactory;
    private final MemoryManager memoryManager;

    public MultiAgentRuntimeService(AgentProfileService agentProfileService,
            ConfigurableAgentExecutor configurableExecutor,
            SpecialistFactory specialistFactory,
            MemoryManager memoryManager) {
        this.agentProfileService = agentProfileService;
        this.configurableExecutor = configurableExecutor;
        this.specialistFactory = specialistFactory;
        this.memoryManager = memoryManager;
    }

    /**
     * 按 Agent 配置执行，优先走 ConfigurableAgentExecutor。
     *
     * @param agentId Agent 编号
     * @param request 原始分析请求
     * @param fileContent 可选文件内容
     * @return 分析结果
     */
    public AnalysisResponse execute(String agentId, AnalysisRequest request, String fileContent) {
        AgentProfile profile = agentProfileService.requireEnabledAgent(agentId);
        return execute(profile, request, fileContent);
    }

    /**
     * 使用已经由统一路由解析过的 Agent 配置执行，避免重复查询和二次路由。
     *
     * @param profile 已启用的 Agent 配置
     * @param request 原始分析请求
     * @param fileContent 可选文件内容
     * @return 分析结果
     */
    public AnalysisResponse execute(AgentProfile profile, AnalysisRequest request, String fileContent) {
        validateRequest(request);
        if (profile == null) {
            throw new IllegalArgumentException("Agent 配置不能为空");
        }
        MemoryContext memoryContext = buildMemoryContext(request);

        AnalysisResponse response;
        if (StringUtils.hasText(profile.getExecutionMode())) {
            response = configurableExecutor.execute(profile, request, fileContent, memoryContext);
        } else {
            response = specialistFactory.execute(profile, request, fileContent, null, memoryContext);
        }
        response.setSkillUsed(AGENT_SKILL_PREFIX + profile.getName());
        return response;
    }

    /**
     * 流式执行指定 Agent。
     *
     * @param agentId Agent 编号
     * @param request 原始分析请求
     * @param fileContent 可选文件内容
     * @param eventEmitter SSE 事件消费者
     * @return 完整分析响应
     */
    public AnalysisResponse executeStreaming(String agentId, AnalysisRequest request, String fileContent,
            Consumer<String> eventEmitter) {
        AgentProfile profile = agentProfileService.requireEnabledAgent(agentId);
        return executeStreaming(profile, request, fileContent, eventEmitter);
    }

    /**
     * 流式执行已经由统一路由解析过的 Agent 配置。
     *
     * @param profile 已启用的 Agent 配置
     * @param request 原始分析请求
     * @param fileContent 可选文件内容
     * @param eventEmitter 兼容事件消费者
     * @return 完整分析响应
     */
    public AnalysisResponse executeStreaming(AgentProfile profile, AnalysisRequest request, String fileContent,
            Consumer<String> eventEmitter) {
        validateRequest(request);
        if (profile == null) {
            throw new IllegalArgumentException("Agent 配置不能为空");
        }
        MemoryContext memoryContext = buildMemoryContext(request);
        AnalysisResponse response = configurableExecutor.executeStreaming(
                profile, request, fileContent, memoryContext, eventEmitter);
        response.setSkillUsed(AGENT_SKILL_PREFIX + profile.getName());
        return response;
    }

    private void validateRequest(AnalysisRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("分析请求不能为空");
        }
    }

    private MemoryContext buildMemoryContext(AnalysisRequest request) {
        try {
            if (memoryManager == null) {
                return MemoryContext.empty();
            }
            return memoryManager.buildContext(request.getSessionId(), request.getQuestion());
        } catch (Exception e) {
            LOGGER.warn("构建 Agent 记忆上下文失败: {}", e.getMessage());
            return MemoryContext.empty();
        }
    }
}
