package com.ai.service;

import com.ai.agent.capability.AgentCapabilityConfigurationCodec;
import com.ai.agent.capability.AgentCapabilityService;
import com.ai.agent.capability.AgentCapabilityType;
import com.ai.agent.dto.AgentProfileListResponse;
import com.ai.agent.dto.AgentProfileMutationResponse;
import com.ai.agent.dto.AgentProfileRequest;
import com.ai.agent.dto.AgentProfileResponse;
import com.ai.model.AgentProfile;
import com.ai.repository.AgentProfileRepository;
import com.ai.security.SecurityContextHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 面向租户的 Agent 配置应用服务。
 *
 * @author data-agent
 */
@Service
public class AgentProfileService {

    private static final String DEFAULT_AGENT_NAME = "我的 Agent";
    private static final String DEFAULT_AGENT_DESCRIPTION = "会使用知识、记忆和工具完成任务，并在结果中给出依据。";
    private static final String DEFAULT_AGENT_PROMPT = "你是用户的长期个人 Agent。先判断是否需要知识或工具，"
            + "需要时调用获准的能力；回答要准确、简洁、可核验。涉及有副作用的动作时必须等待用户确认。";

    private final AgentProfileRepository agentProfileRepository;
    private final SecurityContextHelper securityContextHelper;
    private final AgentCapabilityService capabilityService;
    private final AgentCapabilityConfigurationCodec capabilityConfigurationCodec;

    public AgentProfileService(AgentProfileRepository agentProfileRepository,
            SecurityContextHelper securityContextHelper,
            AgentCapabilityService capabilityService,
            AgentCapabilityConfigurationCodec capabilityConfigurationCodec) {
        this.agentProfileRepository = agentProfileRepository;
        this.securityContextHelper = securityContextHelper;
        this.capabilityService = capabilityService;
        this.capabilityConfigurationCodec = capabilityConfigurationCodec;
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
                .map(this::toResponse)
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
        return toResponse(requireCurrentTenantAgent(agentId));
    }

    /**
     * 查询当前登录用户创建的单个 Agent 配置。
     *
     * @param agentId Agent 编号
     * @return Agent 配置详情
     */
    @Transactional(readOnly = true)
    public AgentProfileResponse getMyAgent(String agentId) {
        return toResponse(requireCurrentUserAgent(agentId));
    }

    /**
     * 获取当前用户的默认个人 Agent；首次使用时自动创建。
     *
     * @return 默认个人 Agent
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentProfileResponse getOrCreateMyDefaultAgent() {
        return toResponse(requireMyDefaultAgent());
    }

    /**
     * 获取当前用户可执行的默认个人 Agent。
     *
     * @return 默认个人 Agent 实体
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentProfile requireMyDefaultAgent() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();
        AgentProfile profile = agentProfileRepository
                .findFirstByTenantIdAndCreatedByAndDefaultAgentTrueOrderByUpdatedAtDesc(tenantId, userId)
                .orElseGet(() -> createDefaultAgent(tenantId, userId));
        if (!profile.isEnabled()) {
            throw new IllegalStateException("默认个人 Agent 已停用，请联系管理员");
        }
        return profile;
    }

    /**
     * 将指定配置设为当前用户的默认 Agent。
     *
     * @param agentId Agent 编号
     * @return 更新后的默认 Agent
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentProfileResponse setMyDefaultAgent(String agentId) {
        AgentProfile selected = requireCurrentUserAgent(agentId);
        if (!selected.isEnabled()) {
            throw new IllegalArgumentException("已停用的 Agent 不能设为默认 Agent");
        }
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();
        List<AgentProfile> profiles = agentProfileRepository
                .findByTenantIdAndCreatedByOrderByUpdatedAtDesc(tenantId, userId);
        profiles.forEach(profile -> profile.setDefaultAgent(profile.getAgentId().equals(agentId)));
        agentProfileRepository.saveAll(profiles);
        return toResponse(selected);
    }

    /**
     * 创建当前租户下的 Agent 配置。
     *
     * @param request 创建请求
     * @return 创建结果
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentProfileMutationResponse createAgent(AgentProfileRequest request) {
        List<String> capabilityBindings = validate(request, null);
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();

        AgentProfile profile = new AgentProfile();
        profile.setTenantId(tenantId);
        profile.setCreatedBy(userId);
        applyProfileRequest(profile, request, capabilityBindings);

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
        AgentProfile profile = requireCurrentTenantAgent(agentId);
        List<String> capabilityBindings = validate(request, profile);
        if (profile.isDefaultAgent() && Boolean.FALSE.equals(request.enabled())) {
            throw new IllegalArgumentException("默认 Agent 不能停用");
        }
        applyProfileRequest(profile, request, capabilityBindings);

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
        AgentProfile profile = requireCurrentUserAgent(agentId);
        List<String> capabilityBindings = validate(request, profile);
        applyProfileRequest(profile, request, capabilityBindings);
        if (profile.isDefaultAgent() && !profile.isEnabled()) {
            throw new IllegalArgumentException("默认 Agent 不能停用，请先切换默认 Agent");
        }
        agentProfileRepository.save(profile);
        return AgentProfileMutationResponse.saved(profile.getAgentId());
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentProfileMutationResponse toggle(String agentId) {
        AgentProfile profile = requireCurrentTenantAgent(agentId);
        if (profile.isDefaultAgent() && profile.isEnabled()) {
            throw new IllegalArgumentException("默认 Agent 不能停用");
        }
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
        if (profile.isDefaultAgent() && profile.isEnabled()) {
            throw new IllegalArgumentException("默认 Agent 不能停用，请先切换默认 Agent");
        }
        profile.setEnabled(!profile.isEnabled());
        agentProfileRepository.save(profile);
        return AgentProfileMutationResponse.toggled(profile.isEnabled());
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentProfileMutationResponse delete(String agentId) {
        AgentProfile profile = requireCurrentTenantAgent(agentId);
        if (profile.isDefaultAgent()) {
            throw new IllegalArgumentException("默认 Agent 不能删除");
        }
        removeDeletedAgentBindings(profile);
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
        if (profile.isDefaultAgent()) {
            throw new IllegalArgumentException("默认 Agent 不能删除，请先切换默认 Agent");
        }
        removeDeletedAgentBindings(profile);
        agentProfileRepository.delete(profile);
        return AgentProfileMutationResponse.deleted();
    }

    /**
     * 删除 Agent 前清理同租户父 Agent 中的委派绑定，避免留下不可执行的悬空引用。
     *
     * @param deletedProfile 即将删除的 Agent
     */
    private void removeDeletedAgentBindings(AgentProfile deletedProfile) {
        String deletedIdentity = AgentCapabilityType.SUB_AGENT.identity(deletedProfile.getAgentId());
        List<AgentProfile> referencingProfiles = agentProfileRepository
                .findByTenantIdOrderByUpdatedAtDesc(deletedProfile.getTenantId()).stream()
                .filter(profile -> !profile.getAgentId().equals(deletedProfile.getAgentId()))
                .filter(profile -> removeCapabilityBinding(profile, deletedIdentity))
                .toList();
        if (!referencingProfiles.isEmpty()) {
            agentProfileRepository.saveAll(referencingProfiles);
        }
    }

