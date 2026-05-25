package com.ai.service;

import com.ai.agent.AgentType;
import com.ai.agent.specialist.AgentSpecialistRegistry;
import com.ai.agent.specialist.SpecialistFactory;
import com.ai.agent.dto.AgentTestRequest;
import com.ai.agent.dto.AgentProfileListResponse;
import com.ai.agent.dto.AgentProfileMutationResponse;
import com.ai.agent.dto.AgentProfileRequest;
import com.ai.agent.dto.AgentProfileResponse;
import com.ai.agent.dto.AgentRegistryCapabilityResponse;
import com.ai.agent.dto.AgentRegistryEntryResponse;
import com.ai.agent.dto.AgentRegistryResponse;
import com.ai.memory.MemoryManager;
import com.ai.memory.dto.MemoryContext;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.repository.AgentProfileRepository;
import com.ai.security.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 面向租户的 Agent 配置应用服务。
 *
 * @author data-agent
 */
@Service
public class AgentProfileService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentProfileService.class);

    private final AgentProfileRepository agentProfileRepository;
    private final SecurityContextHelper securityContextHelper;
    private final AgentSpecialistRegistry specialistRegistry;
    private final SpecialistFactory specialistFactory;
    private final MemoryManager memoryManager;

    public AgentProfileService(AgentProfileRepository agentProfileRepository,
            SecurityContextHelper securityContextHelper,
            AgentSpecialistRegistry specialistRegistry,
            SpecialistFactory specialistFactory,
            MemoryManager memoryManager) {
        this.agentProfileRepository = agentProfileRepository;
        this.securityContextHelper = securityContextHelper;
        this.specialistRegistry = specialistRegistry;
        this.specialistFactory = specialistFactory;
        this.memoryManager = memoryManager;
    }

    @Transactional(readOnly = true)
    public AgentProfileListResponse listAgents(boolean enabledOnly) {
        return listTenantAgents(enabledOnly);
    }

    /**
     * 查询当前登录用户创建的 Agent 配置列表。
     *
     * @param enabledOnly 是否只返回启用配置
     * @return Agent 配置列表
     */
    @Transactional(readOnly = true)
    public AgentProfileListResponse listMyAgents(boolean enabledOnly) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();
        List<AgentProfile> agents = enabledOnly
                ? agentProfileRepository.findByTenantIdAndCreatedByAndEnabledTrueOrderByUpdatedAtDesc(tenantId, userId)
                : agentProfileRepository.findByTenantIdAndCreatedByOrderByUpdatedAtDesc(tenantId, userId);
        return toListResponse(agents);
    }

    private AgentProfileListResponse listTenantAgents(boolean enabledOnly) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        List<AgentProfile> agents = enabledOnly
                ? agentProfileRepository.findByTenantIdAndEnabledTrueOrderByUpdatedAtDesc(tenantId)
                : agentProfileRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId);
        return toListResponse(agents);
    }

    private AgentProfileListResponse toListResponse(List<AgentProfile> agents) {
        List<AgentProfileResponse> responses = agents.stream()
                .map(AgentProfileResponse::from)
                .collect(Collectors.toList());
        return new AgentProfileListResponse(true, responses);
    }

    /**
     * 查询当前租户下的 Agent 配置详情。
     *
     * @param agentId Agent 编号
     * @return Agent 配置详情
     */
    @Transactional(readOnly = true)
    public AgentProfileResponse getAgent(String agentId) {
        return AgentProfileResponse.from(requireCurrentTenantAgent(agentId));
    }

    /**
     * 查询当前登录用户创建的单个 Agent 配置。
     *
     * @param agentId Agent 编号
     * @return Agent 配置详情
     */
    @Transactional(readOnly = true)
    public AgentProfileResponse getMyAgent(String agentId) {
        return AgentProfileResponse.from(requireCurrentUserAgent(agentId));
    }

    /**
     * 创建当前租户下的 Agent 配置。
     *
     * @param request 创建请求
     * @return 创建结果
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentProfileMutationResponse createAgent(AgentProfileRequest request) {
        validate(request);
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();

        AgentProfile profile = new AgentProfile();
        profile.setTenantId(tenantId);
        profile.setCreatedBy(userId);
        applyProfileRequest(profile, request);

        agentProfileRepository.save(profile);
        return AgentProfileMutationResponse.saved(profile.getAgentId());
    }

    /**
     * 创建当前登录用户自定义 Agent。
     *
     * @param request 创建请求
     * @return 创建结果
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentProfileMutationResponse createMyAgent(AgentProfileRequest request) {
        return createAgent(request);
    }

    /**
     * 更新当前租户下的 Agent 配置。
     *
     * @param agentId Agent 编号
     * @param request 更新请求
     * @return 更新结果
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentProfileMutationResponse updateAgent(String agentId, AgentProfileRequest request) {
        validate(request);
        AgentProfile profile = requireCurrentTenantAgent(agentId);
        applyProfileRequest(profile, request);

        agentProfileRepository.save(profile);
        return AgentProfileMutationResponse.saved(profile.getAgentId());
    }

    /**
     * 更新当前登录用户创建的 Agent 配置。
     *
     * @param agentId Agent 编号
     * @param request 更新请求
     * @return 更新结果
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentProfileMutationResponse updateMyAgent(String agentId, AgentProfileRequest request) {
        validate(request);
        AgentProfile profile = requireCurrentUserAgent(agentId);
        applyProfileRequest(profile, request);
        agentProfileRepository.save(profile);
        return AgentProfileMutationResponse.saved(profile.getAgentId());
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentProfileMutationResponse toggle(String agentId) {
        AgentProfile profile = requireCurrentTenantAgent(agentId);
        profile.setEnabled(!profile.isEnabled());
        agentProfileRepository.save(profile);
        return AgentProfileMutationResponse.toggled(profile.isEnabled());
    }

    /**
     * 启用或停用当前登录用户创建的 Agent 配置。
     *
     * @param agentId Agent 编号
     * @return 状态变更结果
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentProfileMutationResponse toggleMyAgent(String agentId) {
        AgentProfile profile = requireCurrentUserAgent(agentId);
        profile.setEnabled(!profile.isEnabled());
        agentProfileRepository.save(profile);
        return AgentProfileMutationResponse.toggled(profile.isEnabled());
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentProfileMutationResponse delete(String agentId) {
        AgentProfile profile = requireCurrentTenantAgent(agentId);
        agentProfileRepository.delete(profile);
        return AgentProfileMutationResponse.deleted();
    }

    /**
     * 删除当前登录用户创建的 Agent 配置。
     *
     * @param agentId Agent 编号
     * @return 删除结果
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentProfileMutationResponse deleteMyAgent(String agentId) {
        AgentProfile profile = requireCurrentUserAgent(agentId);
        agentProfileRepository.delete(profile);
        return AgentProfileMutationResponse.deleted();
    }

    /**
     * 使用指定 Agent 执行一次试运行，便于前端保存前后验证配置效果。
     *
     * @param agentId Agent 编号
     * @param request 试运行请求
     * @return Agent 执行结果
     */
    public AnalysisResponse testAgent(String agentId, AgentTestRequest request) {
        AgentProfile profile = requireEnabledAgent(agentId);
        AnalysisRequest analysisRequest = buildTestAnalysisRequest(request);
        MemoryContext memoryContext = buildMemoryContext(analysisRequest);
        AnalysisResponse response = specialistFactory.execute(profile, analysisRequest, request.fileContent(), null,
                memoryContext);
        response.setSkillUsed("agent:" + profile.getName());
        return response;
    }

    /**
     * 试运行当前登录用户创建的 Agent。
     *
     * @param agentId Agent 编号
     * @param request 试运行请求
     * @return Agent 执行结果
     */
    public AnalysisResponse testMyAgent(String agentId, AgentTestRequest request) {
        AgentProfile profile = requireEnabledCurrentUserAgent(agentId);
        AnalysisRequest analysisRequest = buildTestAnalysisRequest(request);
        MemoryContext memoryContext = buildMemoryContext(analysisRequest);
        AnalysisResponse response = specialistFactory.execute(profile, analysisRequest, request.fileContent(), null,
                memoryContext);
        response.setSkillUsed("agent:" + profile.getName());
        return response;
    }

    @Transactional(readOnly = true)
    public AgentProfile requireEnabledAgent(String agentId) {
        AgentProfile profile = requireCurrentTenantAgent(agentId);
        if (!profile.isEnabled()) {
            throw new IllegalArgumentException("Agent 已禁用");
        }
        return profile;
    }

    /**
     * 查询当前租户下可启用的 Agent 配置，供编排器路由使用。
     *
     * @return 按更新时间倒序排列的启用配置
     */
    @Transactional(readOnly = true)
    public List<AgentProfile> listEnabledProfiles() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        return agentProfileRepository.findByTenantIdAndEnabledTrueOrderByUpdatedAtDesc(tenantId);
    }

    /**
     * 查询当前租户可用于编排的 Agent 注册快照。
     *
     * @return Agent 注册中心快照
     */
    @Transactional(readOnly = true)
    public AgentRegistryResponse getRegistry() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        List<AgentProfile> agents = agentProfileRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId);
        List<AgentRegistryEntryResponse> entries = new ArrayList<>();
        entries.addAll(systemEntries());
        entries.addAll(agents.stream().map(this::tenantEntry).toList());
        List<AgentRegistryCapabilityResponse> capabilities = capabilityResponses(agents);
        int enabledTenantAgentCount = (int) agents.stream().filter(AgentProfile::isEnabled).count();
        return new AgentRegistryResponse(true,
                specialistRegistry.size(),
                agents.size(),
                enabledTenantAgentCount,
                capabilities,
                entries);
    }

    private void applyProfileRequest(AgentProfile profile, AgentProfileRequest request) {
        AgentType type = AgentType.fromCode(request.type());
        profile.setName(request.name().trim());
        profile.setType(type);
        profile.setDescription(blankToNull(request.description()));
        profile.setSystemPrompt(blankToNull(request.systemPrompt()));
        profile.setModelId(blankToNull(request.modelId()));
        profile.setSkillId(resolveSkillId(type, request.skillId()));
        profile.setDatasourceId(resolveDatasourceId(type, request.datasourceId()));
        profile.setEnabled(request.enabled() == null || request.enabled());
    }

    private AgentProfile requireCurrentTenantAgent(String agentId) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        return agentProfileRepository.findByAgentIdAndTenantId(agentId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Agent 不存在或无权限"));
    }

    private AgentProfile requireCurrentUserAgent(String agentId) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();
        return agentProfileRepository.findByAgentIdAndTenantIdAndCreatedBy(agentId, tenantId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Agent 不存在或无权限"));
    }

    private AgentProfile requireEnabledCurrentUserAgent(String agentId) {
        AgentProfile profile = requireCurrentUserAgent(agentId);
        if (!profile.isEnabled()) {
            throw new IllegalArgumentException("Agent 已禁用");
        }
        return profile;
    }

    private void validate(AgentProfileRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Agent 请求不能为空");
        }
        if (!StringUtils.hasText(request.name())) {
            throw new IllegalArgumentException("Agent 名称不能为空");
        }
        AgentType type = AgentType.fromCode(request.type());
        validateRequiredBinding(type, request);
    }

    private void validateRequiredBinding(AgentType type, AgentProfileRequest request) {
        if (AgentType.DATA == type && !StringUtils.hasText(request.datasourceId())) {
            throw new IllegalArgumentException("数据源 Agent 必须绑定数据源");
        }
        if (AgentType.SKILL == type && !StringUtils.hasText(request.skillId())) {
            throw new IllegalArgumentException("技能 Agent 必须绑定技能");
        }
    }

    private String resolveSkillId(AgentType type, String skillId) {
        if (AgentType.SKILL != type) {
            return null;
        }
        return blankToNull(skillId);
    }

    private String resolveDatasourceId(AgentType type, String datasourceId) {
        if (AgentType.DATA != type && AgentType.REACT != type && AgentType.SKILL != type) {
            return null;
        }
        return blankToNull(datasourceId);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AnalysisRequest buildTestAnalysisRequest(AgentTestRequest request) {
        if (request == null || !StringUtils.hasText(request.question())) {
            throw new IllegalArgumentException("测试问题不能为空");
        }
        AnalysisRequest analysisRequest = new AnalysisRequest();
        analysisRequest.setQuestion(request.question().trim());
        analysisRequest.setModelId(blankToNull(request.modelId()));
        analysisRequest.setSessionId(blankToNull(request.sessionId()));
        return analysisRequest;
    }

    private MemoryContext buildMemoryContext(AnalysisRequest request) {
        try {
            if (memoryManager == null) {
                return MemoryContext.empty();
            }
            return memoryManager.buildContext(request.getSessionId(), request.getQuestion());
        } catch (Exception e) {
            LOGGER.warn("Agent 试运行记忆上下文构建失败: {}", e.getMessage());
            return MemoryContext.empty();
        }
    }

    private List<AgentRegistryEntryResponse> systemEntries() {
        return specialistRegistry.registeredTypes().stream()
                .map(type -> new AgentRegistryEntryResponse(
                        "system:" + type.getCode(),
                        type.getDisplayName(),
                        type.getCode(),
                        "SYSTEM",
                        type.getCapability(),
                        type.getDescription(),
                        true,
                        true,
                        null,
                        null,
                        null,
                        List.of("system", "orchestrator")))
                .toList();
    }

    private AgentRegistryEntryResponse tenantEntry(AgentProfile profile) {
        AgentType type = profile.getType() == null ? AgentType.REACT : profile.getType();
        return new AgentRegistryEntryResponse(
                "tenant:" + profile.getAgentId(),
                profile.getName(),
                type.getCode(),
                "TENANT",
                type.getCapability(),
                StringUtils.hasText(profile.getDescription()) ? profile.getDescription() : type.getDescription(),
                profile.isEnabled(),
                specialistRegistry.isRuntimeAvailable(type),
                profile.getModelId(),
                profile.getSkillId(),
                profile.getDatasourceId(),
                tenantTags(profile, type));
    }

    private List<String> tenantTags(AgentProfile profile, AgentType type) {
        List<String> tags = new ArrayList<>();
        tags.add("tenant");
        tags.add(type.getCapability());
        if (StringUtils.hasText(profile.getModelId())) {
            tags.add("model");
        }
        if (StringUtils.hasText(profile.getSkillId())) {
            tags.add("skill");
        }
        if (StringUtils.hasText(profile.getDatasourceId())) {
            tags.add("datasource");
        }
        return List.copyOf(tags);
    }

    private List<AgentRegistryCapabilityResponse> capabilityResponses(List<AgentProfile> agents) {
        Map<AgentType, CapabilityCounter> counterMap = new EnumMap<>(AgentType.class);
        for (AgentType type : AgentType.values()) {
            counterMap.put(type, new CapabilityCounter());
        }
        for (AgentProfile agent : agents) {
            AgentType type = agent.getType() == null ? AgentType.REACT : agent.getType();
            counterMap.computeIfAbsent(type, ignored -> new CapabilityCounter()).add(agent);
        }
        return counterMap.entrySet().stream()
                .map(entry -> toCapabilityResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    private AgentRegistryCapabilityResponse toCapabilityResponse(AgentType type, CapabilityCounter counter) {
        return new AgentRegistryCapabilityResponse(type.getCode(),
                type.getDisplayName(),
                type.getCapability(),
                type.getDescription(),
                specialistRegistry.isRuntimeAvailable(type),
                counter.totalCount(),
                counter.enabledCount());
    }

    /**
     * Agent 能力分组计数器。
     */
    private static final class CapabilityCounter {

        private long totalCount;
        private long enabledCount;

        private void add(AgentProfile profile) {
            totalCount++;
            if (profile.isEnabled()) {
                enabledCount++;
            }
        }

        private long totalCount() {
            return totalCount;
        }

        private long enabledCount() {
            return enabledCount;
        }
    }
}
