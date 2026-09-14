package com.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ai.mcp.McpModelService;
import com.ai.model.SkillConfig;
import com.ai.repository.SkillConfigRepository;
import com.ai.security.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * [正式管理功能] 根据管理员提供的数据样例，通过模型生成并注册可复用 Skill。
 *
 * @author data-agent
 */
@Service
public class SkillGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkillGenerator.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final String KEY_SUCCESS = "success";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_SKILL_ID = "skillId";
    private static final String KEY_NAME = "name";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_PROMPT_TEMPLATE = "promptTemplate";
    private static final String KEY_STEPS = "steps";
    private static final String SOURCE_DATA = "data";
    private static final String DEFAULT_VERSION = "1.0";
    private static final String DEFAULT_API_METHOD = "POST";
    private static final String DEFAULT_PROMPT_TEMPLATE = "简洁回答: {{query}}";
    private static final String DEFAULT_SKILL_NAME = "自动生成Skill";
    private static final String EMPTY_VALUE = "";
    private static final String MARKDOWN_FENCE = "```";
    private static final int MAX_DATA_LENGTH = 2000;
    private static final int LOG_PREVIEW_LENGTH = 200;

    private final McpModelService mcpModelService;
    private final SkillConfigRepository skillConfigRepository;
    private final SkillServiceImpl skillService;
    private final SecurityContextHelper securityContextHelper;
    private final ObjectMapper objectMapper;

    public SkillGenerator(McpModelService mcpModelService,
                          SkillConfigRepository skillConfigRepository,
                          SkillServiceImpl skillService,
                          SecurityContextHelper securityContextHelper,
                          ObjectMapper objectMapper) {
        this.mcpModelService = mcpModelService;
        this.skillConfigRepository = skillConfigRepository;
        this.skillService = skillService;
        this.securityContextHelper = securityContextHelper;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generateFromData(String data, String dataDescription) {
        String prompt = buildDataGenerationPrompt(data, dataDescription);
        String aiResponse = mcpModelService.callModelJson(prompt, null);

        SkillConfig config = parseAiResponseToSkillConfig(aiResponse, dataDescription);
        if (config == null) {
            return failure("AI无法生成有效的Skill配置");
        }

        persistGeneratedSkill(config, SOURCE_DATA);

        LOGGER.info("从数据自动生成技能: {}", config.getName());
        return generatedResponse(config, "Skill自动生成成功");
    }

    private String buildDataGenerationPrompt(String data, String dataDescription) {
        String truncatedData = data;
        if (data != null && data.length() > MAX_DATA_LENGTH) {
            truncatedData = data.substring(0, MAX_DATA_LENGTH) + "\n...[数据已截断]";
        }

        return """
                你是一个Skill生成器。根据提供的数据和描述，生成一个专业的数据分析Skill配置。

                数据描述: %s

                数据样本:
                %s

                请严格按照以下JSON格式输出，不要输出任何其他内容:
                {
                  "name": "Skill名称(简洁，2-6字，体现数据领域)",
                  "description": "Skill描述(说明能做什么分析，50字以内)",
                  "promptTemplate": "提示词模板(用{{query}}代表用户问题，用{{data}}代表数据。必须包含:1.有数据时分析数据 2.无数据时提示用户上传 3.绝不编造数据 4.简洁专业)",
                  "steps": "[\\"步骤1:理解用户问题\\",\\"步骤2:分析相关数据\\",\\"步骤3:生成结论\\"]"
                }

                要求:
                1. name要体现数据领域(如"销售分析"、"财务审计"、"用户画像")
                2. promptTemplate必须包含{{query}}，可选包含{{data}}
                3. promptTemplate要约束输出长度和格式，减少token消耗
                4. steps是JSON数组字符串，描述分析工作流的3-5个步骤
                5. 只输出JSON，不要输出markdown代码块标记
                """.formatted(
                dataDescription != null ? dataDescription : "未提供描述",
                truncatedData != null ? truncatedData : "无数据");
    }

    private SkillConfig parseAiResponseToSkillConfig(String aiResponse, String dataDescription) {
        try {
            String json = aiResponse.trim();
            LOGGER.info("技能生成 AI 响应（前 {} 字符）: {}", LOG_PREVIEW_LENGTH,
                    truncate(json, LOG_PREVIEW_LENGTH));
            if (json.startsWith(MARKDOWN_FENCE)) {
                json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }

            Map<String, Object> parsed = objectMapper.readValue(json, MAP_TYPE_REFERENCE);

            SkillConfig config = new SkillConfig();
            config.setName(String.valueOf(parsed.getOrDefault(KEY_NAME,
                    dataDescription != null ? dataDescription : DEFAULT_SKILL_NAME)));
            config.setDescription(String.valueOf(parsed.getOrDefault(KEY_DESCRIPTION, EMPTY_VALUE)));
            config.setPromptTemplate(String.valueOf(parsed.getOrDefault(KEY_PROMPT_TEMPLATE, DEFAULT_PROMPT_TEMPLATE)));

            Object stepsObj = parsed.get(KEY_STEPS);
            if (stepsObj != null) {
                config.setSteps(objectMapper.writeValueAsString(stepsObj));
            } else {
                config.setSteps(EMPTY_VALUE);
            }
            config.setVersion(DEFAULT_VERSION);
            config.setApiUrl(EMPTY_VALUE);
            config.setApiMethod(DEFAULT_API_METHOD);
            config.setEnabled(true);

            return config;
        } catch (Exception e) {
            LOGGER.warn("解析 AI 响应为 SkillConfig 失败: {}", e.getMessage());
            LOGGER.debug("AI 响应内容: {}", aiResponse);
            return null;
        }
    }

    private void persistGeneratedSkill(SkillConfig config, String source) {
        config.setSource(source);
        config.setTenantId(securityContextHelper.getCurrentTenantId());
        config.setCreatedBy(securityContextHelper.getCurrentUserId());
        skillConfigRepository.save(config);
        skillService.registerToSkillManager(config);
    }

    private Map<String, Object> generatedResponse(SkillConfig config, String message) {
        return Map.of(
                KEY_SUCCESS, true,
                KEY_SKILL_ID, config.getSkillId(),
                KEY_NAME, config.getName(),
                KEY_DESCRIPTION, config.getDescription(),
                KEY_PROMPT_TEMPLATE, config.getPromptTemplate(),
                KEY_STEPS, nullToEmpty(config.getSteps()),
                KEY_MESSAGE, message);
    }

    private Map<String, Object> failure(String message) {
        return Map.of(KEY_SUCCESS, false, KEY_MESSAGE, message);
    }

    private String nullToEmpty(String value) {
        return value == null ? EMPTY_VALUE : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