    private boolean removeCapabilityBinding(AgentProfile profile, String deletedIdentity) {
        List<String> currentBindings = capabilityConfigurationCodec.decodeCapabilityBindings(
                profile.getCapabilityBindings(), profile.getAgentId());
        List<String> retainedBindings = currentBindings.stream()
                .filter(identity -> !deletedIdentity.equals(identity))
                .toList();
        if (retainedBindings.size() == currentBindings.size()) {
            return false;
        }
        profile.setCapabilityBindings(capabilityConfigurationCodec.encodeCapabilityBindings(retainedBindings));
        return true;
    }

    @Transactional(readOnly = true)
    public AgentProfile requireEnabledAgent(String agentId) {
        AgentProfile profile = requireCurrentTenantAgent(agentId);
        if (!profile.isEnabled()) {
            throw new IllegalArgumentException("Agent 已禁用");
        }
        return profile;
    }

    private void applyProfileRequest(AgentProfile profile,
            AgentProfileRequest request,
            List<String> capabilityBindings) {
        profile.setName(request.name().trim());
        profile.setDescription(blankToNull(request.description()));
        profile.setSystemPrompt(blankToNull(request.systemPrompt()));
        profile.setModelId(blankToNull(request.modelId()));
        profile.setExecutionMode(blankToNull(request.executionMode()) != null ? request.executionMode().trim() : "auto");
        profile.setCapabilityBindings(capabilityConfigurationCodec.encodeCapabilityBindings(capabilityBindings));
        profile.setEnabled(request.enabled() == null || request.enabled());
    }

    private AgentProfileResponse toResponse(AgentProfile profile) {
        List<String> capabilityBindings =
                capabilityConfigurationCodec.decodeCapabilityBindings(
                        profile.getCapabilityBindings(), profile.getAgentId());
        return AgentProfileResponse.from(profile, capabilityBindings);
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

    private List<String> validate(AgentProfileRequest request, AgentProfile existingProfile) {
        if (request == null) {
            throw new IllegalArgumentException("Agent 请求不能为空");
        }
        if (!StringUtils.hasText(request.name())) {
            throw new IllegalArgumentException("Agent 名称不能为空");
        }
        validateExecutionMode(request.executionMode());
        return capabilityService.resolveBindingsForSave(
                existingProfile,
                request.capabilityBindings());
    }

    private void validateExecutionMode(String executionMode) {
        if (!StringUtils.hasText(executionMode)) {
            return;
        }
        String normalizedMode = executionMode.trim().toLowerCase();
        if (!"auto".equals(normalizedMode) && !"chat".equals(normalizedMode)
                && !"react".equals(normalizedMode) && !"orchestrated".equals(normalizedMode)) {
            throw new IllegalArgumentException("执行模式仅支持 auto、chat、react 或 orchestrated");
        }
    }

    private AgentProfile createDefaultAgent(String tenantId, String userId) {
        AgentProfile profile = new AgentProfile();
        profile.setName(DEFAULT_AGENT_NAME);
        profile.setDescription(DEFAULT_AGENT_DESCRIPTION);
        profile.setSystemPrompt(DEFAULT_AGENT_PROMPT);
        profile.setExecutionMode("auto");
        profile.setCapabilityBindings(capabilityConfigurationCodec.encodeCapabilityBindings(
                capabilityService.snapshotDefaultToolBindings()));
        profile.setEnabled(true);
        profile.setDefaultAgent(true);
        profile.setTenantId(tenantId);
        profile.setCreatedBy(userId);
        return agentProfileRepository.save(profile);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
