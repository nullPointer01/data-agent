package com.ai.agent.capability;

import com.ai.agent.tool.governance.AgentToolAuthorizationService;
import com.ai.agent.tool.governance.AgentToolAuthorizationSnapshot;
import com.ai.agent.runtime.AgentExecutionMode;
import com.ai.agent.runtime.AgentRunContext;
import com.ai.agent.runtime.AgentRunScope;
import com.ai.model.AgentProfile;
import com.ai.repository.AgentProfileRepository;
import com.ai.security.SecurityContextHelper;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 面向当前登录用户的能力目录与 Profile 绑定准入服务。
 *
 * @author data-agent
 */
@Service
public class AgentCapabilityService {

    private final AgentCapabilityRegistry registry;
    private final AgentCapabilityConfigurationCodec configurationCodec;
    private final AgentProfileRepository agentProfileRepository;
    private final AgentToolAuthorizationService authorizationService;
    private final SecurityContextHelper securityContextHelper;

    public AgentCapabilityService(AgentCapabilityRegistry registry,
            AgentCapabilityConfigurationCodec configurationCodec,
            AgentProfileRepository agentProfileRepository,
            AgentToolAuthorizationService authorizationService,
            SecurityContextHelper securityContextHelper) {
        this.registry = registry;
        this.configurationCodec = configurationCodec;
        this.agentProfileRepository = agentProfileRepository;
        this.authorizationService = authorizationService;
        this.securityContextHelper = securityContextHelper;
    }

    /**
     * 查询当前用户可见的能力。
     * 不可用但仍有权查看的能力会保留安全原因，供设置页禁选展示。
     *
     * @param editingAgentId 正在编辑的 Agent，可为空；用于排除递归绑定自身
     * @return 权限过滤后的目录
     */
    public CapabilityDirectory currentUserDirectory(String editingAgentId) {
        CallerContext caller = requireCaller();
        List<AgentCapabilityDescriptor> capabilities = visibleCapabilities(caller, editingAgentId);
        return new CapabilityDirectory(true, capabilities);
    }

    /**
     * 使用当前根 Run 的不可变授权快照读取一层 Agent 可见的能力目录。
     * 根请求尚未建立 Run 时回到当前登录身份，嵌套委派则不依赖线程本地的 HTTP 认证对象。
     *
     * @param profile 当前执行层的 Agent Profile
     * @return 与本次运行身份一致的能力目录
     */
    public CapabilityDirectory runtimeDirectory(AgentProfile profile) {
        if (profile == null || profile.getAgentId() == null || profile.getAgentId().isBlank()) {
            throw new IllegalArgumentException("运行时能力目录需要已持久化的 Agent Profile");
        }
        CallerContext caller = runtimeCaller(profile);
        return new CapabilityDirectory(true, visibleCapabilities(caller, profile.getAgentId()));
    }

    /**
     * 解析并校验一次 Agent 保存请求中的统一能力绑定。
     *
     * @param existingProfile 更新时的现有 Profile，创建时为 null
     * @param capabilityBindings 稳定能力身份；必须显式提交，空数组表示零能力
     * @return 规范化且顺序稳定的能力绑定
     */
    public List<String> resolveBindingsForSave(AgentProfile existingProfile,
            List<String> capabilityBindings) {
        return validateExplicitBindings(capabilityBindings,
                existingProfile == null ? null : existingProfile.getAgentId());
    }

    /**
     * 为首次创建的个人 Agent 固化当前可见且可用的 Tool 快照。
     * 后续注册的新 Tool 不会自动扩大已存在 Agent 的权限。
     *
     * @return 当前 Tool 能力稳定身份列表
     */
    public List<String> snapshotDefaultToolBindings() {
        CallerContext caller = requireCaller();
        return visibleCapabilities(caller, null).stream()
                .filter(descriptor -> descriptor.type() == AgentCapabilityType.TOOL)
                .filter(descriptor -> descriptor.availability().available())
                .map(AgentCapabilityDescriptor::identity)
                .toList();
    }

