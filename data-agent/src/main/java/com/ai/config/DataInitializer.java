package com.ai.config;

import com.ai.model.ModelConfig;
import com.ai.model.SkillConfig;
import com.ai.repository.ModelConfigRepository;
import com.ai.repository.SkillConfigRepository;
import com.ai.skill.DynamicSkill;
import com.ai.skill.SkillManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Value("${langchain4j.open-ai.api-key}")
    private String defaultApiKey;

    @Value("${langchain4j.open-ai.model-name}")
    private String defaultModelName;

    @Value("${langchain4j.open-ai.base-url}")
    private String defaultBaseUrl;

    @Value("${langchain4j.open-ai.temperature:0.7}")
    private Double defaultTemperature;

    @Bean
    public CommandLineRunner initData(ModelConfigRepository modelConfigRepository,
            SkillConfigRepository skillConfigRepository,
            SkillManager skillManager) {
        return args -> {
            initDefaultModel(modelConfigRepository);
            initDefaultSkill(skillConfigRepository, skillManager);
            loadExistingSkills(skillConfigRepository, skillManager);
        };
    }

    private void initDefaultModel(ModelConfigRepository repository) {
        if (repository.countByIsDefaultTrue() == 0) {
            ModelConfig defaultModel = new ModelConfig();
            defaultModel.setName("Qwen Plus (默认)");
            defaultModel.setProvider("qwen");
            defaultModel.setApiKey(defaultApiKey);
            defaultModel.setBaseUrl(defaultBaseUrl);
            defaultModel.setModelName(defaultModelName);
            defaultModel.setTemperature(defaultTemperature);
            defaultModel.setMaxTokens(4096);
            defaultModel.setEnabled(true);
            defaultModel.setDefault(true);
            defaultModel.setTenantId("default");

            repository.save(defaultModel);
            log.info("Default model initialized: {} @ {}", defaultModelName, defaultBaseUrl);
        }
    }

    private void initDefaultSkill(SkillConfigRepository repository, SkillManager skillManager) {
        if (repository.countByIsDefaultTrue() == 0) {
            SkillConfig defaultSkill = new SkillConfig();
            defaultSkill.setName("通用数据分析");
            defaultSkill.setDescription("默认数据分析技能，支持通用数据查询、统计分析和趋势预测");
            defaultSkill.setVersion("1.0");
            defaultSkill.setApiUrl("");
            defaultSkill.setApiMethod("POST");
            defaultSkill.setPromptTemplate(
                    "你是数据分析助手。基于{{data}}回答{{query}}。规则:1.有数据时分析数据 2.无数据时提示用户上传 3.绝不编造数据 4.简洁专业");
            defaultSkill.setKeywords("分析,数据,统计,查询,报表,趋势,预测,对比,汇总");
            defaultSkill.setEnabled(true);
            defaultSkill.setDefault(true);
            defaultSkill.setTenantId("default");

            repository.save(defaultSkill);
            log.info("Default skill initialized: {}", defaultSkill.getName());
        }
    }

    private void loadExistingSkills(SkillConfigRepository repository, SkillManager skillManager) {
        List<SkillConfig> skills = repository.findByEnabledTrue();
        for (SkillConfig config : skills) {
            if (config.isDefault()) {
                registerDefaultSkillToManager(config, skillManager);
            } else {
                registerSkillToManager(config, skillManager);
            }
        }
        log.info("Loaded {} enabled skills into SkillManager", skills.size());
    }

    private void registerDefaultSkillToManager(SkillConfig config, SkillManager skillManager) {
        DynamicSkill skill = buildDynamicSkill(config);
        skillManager.registerDefaultSkill(skill);
    }

    private void registerSkillToManager(SkillConfig config, SkillManager skillManager) {
        DynamicSkill skill = buildDynamicSkill(config);
        skillManager.registerSkill(skill);
    }

    private DynamicSkill buildDynamicSkill(SkillConfig config) {
        return new DynamicSkill(
                config.getName(),
                config.getDescription(),
                Map.of(
                        "apiUrl", config.getApiUrl() != null ? config.getApiUrl() : "",
                        "apiMethod", config.getApiMethod() != null ? config.getApiMethod() : "POST",
                        "apiHeaders", config.getApiHeaders() != null ? config.getApiHeaders() : "",
                        "promptTemplate", config.getPromptTemplate() != null ? config.getPromptTemplate() : "",
                        "responseTemplate", config.getResponseTemplate() != null ? config.getResponseTemplate() : "",
                        "keywords", config.getKeywords() != null ? config.getKeywords() : ""));
    }
}
