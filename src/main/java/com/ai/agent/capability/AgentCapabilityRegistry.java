package com.ai.agent.capability;

import com.ai.agent.tool.AgentTools;
import com.ai.agent.tool.governance.AgentToolDescriptor;
import com.ai.agent.tool.governance.AgentToolGovernanceProperties;
import com.ai.agent.tool.governance.AgentToolRegistry;
import com.ai.agent.tool.governance.AgentToolRiskLevel;
import com.ai.model.AgentProfile;
import com.ai.model.SkillConfig;
import com.ai.repository.AgentProfileRepository;
import com.ai.repository.SkillConfigRepository;
import com.ai.skill.SkillManager;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.internal.JsonSchemaElementJsonUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 将现有 Tool Registry、Skill 配置和 Agent Profile 投影为统一能力目录。
 * 执行仍由原运行时负责，本注册表不持有执行器，也不会绕过 Tool Pipeline。
 *
 * @author data-agent
 */
@Component
public class AgentCapabilityRegistry {

    private static final String PLATFORM_OWNER = "platform";
    private static final String PLATFORM_TENANT = "*";
    private static final String DEFAULT_TENANT = "default";
    private static final String USE_PERMISSION = "app:use";
    private static final String TOOL_VERSION = "1.0.0";

    private final AgentToolRegistry toolRegistry;
    private final AgentCapabilityConfigurationCodec configurationCodec;
    private final SkillConfigRepository skillRepository;
    private final AgentProfileRepository agentRepository;
    private final SkillManager skillManager;
    private final AgentCapabilityValidator validator;
    private final AgentToolGovernanceProperties toolGovernanceProperties;

    public AgentCapabilityRegistry(AgentToolRegistry toolRegistry,
            AgentCapabilityConfigurationCodec configurationCodec,
            SkillConfigRepository skillRepository,
            AgentProfileRepository agentRepository,
            SkillManager skillManager,
            AgentCapabilityValidator validator,
            AgentToolGovernanceProperties toolGovernanceProperties) {
        this.toolRegistry = toolRegistry;
        this.configurationCodec = configurationCodec;
        this.skillRepository = skillRepository;
        this.agentRepository = agentRepository;
        this.skillManager = skillManager;
        this.validator = validator;
        this.toolGovernanceProperties = toolGovernanceProperties;
    }