    /**
     * 校验新客户端提交的显式能力列表并返回规范化结果。
     *
     * @param values 稳定能力身份列表
     * @param editingAgentId 正在更新的 Agent 编号，创建时为空
     * @return 规范化且顺序稳定的能力身份
     */
    public List<String> validateExplicitBindings(List<String> values, String editingAgentId) {
        AgentCapabilityBindingSet bindings = AgentCapabilityBindingSet.of(values);
        CallerContext caller = requireCaller();
        String selfIdentity = editingAgentId == null || editingAgentId.isBlank()
                ? null : AgentCapabilityType.SUB_AGENT.identity(editingAgentId);
        if (selfIdentity != null && bindings.contains(selfIdentity)) {
            throw new IllegalArgumentException("Agent 不能绑定自身: " + selfIdentity);
        }

        Map<String, AgentCapabilityDescriptor> visible = new LinkedHashMap<>();
        visibleCapabilities(caller, editingAgentId)
                .forEach(descriptor -> visible.put(descriptor.identity(), descriptor));
        bindings.identities().forEach(identity -> requireBindable(visible, identity));
        validateNoAgentCycle(editingAgentId, bindings, caller.tenantId());
        return bindings.identities();
    }

    /**
     * 在一层 Agent 开始执行前解析当前用户真正可用的能力交集。
     *
     * @param profile 当前层 Agent Profile
     * @param mode 已由统一路由解析的执行模式
     * @return 不可变能力快照
     */
    public AgentCapabilityBindingSnapshot resolveRuntimeBindings(AgentProfile profile, AgentExecutionMode mode) {
        if (profile == null || profile.getAgentId() == null || profile.getAgentId().isBlank()) {
            throw new IllegalArgumentException("运行时能力解析需要已持久化的 Agent Profile");
        }
        if (mode == null) {
            throw new IllegalArgumentException("运行时能力解析缺少执行模式");
        }

        CallerContext caller = runtimeCaller(profile);
        Map<String, AgentCapabilityDescriptor> registered = new LinkedHashMap<>();
        registry.snapshot(caller.tenantId())
                .forEach(descriptor -> registered.put(descriptor.identity(), descriptor));
        List<String> requested = requestedBindings(profile);
        Set<String> tools = new LinkedHashSet<>();
        Set<String> skills = new LinkedHashSet<>();
        Set<String> subAgents = new LinkedHashSet<>();
        List<AgentCapabilityBindingSnapshot.CapabilityExclusion> exclusions = new ArrayList<>();

        for (String identity : requested) {
            AgentCapabilityDescriptor descriptor = registered.get(identity);
            AgentCapabilityBindingSnapshot.CapabilityExclusion exclusion = exclusionFor(
                    descriptor, identity, profile.getAgentId(), mode, caller);
            if (exclusion != null) {
                exclusions.add(exclusion);
                continue;
            }
            String sourceId = descriptor.type().sourceId(identity);
            switch (descriptor.type()) {
                case TOOL -> tools.add(sourceId);
                case SKILL -> skills.add(sourceId);
                case SUB_AGENT -> subAgents.add(sourceId);
            }
        }

        return new AgentCapabilityBindingSnapshot(
                profile.getAgentId(),
                mode,
                requested,
                tools,
                skills,
                subAgents,
                exclusions);
    }

    /**
     * 判断 Profile 是否配置了 Skill，同时遵守统一字段优先和失败关闭合同。
     *
     * @param profile Agent Profile
     * @return 存在 Skill 绑定时返回 true
     */
    public boolean hasConfiguredSkill(AgentProfile profile) {
        if (profile == null) {
            return false;
        }
        return configurationCodec.decodeCapabilityBindings(
                        profile.getCapabilityBindings(), profile.getAgentId()).stream()
                .anyMatch(AgentCapabilityType.SKILL::owns);
    }

