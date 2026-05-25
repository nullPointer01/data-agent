package com.ai.service;

import com.ai.model.SkillConfig;
import com.ai.model.SkillPromptHistory;
import com.ai.repository.SkillConfigRepository;
import com.ai.repository.SkillPromptHistoryRepository;
import com.ai.security.SecurityContextHelper;
import com.ai.skill.DynamicSkill;
import com.ai.skill.SkillManager;
import com.ai.skill.dto.SkillHistoryListResponse;
import com.ai.skill.dto.SkillHistoryResponse;
import com.ai.skill.dto.SkillListResponse;
import com.ai.skill.dto.SkillMutationResponse;
import com.ai.skill.dto.SkillRequest;
import com.ai.skill.dto.SkillResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 技能管理应用服务默认实现。
 *
 * @author data-agent
 */
@Service
public class SkillServiceImpl implements SkillService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkillServiceImpl.class);
    private static final String DEFAULT_TENANT_ID = "default";
    private static final String DEFAULT_SKILL_VERSION = "1.0";
    private static final String DEFAULT_API_METHOD = "POST";
    private static final String DEFAULT_SKILL_SOURCE = "manual";
    private static final String EMPTY_VALUE = "";
    private static final String KEY_SUCCESS = "success";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_SKILL_ID = "skillId";
    private static final String KEY_RESULT = "result";
    private static final String KEY_QUERY = "query";
    private static final String KEY_DATA = "data";
    private static final String CONFIG_KEY_API_URL = "apiUrl";
    private static final String CONFIG_KEY_API_METHOD = "apiMethod";
    private static final String CONFIG_KEY_API_HEADERS = "apiHeaders";
    private static final String CONFIG_KEY_PROMPT_TEMPLATE = "promptTemplate";
    private static final String CONFIG_KEY_RESPONSE_TEMPLATE = "responseTemplate";
    private static final String CONFIG_KEY_KEYWORDS = "keywords";
    private static final String CONFIG_KEY_STEPS = "steps";
    private static final String CONFIG_KEY_AUTO_ATTACH = "autoAttach";
    private static final String MESSAGE_NOT_FOUND_OR_DENIED = "Skill不存在或无权限";
    private static final String MESSAGE_NOT_FOUND_OR_DISABLED = "Skill不存在或已禁用";
    private static final String DEFAULT_UPDATE_REMARK = "手动更新";
    private static final String ROLLBACK_REMARK = "回滚前自动保存";

    private final SkillConfigRepository skillConfigRepository;
    private final SkillPromptHistoryRepository skillPromptHistoryRepository;
    private final SkillManager skillManager;
    private final SecurityContextHelper securityContextHelper;

    public SkillServiceImpl(SkillConfigRepository skillConfigRepository,
            SkillPromptHistoryRepository skillPromptHistoryRepository,
            SkillManager skillManager,
            SecurityContextHelper securityContextHelper) {
        this.skillConfigRepository = skillConfigRepository;
        this.skillPromptHistoryRepository = skillPromptHistoryRepository;
        this.skillManager = skillManager;
        this.securityContextHelper = securityContextHelper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillMutationResponse createSkill(SkillRequest request) {
        validateCreateRequest(request);
        SkillConfig config = new SkillConfig();
        applyCreateRequest(config, request);
        config.setEnabled(true);
        config.setTenantId(securityContextHelper.getCurrentTenantId());
        config.setCreatedBy(securityContextHelper.getCurrentUserId());

        skillConfigRepository.save(config);
        registerToSkillManager(config);

        LOGGER.info("技能已创建: {}, 租户: {}", config.getName(), config.getTenantId());
        return SkillMutationResponse.created(config.getSkillId(), config.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public SkillListResponse listSkills() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        List<SkillConfig> skills = new ArrayList<>(skillConfigRepository.findByTenantId(tenantId));
        if (!DEFAULT_TENANT_ID.equals(tenantId)) {
            skills.addAll(skillConfigRepository.findByTenantId(DEFAULT_TENANT_ID));
        }
        return new SkillListResponse(true, skills.stream()
                .map(SkillResponse::from)
                .collect(Collectors.toList()));
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
        skillConfigRepository.save(config);
        registerToSkillManager(config);

        LOGGER.info("技能已更新: {}, 已保存历史版本: {}", skillId, nextVersion);
        return SkillMutationResponse.updated(nextVersion);
    }

    @Override
    @Transactional(readOnly = true)
    public SkillHistoryListResponse listHistory(String skillId) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        if (findMutableSkill(skillId, tenantId) == null) {
            return new SkillHistoryListResponse(false, List.of());
        }
        List<SkillHistoryResponse> history = skillPromptHistoryRepository
                .findBySkillIdAndTenantIdOrderByVersionDesc(skillId, tenantId)
                .stream()
                .map(SkillHistoryResponse::from)
                .collect(Collectors.toList());
        return new SkillHistoryListResponse(true, history);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillMutationResponse rollbackSkill(String skillId, int version) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        SkillConfig config = findMutableSkill(skillId, tenantId);
        if (config == null) {
            return SkillMutationResponse.failure(MESSAGE_NOT_FOUND_OR_DENIED);
        }

        List<SkillPromptHistory> history = skillPromptHistoryRepository
                .findBySkillIdAndTenantIdOrderByVersionDesc(skillId, tenantId);
        var targetHistory = history.stream()
                .filter(item -> item.getVersion() == version)
                .findFirst();
        if (targetHistory.isEmpty()) {
            return SkillMutationResponse.failure("版本不存在");
        }

        saveHistory(config, tenantId, ROLLBACK_REMARK);
        SkillPromptHistory target = targetHistory.get();
        config.setPromptTemplate(target.getPromptTemplate());
        config.setSteps(target.getSteps());
        skillConfigRepository.save(config);
        registerToSkillManager(config);

        return SkillMutationResponse.rolledBack(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillMutationResponse deleteSkill(String skillId) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        var existingOptional = skillConfigRepository.findBySkillIdAndTenantId(skillId, tenantId);
        if (existingOptional.isEmpty()) {
            return SkillMutationResponse.failure(MESSAGE_NOT_FOUND_OR_DENIED);
        }

        SkillConfig config = existingOptional.get();
        skillConfigRepository.delete(config);
        skillManager.unregisterSkill(config.getName());
        LOGGER.info("技能已删除: {}", skillId);
        return SkillMutationResponse.deleted();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillMutationResponse toggleSkill(String skillId) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        var existingOptional = skillConfigRepository.findBySkillIdAndTenantId(skillId, tenantId);
        if (existingOptional.isEmpty()) {
            return SkillMutationResponse.failure(MESSAGE_NOT_FOUND_OR_DENIED);
        }

        SkillConfig config = existingOptional.get();
        config.setEnabled(!config.isEnabled());
        skillConfigRepository.save(config);

        if (config.isEnabled()) {
            registerToSkillManager(config);
        } else {
            skillManager.unregisterSkill(config.getName());
        }

        return SkillMutationResponse.toggled(skillId, config.isEnabled());
    }

    @Override
    public Map<String, Object> executeSkill(String skillId, String query, Map<String, Object> data) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        var configOptional = skillConfigRepository.findBySkillIdAndTenantId(skillId, tenantId);
        if (configOptional.isPresent() && configOptional.get().isEnabled()) {
            return Map.of(KEY_SUCCESS, true, KEY_RESULT,
                    Map.of(KEY_SKILL_ID, skillId, KEY_QUERY, query, KEY_DATA, data));
        }
        return Map.of(KEY_SUCCESS, false, KEY_MESSAGE, MESSAGE_NOT_FOUND_OR_DISABLED);
    }

    public void registerToSkillManager(SkillConfig config) {
        DynamicSkill dynamicSkill = new DynamicSkill(
                config.getName(),
                config.getDescription(),
                Map.of(
                        CONFIG_KEY_API_URL, nullToEmpty(config.getApiUrl()),
                        CONFIG_KEY_API_METHOD, config.getApiMethod() != null ? config.getApiMethod()
                                : DEFAULT_API_METHOD,
                        CONFIG_KEY_API_HEADERS, nullToEmpty(config.getApiHeaders()),
                        CONFIG_KEY_PROMPT_TEMPLATE, nullToEmpty(config.getPromptTemplate()),
                        CONFIG_KEY_RESPONSE_TEMPLATE, nullToEmpty(config.getResponseTemplate()),
                        CONFIG_KEY_KEYWORDS, nullToEmpty(config.getKeywords()),
                        CONFIG_KEY_STEPS, nullToEmpty(config.getSteps()),
                        CONFIG_KEY_AUTO_ATTACH, nullToEmpty(config.getAutoAttach())));
        skillManager.registerSkill(dynamicSkill);
    }

    @Transactional(readOnly = true)
    public List<SkillConfig> getEnabledSkills(String tenantId) {
        return skillConfigRepository.findByTenantIdAndEnabledTrue(tenantId);
    }

    private void applyCreateRequest(SkillConfig config, SkillRequest request) {
        config.setName(request.name().trim());
        config.setDescription(request.description());
        config.setVersion(StringUtils.hasText(request.version()) ? request.version().trim() : DEFAULT_SKILL_VERSION);
        config.setApiUrl(request.apiUrl());
        config.setApiMethod(StringUtils.hasText(request.apiMethod()) ? request.apiMethod().trim()
                : DEFAULT_API_METHOD);
        config.setApiHeaders(request.apiHeaders());
        config.setPromptTemplate(request.promptTemplate());
        config.setResponseTemplate(request.responseTemplate());
        config.setKeywords(request.keywords());
        config.setSteps(request.steps());
        config.setAutoAttach(request.autoAttach());
        config.setSource(StringUtils.hasText(request.source()) ? request.source().trim() : DEFAULT_SKILL_SOURCE);
    }

    private void applyUpdateRequest(SkillConfig config, SkillRequest request) {
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
        if (request.keywords() != null) {
            config.setKeywords(request.keywords());
        }
        if (request.apiUrl() != null) {
            config.setApiUrl(request.apiUrl());
        }
        if (request.apiMethod() != null) {
            config.setApiMethod(request.apiMethod());
        }
        if (request.apiHeaders() != null) {
            config.setApiHeaders(request.apiHeaders());
        }
        if (request.responseTemplate() != null) {
            config.setResponseTemplate(request.responseTemplate());
        }
        if (request.autoAttach() != null) {
            config.setAutoAttach(request.autoAttach());
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
        history.setRemark(remark);
        history.setTenantId(tenantId);
        skillPromptHistoryRepository.save(history);
        return nextVersion;
    }

    private SkillConfig findMutableSkill(String skillId, String tenantId) {
        return skillConfigRepository.findBySkillIdAndTenantId(skillId, tenantId)
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

    private String nullToEmpty(String value) {
        return value == null ? EMPTY_VALUE : value;
    }
}
