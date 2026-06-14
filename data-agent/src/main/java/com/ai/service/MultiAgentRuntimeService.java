package com.ai.service;

import com.ai.agent.specialist.SpecialistFactory;
import com.ai.memory.MemoryManager;
import com.ai.memory.dto.MemoryContext;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 应用级 Agent 运行时分发服务。
 *
 * @author data-agent
 */
@Service
public class MultiAgentRuntimeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiAgentRuntimeService.class);
    private static final String AGENT_SKILL_PREFIX = "agent:";

    private final AgentProfileService agentProfileService;
    private final SpecialistFactory specialistFactory;
    private final MemoryManager memoryManager;

    public MultiAgentRuntimeService(AgentProfileService agentProfileService,
            SpecialistFactory specialistFactory,
            MemoryManager memoryManager) {
        this.agentProfileService = agentProfileService;
        this.specialistFactory = specialistFactory;
        this.memoryManager = memoryManager;
    }

    /**
     * 按 Agent 配置选择专家执行，避免修改调用方传入的原始请求对象。
     *
     * @param agentId Agent 编号
     * @param request 原始分析请求
     * @param fileContent 可选文件内容
     * @return 分析结果
     */
    public AnalysisResponse execute(String agentId, AnalysisRequest request, String fileContent) {
        AgentProfile profile = agentProfileService.requireEnabledAgent(agentId);
        validateRequest(request);
        MemoryContext memoryContext = buildMemoryContext(request);
        AnalysisResponse response = specialistFactory.execute(profile, request, fileContent, null,
                memoryContext);
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