    private AgentCapabilityDescriptor requireBindable(
            Map<String, AgentCapabilityDescriptor> visible, String identity) {
        AgentCapabilityDescriptor descriptor = visible.get(identity);
        if (descriptor == null) {
            throw new IllegalArgumentException("能力不存在或当前用户无权绑定: " + identity);
        }
        if (!descriptor.availability().available()) {
            throw new IllegalArgumentException("能力当前不可绑定 [" + identity + "]: "
                    + descriptor.availability().summary());
        }
        return descriptor;
    }

    private CallerContext runtimeCaller(AgentProfile profile) {
        AgentRunContext runContext = AgentRunScope.current().orElse(null);
        if (runContext == null) {
            return requireCaller();
        }
        if (!Objects.equals(runContext.tenantId(), profile.getTenantId())) {
            throw new SecurityException("Agent Profile 不属于当前 Run 租户");
        }
        AgentToolAuthorizationSnapshot authorization = runContext.toolAuthorization();
        if (!authorization.authenticated()
                || !Objects.equals(runContext.userId(), authorization.userId())
                || !Objects.equals(runContext.tenantId(), authorization.tenantId())) {
            throw new SecurityException("Agent Run 能力授权快照无效");
        }
        return new CallerContext(runContext.userId(), runContext.tenantId(), authorization);
    }

    private List<String> requestedBindings(AgentProfile profile) {
        return configurationCodec.decodeCapabilityBindings(
                profile.getCapabilityBindings(), profile.getAgentId());
    }

    private AgentCapabilityBindingSnapshot.CapabilityExclusion exclusionFor(
            AgentCapabilityDescriptor descriptor,
            String identity,
            String currentAgentId,
            AgentExecutionMode mode,
            CallerContext caller) {
        if (descriptor == null) {
            return exclusion(identity, "CAPABILITY_NOT_FOUND", "绑定能力不存在或已删除");
        }
        if (descriptor.type() == AgentCapabilityType.SUB_AGENT
                && descriptor.identity().equals(AgentCapabilityType.SUB_AGENT.identity(currentAgentId))) {
            return exclusion(identity, "SELF_REFERENCE", "Agent 不能委派自身");
        }
        if (!visibleTo(descriptor, caller) || !hasRequiredPermissions(descriptor, caller.authorization())) {
            return exclusion(identity, "CAPABILITY_NOT_AUTHORIZED", "当前用户无权使用该能力");
        }
        if (!descriptor.availability().available()) {
            return exclusion(identity, descriptor.availability().reasonCode(), descriptor.availability().summary());
        }
        if (!modeAllows(mode, descriptor.type())) {
            return exclusion(identity, "EXECUTION_MODE_EXCLUDED", modeExclusionSummary(mode));
        }
        return null;
    }

    private boolean modeAllows(AgentExecutionMode mode, AgentCapabilityType type) {
        return switch (mode) {
            case CHAT -> false;
            case REACT -> type != AgentCapabilityType.SUB_AGENT;
            case ORCHESTRATED -> true;
        };
    }

    private String modeExclusionSummary(AgentExecutionMode mode) {
        return switch (mode) {
            case CHAT -> "Chat 模式不开放可调用能力";
            case REACT -> "ReAct 模式不开放子 Agent 委派";
            case ORCHESTRATED -> "能力被当前执行模式排除";
        };
    }

    private AgentCapabilityBindingSnapshot.CapabilityExclusion exclusion(
            String identity, String reasonCode, String summary) {
        return new AgentCapabilityBindingSnapshot.CapabilityExclusion(identity, reasonCode, summary);
    }

    private List<AgentCapabilityDescriptor> visibleCapabilities(CallerContext caller, String editingAgentId) {
        return registry.snapshot(caller.tenantId()).stream()
                .filter(descriptor -> visibleTo(descriptor, caller))
                .filter(descriptor -> hasRequiredPermissions(descriptor, caller.authorization()))
                .filter(descriptor -> !isEditingAgent(descriptor, editingAgentId))
                .toList();
    }

