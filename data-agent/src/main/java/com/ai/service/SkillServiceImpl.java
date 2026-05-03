package com.ai.service;

import com.ai.model.SkillConfig;
import com.ai.repository.SkillConfigRepository;
import com.ai.security.SecurityContextHelper;
import com.ai.skill.DynamicSkill;
import com.ai.skill.SkillManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class SkillServiceImpl implements SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillServiceImpl.class);

    private final SkillConfigRepository skillConfigRepository;
    private final SkillManager skillManager;
    private final SecurityContextHelper securityContextHelper;

    public SkillServiceImpl(SkillConfigRepository skillConfigRepository,
            SkillManager skillManager,
            SecurityContextHelper securityContextHelper) {
        this.skillConfigRepository = skillConfigRepository;
        this.skillManager = skillManager;
        this.securityContextHelper = securityContextHelper;
    }

    @Override
    @Transactional
    public Map<String, Object> createSkill(Map<String, Object> skillConfigMap) {
        try {
            SkillConfig config = new SkillConfig();
            config.setName((String) skillConfigMap.get("name"));
            config.setDescription((String) skillConfigMap.get("description"));
            config.setVersion((String) skillConfigMap.getOrDefault("version", "1.0"));
            config.setApiUrl((String) skillConfigMap.get("apiUrl"));
            config.setApiMethod((String) skillConfigMap.getOrDefault("apiMethod", "POST"));
            config.setApiHeaders((String) skillConfigMap.get("apiHeaders"));
            config.setPromptTemplate((String) skillConfigMap.get("promptTemplate"));
            config.setResponseTemplate((String) skillConfigMap.get("responseTemplate"));
            config.setKeywords((String) skillConfigMap.get("keywords"));
            config.setSteps((String) skillConfigMap.get("steps"));
            config.setAutoAttach((String) skillConfigMap.get("autoAttach"));
            config.setSource((String) skillConfigMap.getOrDefault("source", "manual"));
            config.setEnabled(true);
            config.setTenantId(securityContextHelper.getCurrentTenantId());
            config.setCreatedBy(securityContextHelper.getCurrentUserId());

            skillConfigRepository.save(config);
            registerToSkillManager(config);

            log.info("Skill created: {}, tenant: {}", config.getName(), config.getTenantId());
            return Map.of("success", true, "skillId", config.getSkillId(), "name", config.getName(), "message",
                    "Skill创建成功");
        } catch (Exception e) {
            log.error("Failed to create skill", e);
            return Map.of("success", false, "message", "Skill创建失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> listSkills() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        List<SkillConfig> tenantSkills = skillConfigRepository.findByTenantId(tenantId);
        if (!tenantId.equals("default")) {
            List<SkillConfig> defaultSkills = skillConfigRepository.findByTenantId("default");
            tenantSkills.addAll(defaultSkills);
        }
        return Map.of("success", true, "skills", tenantSkills);
    }

    @Override
    @Transactional
    public Map<String, Object> deleteSkill(String skillId) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        var existingOpt = skillConfigRepository.findBySkillIdAndTenantId(skillId, tenantId);
        if (existingOpt.isEmpty()) {
            return Map.of("success", false, "message", "Skill不存在或无权限");
        }

        skillConfigRepository.delete(existingOpt.get());
        skillManager.unregisterSkill(existingOpt.get().getName());
        log.info("Skill deleted: {}", skillId);
        return Map.of("success", true, "message", "Skill删除成功");
    }

    @Override
    @Transactional
    public Map<String, Object> toggleSkill(String skillId) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        var existingOpt = skillConfigRepository.findBySkillIdAndTenantId(skillId, tenantId);
        if (existingOpt.isEmpty()) {
            return Map.of("success", false, "message", "Skill不存在或无权限");
        }

        SkillConfig config = existingOpt.get();
        config.setEnabled(!config.isEnabled());
        skillConfigRepository.save(config);

        if (config.isEnabled()) {
            registerToSkillManager(config);
        } else {
            skillManager.unregisterSkill(config.getName());
        }

        return Map.of("success", true, "skillId", skillId, "enabled", config.isEnabled(), "message", "Skill状态更新成功");
    }

    @Override
    public Map<String, Object> executeSkill(String skillId, String query, Map<String, Object> data) {
        var configOpt = skillConfigRepository.findById(skillId);
        if (configOpt.isPresent() && configOpt.get().isEnabled()) {
            return Map.of("success", true, "result", Map.of("skillId", skillId, "query", query, "data", data));
        }
        return Map.of("success", false, "message", "Skill不存在或已禁用");
    }

    void registerToSkillManager(SkillConfig config) {
        DynamicSkill dynamicSkill = new DynamicSkill(
                config.getName(),
                config.getDescription(),
                Map.of(
                        "apiUrl", config.getApiUrl() != null ? config.getApiUrl() : "",
                        "apiMethod", config.getApiMethod() != null ? config.getApiMethod() : "POST",
                        "apiHeaders", config.getApiHeaders() != null ? config.getApiHeaders() : "",
                        "promptTemplate", config.getPromptTemplate() != null ? config.getPromptTemplate() : "",
                        "responseTemplate", config.getResponseTemplate() != null ? config.getResponseTemplate() : "",
                        "keywords", config.getKeywords() != null ? config.getKeywords() : "",
                        "steps", config.getSteps() != null ? config.getSteps() : "",
                        "autoAttach", config.getAutoAttach() != null ? config.getAutoAttach() : ""));
        skillManager.registerSkill(dynamicSkill);
    }

    public List<SkillConfig> getEnabledSkills(String tenantId) {
        return skillConfigRepository.findByTenantIdAndEnabledTrue(tenantId);
    }
}