    /**
     * 获取指定租户当前可发现的能力注册快照。
     *
     * @param tenantId 当前租户
     * @return 已校验、顺序稳定的能力描述符
     */
    public List<AgentCapabilityDescriptor> snapshot(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("能力目录租户不能为空");
        }
        Map<String, AgentCapabilityDescriptor> registered = new LinkedHashMap<>();
        toolRegistry.registeredToolNames().stream()
                .filter(name -> !AgentTools.isCapabilityAdapter(name))
                .sorted()
                .map(this::toolDescriptor)
                .forEach(descriptor -> register(registered, descriptor));
        visibleSkills(tenantId).stream()
                .map(this::skillDescriptor)
                .forEach(descriptor -> register(registered, descriptor));
        agentRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId).stream()
                .filter(profile -> !profile.isDefaultAgent())
                .map(this::agentDescriptor)
                .forEach(descriptor -> register(registered, descriptor));
        return registered.values().stream()
                .sorted(Comparator.comparing(AgentCapabilityDescriptor::type)
                        .thenComparing(AgentCapabilityDescriptor::name)
                        .thenComparing(AgentCapabilityDescriptor::identity))
                .toList();
    }

    /**
     * 按统一稳定标识查询租户可见能力。
     *
     * @param tenantId 当前租户
     * @param identity 能力标识
     * @return 匹配描述符
     */
    public Optional<AgentCapabilityDescriptor> find(String tenantId, String identity) {
        if (identity == null || identity.isBlank()) {
            return Optional.empty();
        }
        return snapshot(tenantId).stream()
                .filter(descriptor -> descriptor.identity().equals(identity.trim()))
                .findFirst();
    }

    private void register(Map<String, AgentCapabilityDescriptor> registered,
            AgentCapabilityDescriptor descriptor) {
        validator.validate(descriptor);
        String registrationKey = descriptor.identity() + "@" + descriptor.version();
        if (registered.putIfAbsent(registrationKey, descriptor) != null) {
            throw new IllegalStateException("能力身份与版本重复: " + registrationKey);
        }
    }

    private AgentCapabilityDescriptor toolDescriptor(String name) {
        AgentToolRegistry.RegisteredTool registered = toolRegistry.find(name)
                .orElseThrow(() -> new IllegalStateException("工具注册信息不存在: " + name));
        ToolSpecification specification = registered.specification();
        AgentToolDescriptor governance = registered.descriptor();
        boolean enabled = toolRegistry.isEnabled(name);
        boolean riskAllowed = governance.risk().ordinal() <= toolGovernanceProperties.getMaxRisk().ordinal();
        return new AgentCapabilityDescriptor(
                AgentCapabilityType.TOOL.identity(name),
                AgentCapabilityType.TOOL,
                TOOL_VERSION,
                name,
                specification.description(),
                toolInputContract(specification),
                Map.of("type", "string", "sanitization", "tool-governance-pipeline"),
                PLATFORM_OWNER,
                PLATFORM_TENANT,
                Set.of(governance.requiredPermission()),
                toolRisk(governance),
                AgentCapabilityDescriptor.LifecycleState.ACTIVE,
                enabled && riskAllowed
                        ? AgentCapabilityDescriptor.Availability.ready()
                        : AgentCapabilityDescriptor.Availability.unavailable(
                                enabled ? "RISK_POLICY_DENIED" : "DISABLED_BY_RUNTIME_POLICY",
                                enabled ? "工具风险等级超过当前环境上限" : "当前环境未启用该工具"));
    }

    private List<SkillConfig> visibleSkills(String tenantId) {
        List<SkillConfig> skills = new ArrayList<>(skillRepository.findByTenantId(tenantId));
        if (!DEFAULT_TENANT.equals(tenantId)) {
            skills.addAll(skillRepository.findByTenantId(DEFAULT_TENANT));
        }
        return skills;
    }

    private AgentCapabilityDescriptor skillDescriptor(SkillConfig skill) {
        boolean enabled = skill.isEnabled();
        boolean loaded = enabled && skillManager.findSkillById(skill.getSkillId()) != null;
        boolean catalogAdapterEnabled = toolAllowedByRuntimePolicy(AgentTools.LIST_AVAILABLE_SKILLS_TOOL);
        boolean executionAdapterEnabled = toolAllowedByRuntimePolicy(AgentTools.USE_SKILL_TOOL);
        boolean externalAccess = hasText(skill.getApiUrl());
        AgentCapabilityDescriptor.Availability availability = !enabled
                ? AgentCapabilityDescriptor.Availability.unavailable("CAPABILITY_DISABLED", "Skill 已停用")
                : !loaded
                        ? AgentCapabilityDescriptor.Availability.unavailable(
                                "RUNTIME_NOT_LOADED", "Skill 配置已启用但未加载到运行时")
                        : !executionAdapterEnabled
                                ? AgentCapabilityDescriptor.Availability.unavailable(
                                        "SKILL_ADAPTER_DISABLED", "Skill 执行适配器被运行策略关闭")
                                : !catalogAdapterEnabled
                                        ? AgentCapabilityDescriptor.Availability.unavailable(
                                                "SKILL_CATALOG_DISABLED", "Skill 目录适配器被运行策略关闭")
                                        : AgentCapabilityDescriptor.Availability.ready();
        return new AgentCapabilityDescriptor(
                AgentCapabilityType.SKILL.identity(skill.getSkillId()),
                AgentCapabilityType.SKILL,
                valueOrDefault(skill.getVersion(), "1.0"),
                skill.getName(),
                skill.getDescription(),
                standardTaskInput(),
                Map.of("type", "string", "contract", "skill-result"),
                resolveOwner(skill.getCreatedBy(), skill.getTenantId()),
                skill.getTenantId(),
                Set.of(USE_PERMISSION),
                new AgentCapabilityDescriptor.RiskMetadata(
                        externalAccess ? AgentCapabilityDescriptor.RiskLevel.MEDIUM
                                : AgentCapabilityDescriptor.RiskLevel.LOW,
                        !externalAccess,
                        !externalAccess,
                        false,
                        externalAccess,
                        "PLATFORM_SKILL_POLICY"),
                enabled ? AgentCapabilityDescriptor.LifecycleState.ACTIVE
                        : AgentCapabilityDescriptor.LifecycleState.DISABLED,
                availability);
    }

    private AgentCapabilityDescriptor agentDescriptor(AgentProfile profile) {
        List<AgentToolDescriptor> tools = effectiveTools(profile);
        boolean readOnly = tools.stream().allMatch(AgentToolDescriptor::readOnly);
        boolean idempotent = tools.stream().allMatch(AgentToolDescriptor::idempotent);
        boolean approvalRequired = tools.stream().anyMatch(AgentToolDescriptor::approvalRequired);
        Set<String> permissions = new LinkedHashSet<>();
        permissions.add(USE_PERMISSION);
        tools.stream().map(AgentToolDescriptor::requiredPermission).forEach(permissions::add);
        boolean enabled = profile.isEnabled();
        boolean delegationAdapterEnabled = toolAllowedByRuntimePolicy(AgentTools.DELEGATE_TO_AGENT_TOOL);
        return new AgentCapabilityDescriptor(
                AgentCapabilityType.SUB_AGENT.identity(profile.getAgentId()),
                AgentCapabilityType.SUB_AGENT,
                profileVersion(profile),
                profile.getName(),
                profile.getDescription(),
                standardTaskInput(),
                Map.of("type", "object", "contract", "analysis-response"),
                profile.getCreatedBy(),
                profile.getTenantId(),
                permissions,
                new AgentCapabilityDescriptor.RiskMetadata(
                        highestRisk(tools), readOnly, idempotent, approvalRequired,
                        false, "DERIVED_FROM_EFFECTIVE_TOOLS"),
                enabled ? AgentCapabilityDescriptor.LifecycleState.ACTIVE
                        : AgentCapabilityDescriptor.LifecycleState.DISABLED,
                enabled && delegationAdapterEnabled
                        ? AgentCapabilityDescriptor.Availability.ready()
                        : AgentCapabilityDescriptor.Availability.unavailable(
                                enabled ? "DELEGATION_ADAPTER_DISABLED" : "CAPABILITY_DISABLED",
                                enabled ? "子 Agent 委派适配器被运行策略关闭" : "子 Agent 已停用"));
    }

    private List<AgentToolDescriptor> effectiveTools(AgentProfile profile) {
        Set<String> selected = Set.copyOf(AgentCapabilityBindingSet.of(
                        configurationCodec.decodeCapabilityBindings(
                                profile.getCapabilityBindings(), profile.getAgentId()))
                .sourceIds(AgentCapabilityType.TOOL));
        return selected.stream()
                .map(toolRegistry::find)
                .flatMap(Optional::stream)
                .filter(entry -> toolRegistry.isEnabled(entry.descriptor().name()))
                .map(AgentToolRegistry.RegisteredTool::descriptor)
                .toList();
    }

    private AgentCapabilityDescriptor.RiskMetadata toolRisk(AgentToolDescriptor tool) {
        return new AgentCapabilityDescriptor.RiskMetadata(
                mapRisk(tool.risk()), tool.readOnly(), tool.idempotent(),
                tool.approvalRequired(), false, "DECLARED_TOOL_POLICY");
    }

    private boolean toolAllowedByRuntimePolicy(String toolName) {
        return toolRegistry.find(toolName)
                .filter(registered -> toolRegistry.isEnabled(toolName))
                .map(AgentToolRegistry.RegisteredTool::descriptor)
                .map(descriptor -> descriptor.risk().ordinal()
                        <= toolGovernanceProperties.getMaxRisk().ordinal())
                .orElse(false);
    }

    private AgentCapabilityDescriptor.RiskLevel highestRisk(List<AgentToolDescriptor> tools) {
        return tools.stream().map(AgentToolDescriptor::risk).map(this::mapRisk)
                .max(Comparator.comparingInt(risk -> risk.ordinal()))
                .orElse(AgentCapabilityDescriptor.RiskLevel.LOW);
    }

    private AgentCapabilityDescriptor.RiskLevel mapRisk(AgentToolRiskLevel risk) {
        return AgentCapabilityDescriptor.RiskLevel.valueOf(risk.name());
    }

    private Map<String, Object> standardTaskInput() {
        return Map.of(
                "type", "object",
                "required", List.of("question"),
                "properties", Map.of(
                        "question", Map.of("type", "string"),
                        "context", Map.of("type", "object", "optional", true)));
    }

    private Map<String, Object> toolInputContract(ToolSpecification specification) {
        var schema = specification.parameters();
        return schema == null
                ? Map.of("type", "object", "properties", Map.of())
                : JsonSchemaElementJsonUtils.toMap(schema);
    }

    private String profileVersion(AgentProfile profile) {
        LocalDateTime revision = profile.getUpdatedAt() != null ? profile.getUpdatedAt() : profile.getCreatedAt();
        return revision == null ? "revision:legacy" : "revision:" + revision;
    }

    private String resolveOwner(String ownerId, String tenantId) {
        if (hasText(ownerId)) {
            return ownerId.trim();
        }
        return DEFAULT_TENANT.equals(tenantId) ? PLATFORM_OWNER : ownerId;
    }

    private String valueOrDefault(String value, String defaultValue) {
        return hasText(value) ? value.trim() : defaultValue;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
