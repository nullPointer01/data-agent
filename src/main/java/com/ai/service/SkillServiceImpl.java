package com.ai.service;

import com.ai.api.ListPageResponse;
import com.ai.api.ListPaging;
import com.ai.api.CatalogListFilter;
import com.ai.model.SkillConfig;
import com.ai.model.SkillPromptHistory;
import com.ai.repository.SkillConfigRepository;
import com.ai.repository.SkillPromptHistoryRepository;
import com.ai.security.SecurityContextHelper;
import com.ai.skill.SkillCatalog;
import com.ai.skill.SkillToolDependencies;
import com.ai.agent.tool.governance.AgentToolRegistry;
import com.ai.skill.dto.SkillHistoryResponse;
import com.ai.skill.dto.SkillMutationResponse;
import com.ai.skill.dto.SkillRequest;
import com.ai.skill.dto.SkillResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


/**
 * 技能管理应用服务默认实现。
 *
 * @author data-agent
 */
@Service
public class SkillServiceImpl implements SkillService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkillServiceImpl.class);
    private static final String DEFAULT_SKILL_VERSION = "1.0";
    private static final String DEFAULT_API_METHOD = "POST";
    private static final String DEFAULT_SKILL_SOURCE = "manual";
    private static final String MESSAGE_NOT_FOUND_OR_DENIED = "Skill不存在或无权限";
    private static final String DEFAULT_UPDATE_REMARK = "手动更新";
    private static final String ROLLBACK_REMARK = "回滚前自动保存";

    private final SkillConfigRepository skillConfigRepository;
    private final SkillPromptHistoryRepository skillPromptHistoryRepository;
    private final SecurityContextHelper securityContextHelper;
    private final AgentToolRegistry tools;

    public SkillServiceImpl(SkillConfigRepository skillConfigRepository,
            SkillPromptHistoryRepository skillPromptHistoryRepository,
            SecurityContextHelper securityContextHelper, AgentToolRegistry tools) {
        this.skillConfigRepository = skillConfigRepository;
        this.skillPromptHistoryRepository = skillPromptHistoryRepository;
        this.securityContextHelper = securityContextHelper;
        this.tools = tools;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillMutationResponse createSkill(SkillRequest request) {
        validateCreateRequest(request);
        SkillConfig config = new SkillConfig();
        applyCreateRequest(config, request);
        config.setEnabled(request.enabled() == null || request.enabled());
        config.setTenantId(securityContextHelper.getCurrentTenantId());
        config.setCreatedBy(securityContextHelper.getCurrentUserId());

        validateInstructions(config);
        skillConfigRepository.save(config);

        LOGGER.info("技能已创建: {}, 租户: {}", config.getName(), config.getTenantId());
        return SkillMutationResponse.created(config.getSkillId(), config.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public ListPageResponse<SkillResponse> listSkills(int page, int size, String query) {
        ListPaging.page(page, size);
        var filter = new CatalogListFilter(securityContextHelper.getCurrentTenantId(), null,
                true, false, null, query);
        String viewer = securityContextHelper.getCurrentUserId();
        return ListPageResponse.fetch(page, size, skillConfigRepository.countList(filter, viewer),
                pageable -> skillConfigRepository.findListPage(filter, viewer, pageable)).map(SkillResponse::from);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillMutationResponse updateSkill(String skillId, SkillRequest request) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        SkillConfig config = findMutableSkill(skillId, tenantId);
        if (config == null) {
            return SkillMutationResponse.failure(MESSAGE_NOT_FOUND_OR_DENIED);
        }

        int nextVersion = saveHistory(config, tenantId, defaultRemark(request.remark()));
        applyUpdateRequest(config, request);
        validateInstructions(config);
        skillConfigRepository.save(config);

        LOGGER.info("技能已更新: {}, 已保存历史版本: {}", skillId, nextVersion);
        return SkillMutationResponse.updated(nextVersion);
    }

    @Override
    @Transactional(readOnly = true)
    public ListPageResponse<SkillHistoryResponse> listHistory(String skillId, int page, int size) {
        ListPaging.page(page, size);
        String tenant = securityContextHelper.getCurrentTenantId();
        if (findMutableSkill(skillId, tenant) == null) {
            throw new IllegalArgumentException(MESSAGE_NOT_FOUND_OR_DENIED);
        }
        return ListPageResponse.fetch(page, size, skillPromptHistoryRepository.countHistory(skillId, tenant),
                pageable -> skillPromptHistoryRepository.findHistoryPage(skillId, tenant, pageable))
                .map(SkillHistoryResponse::from);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillMutationResponse rollbackSkill(String skillId, int version) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        SkillConfig config = findMutableSkill(skillId, tenantId);
        if (config == null) {
            return SkillMutationResponse.failure(MESSAGE_NOT_FOUND_OR_DENIED);
        }

        var targetHistory = skillPromptHistoryRepository.findVersion(skillId, tenantId, version);
        if (targetHistory.isEmpty()) {
            return SkillMutationResponse.failure("版本不存在");
        }

        saveHistory(config, tenantId, ROLLBACK_REMARK);
        SkillPromptHistory target = targetHistory.get();
        config.setPromptTemplate(target.getPromptTemplate());
        config.setSteps(target.getSteps());
        config.setToolDependencies(target.getToolDependencies());
        validateInstructions(config);
        skillConfigRepository.save(config);

        return SkillMutationResponse.rolledBack(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillMutationResponse deleteSkill(String skillId) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        var existingOptional = java.util.Optional.ofNullable(findMutableSkill(skillId, tenantId));
        if (existingOptional.isEmpty()) {
            return SkillMutationResponse.failure(MESSAGE_NOT_FOUND_OR_DENIED);
        }

        SkillConfig config = existingOptional.get();
        skillConfigRepository.delete(config);
        LOGGER.info("技能已删除: {}", skillId);
        return SkillMutationResponse.deleted();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillMutationResponse toggleSkill(String skillId) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        var existingOptional = java.util.Optional.ofNullable(findMutableSkill(skillId, tenantId));
        if (existingOptional.isEmpty()) {
            return SkillMutationResponse.failure(MESSAGE_NOT_FOUND_OR_DENIED);
        }

        SkillConfig config = existingOptional.get();
        config.setEnabled(!config.isEnabled());
        validateInstructions(config);
        skillConfigRepository.save(config);

        return SkillMutationResponse.toggled(skillId, config.isEnabled());
    }

    private void applyCreateRequest(SkillConfig config, SkillRequest request) {
        config.setName(request.name().trim());
        config.setDescription(request.description());
        config.setVersion(StringUtils.hasText(request.version()) ? request.version().trim() : DEFAULT_SKILL_VERSION);
        validateLegacyApiRequest(request);
        config.setPromptTemplate(request.promptTemplate());
        config.setSteps(request.steps());
        config.setToolDependencies(SkillToolDependencies.normalize(request.toolDependencies()));
        config.setSource(StringUtils.hasText(request.source()) ? request.source().trim() : DEFAULT_SKILL_SOURCE);
    }

    private void applyUpdateRequest(SkillConfig config, SkillRequest request) {
        validateLegacyApiRequest(request);
        if (StringUtils.hasText(request.name())) {
            config.setName(request.name().trim());
        }
        if (request.description() != null) {
            config.setDescription(request.description());
        }
        if (request.promptTemplate() != null) {
            config.setPromptTemplate(request.promptTemplate());
        }
        if (request.steps() != null) {
            config.setSteps(request.steps());
        }
        if (request.toolDependencies() != null) {
            config.setToolDependencies(SkillToolDependencies.normalize(request.toolDependencies()));
        }
        if (request.apiUrl() != null) {
            config.setApiUrl(request.apiUrl());
            // 明确清空旧 API 时一起移除旧凭据；不把请求凭据放入 Skill 指令或资源。
            config.setApiMethod(DEFAULT_API_METHOD);
            config.setApiHeaders(null);
        }
        if (request.enabled() != null) {
            config.setEnabled(request.enabled());
        }
    }

    private int saveHistory(SkillConfig config, String tenantId, String remark) {
        int nextVersion = skillPromptHistoryRepository
                .findTopBySkillIdAndTenantIdOrderByVersionDesc(config.getSkillId(), tenantId)
                .map(history -> history.getVersion() + 1)
                .orElse(1);
        SkillPromptHistory history = new SkillPromptHistory();
        history.setSkillId(config.getSkillId());
        history.setVersion(nextVersion);
        history.setPromptTemplate(config.getPromptTemplate());
        history.setSteps(config.getSteps());
        history.setToolDependencies(config.getToolDependencies());
        history.setRemark(remark);
        history.setTenantId(tenantId);
        skillPromptHistoryRepository.save(history);
        return nextVersion;
    }

    private SkillConfig findMutableSkill(String skillId, String tenantId) {
        return skillConfigRepository.findBySkillIdAndTenantId(skillId, tenantId)
                .filter(config -> !config.isPrivateSkill()
                        || securityContextHelper.getCurrentUserId().equals(config.getCreatedBy()))
                .orElse(null);
    }

    private void validateCreateRequest(SkillRequest request) {
        if (!StringUtils.hasText(request.name())) {
            throw new IllegalArgumentException("Skill名称不能为空");
        }
    }

    private String defaultRemark(String remark) {
        return StringUtils.hasText(remark) ? remark.trim() : DEFAULT_UPDATE_REMARK;
    }

    private void validateLegacyApiRequest(SkillRequest request) {
        if (StringUtils.hasText(request.apiUrl()) || StringUtils.hasText(request.apiHeaders())) {
            throw new IllegalArgumentException("Skill 只维护操作指引，外部 API 请迁移到 Tool/MCP；旧 API 字段仅支持清空");
        }
    }

    private void validateInstructions(SkillConfig config) {
        if (!tools.registeredToolNames().containsAll(SkillToolDependencies.parse(config.getToolDependencies()))) {
            throw new IllegalArgumentException("技能依赖包含未注册工具，请先接入 Tool/MCP");
        }
        if (config.isEnabled()) {
            String reason = SkillCatalog.configurationIssue(config);
            if (!reason.isEmpty()) {
                throw new IllegalArgumentException(reason);
            }
        }
    }
}