    private CallerContext requireCaller() {
        String userId = securityContextHelper.getCurrentUserId();
        String tenantId = securityContextHelper.getCurrentTenantId();
        AgentToolAuthorizationSnapshot authorization = authorizationService.resolve(userId, tenantId);
        if (!authorization.authenticated()) {
            throw new IllegalStateException("当前用户身份不可用于能力目录");
        }
        return new CallerContext(userId, tenantId, authorization);
    }

    private boolean visibleTo(AgentCapabilityDescriptor descriptor, CallerContext caller) {
        boolean tenantVisible = "*".equals(descriptor.tenantId())
                || "default".equals(descriptor.tenantId())
                || caller.tenantId().equals(descriptor.tenantId());
        if (!tenantVisible) {
            return false;
        }
        if (descriptor.type() != AgentCapabilityType.SUB_AGENT) {
            return true;
        }
        return caller.userId().equals(descriptor.ownerId());
    }

    private boolean hasRequiredPermissions(AgentCapabilityDescriptor descriptor,
            AgentToolAuthorizationSnapshot authorization) {
        return authorization.administrator()
                || authorization.permissionCodes().contains("*:*")
                || authorization.permissionCodes().containsAll(descriptor.requiredPermissions());
    }

    private boolean isEditingAgent(AgentCapabilityDescriptor descriptor, String editingAgentId) {
        return editingAgentId != null
                && !editingAgentId.isBlank()
                && descriptor.identity().equals(AgentCapabilityType.SUB_AGENT.identity(editingAgentId));
    }

    private void validateNoAgentCycle(String rootAgentId,
            AgentCapabilityBindingSet rootBindings,
            String tenantId) {
        if (rootAgentId == null || rootAgentId.isBlank()
                || rootBindings.sourceIds(AgentCapabilityType.SUB_AGENT).isEmpty()) {
            return;
        }
        Map<String, AgentProfile> profiles = new LinkedHashMap<>();
        agentProfileRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId)
                .forEach(profile -> profiles.put(profile.getAgentId(), profile));
        detectCycle(rootAgentId, rootAgentId, rootBindings, profiles,
                new LinkedHashSet<>(), new LinkedHashSet<>(), new ArrayDeque<>());
    }

    private void detectCycle(String agentId,
            String rootAgentId,
            AgentCapabilityBindingSet rootBindings,
            Map<String, AgentProfile> profiles,
            Set<String> visited,
            Set<String> active,
            Deque<String> path) {
        if (active.contains(agentId)) {
            path.addLast(agentId);
            throw new IllegalArgumentException("Agent 绑定存在循环引用: " + String.join(" -> ", path));
        }
        if (visited.contains(agentId)) {
            return;
        }

        active.add(agentId);
        path.addLast(agentId);
        for (String childId : childAgentIds(agentId, rootAgentId, rootBindings, profiles)) {
            detectCycle(childId, rootAgentId, rootBindings, profiles, visited, active, path);
        }
        path.removeLast();
        active.remove(agentId);
        visited.add(agentId);
    }

    private List<String> childAgentIds(String agentId,
            String rootAgentId,
            AgentCapabilityBindingSet rootBindings,
            Map<String, AgentProfile> profiles) {
        if (rootAgentId.equals(agentId)) {
            return rootBindings.sourceIds(AgentCapabilityType.SUB_AGENT);
        }
        AgentProfile profile = profiles.get(agentId);
        if (profile == null) {
            return List.of();
        }
        return configurationCodec.decodeCapabilityBindings(
                        profile.getCapabilityBindings(), profile.getAgentId()).stream()
                .filter(AgentCapabilityType.SUB_AGENT::owns)
                .map(AgentCapabilityType.SUB_AGENT::sourceId)
                .toList();
    }

    /** 权限过滤后的能力目录响应。 */
    public record CapabilityDirectory(boolean success, List<AgentCapabilityDescriptor> capabilities) {

        public CapabilityDirectory {
            capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        }
    }

    private record CallerContext(
            String userId,
            String tenantId,
            AgentToolAuthorizationSnapshot authorization) {
    }
}
