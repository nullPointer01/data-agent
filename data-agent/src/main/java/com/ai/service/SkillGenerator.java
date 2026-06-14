package com.ai.service;

import com.ai.service.file.FileUploadedEvent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ai.mcp.McpModelService;
import com.ai.model.ConversationSession;
import com.ai.model.SkillConfig;
import com.ai.repository.SkillConfigRepository;
import com.ai.security.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 通过模型服务生成和优化可复用技能。
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
    private static final String KEY_KEYWORDS = "keywords";
    private static final String KEY_STEPS = "steps";
    private static final String KEY_AUTO_ATTACH = "autoAttach";
    private static final String SOURCE_DATA = "data";
    private static final String SOURCE_CONVERSATION = "conversation";
    private static final String AUTO_ATTACH_KEYWORD = "keyword";
    private static final String DEFAULT_VERSION = "1.0";
    private static final String DEFAULT_API_METHOD = "POST";
    private static final String DEFAULT_PROMPT_TEMPLATE = "简洁回答: {{query}}";
    private static final String DEFAULT_SKILL_NAME = "自动生成Skill";
    private static final String EMPTY_VALUE = "";
    private static final String ROLE_USER = "user";
    private static final String MARKDOWN_FENCE = "```";
    private static final int MAX_CONVERSATION_MESSAGES = 10;
    private static final int MAX_MESSAGE_LENGTH = 300;
    private static final int MAX_DATA_LENGTH = 2000;
    private static final int LOG_PREVIEW_LENGTH = 200;
    private static final int MIN_FEEDBACK_COUNT_FOR_OPTIMIZE = 5;
    private static final double LOW_POSITIVE_RATE_THRESHOLD = 0.3D;

    private final McpModelService mcpModelService;
    private final SkillConfigRepository skillConfigRepository;
    private final SkillServiceImpl skillService;
    private final SecurityContextHelper securityContextHelper;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public SkillGenerator(McpModelService mcpModelService,
                          SkillConfigRepository skillConfigRepository,
                          SkillServiceImpl skillService,
                          SecurityContextHelper securityContextHelper,
                          SessionManager sessionManager,
                          ObjectMapper objectMapper) {
        this.mcpModelService = mcpModelService;
        this.skillConfigRepository = skillConfigRepository;
        this.skillService = skillService;
        this.securityContextHelper = securityContextHelper;
        this.sessionManager = sessionManager;
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
        return generatedResponse(config, "Skill自动生成成功", true);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generateFromConversation(String sessionId) {
        ConversationSession session = sessionManager.getSession(sessionId);
        if (session == null || session.getHistory().isEmpty()) {
            return failure("会话不存在或没有对话历史");
        }

        StringBuilder conversationText = new StringBuilder();
        List<ConversationSession.Message> history = session.getHistory();
        int start = Math.max(0, history.size() - MAX_CONVERSATION_MESSAGES);
        for (int i = start; i < history.size(); i++) {
            ConversationSession.Message message = history.get(i);
            String role = ROLE_USER.equals(message.getRole()) ? "用户" : "助手";
            String content = truncate(message.getContent(), MAX_MESSAGE_LENGTH);
            conversationText.append(role).append(": ").append(content).append("\n\n");
        }

        String prompt = buildConversationGenerationPrompt(conversationText.toString());
        String aiResponse = mcpModelService.callModelJson(prompt, null);

        SkillConfig config = parseAiResponseToSkillConfig(aiResponse, "对话生成Skill");
        if (config == null) {
            return failure("AI无法从对话中提取有效的Skill配置");
        }

        persistGeneratedSkill(config, SOURCE_CONVERSATION);

        LOGGER.info("从对话生成技能: {}", config.getName());
        return generatedResponse(config, "从对话生成Skill成功", false);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> recordFeedback(String skillId, boolean positive) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        Optional<SkillConfig> skillOptional = skillConfigRepository.findBySkillIdAndTenantId(skillId, tenantId);
        if (skillOptional.isEmpty()) {
            return failure("Skill不存在或无权限");
        }

        SkillConfig config = skillOptional.get();
        int feedbackCount = nullToZero(config.getFeedbackCount()) + 1;
        int positiveCount = nullToZero(config.getPositiveCount());
        config.setFeedbackCount(feedbackCount);
        if (positive) {
            positiveCount++;
            config.setPositiveCount(positiveCount);
        }
        skillConfigRepository.save(config);

        if (shouldOptimize(feedbackCount, positiveCount)) {
            LOGGER.info("技能 {} 正向反馈率偏低，触发自动优化", config.getName());
            optimizeSkill(config);
        }

        return Map.of(KEY_SUCCESS, true, KEY_MESSAGE, positive ? "感谢正面反馈" : "已记录反馈，Skill将自动优化");
    }

    private void optimizeSkill(SkillConfig config) {
        try {
            String prompt = String.format("""
                    以下Skill配置收到较多负面反馈，请优化它。

                    当前配置:
                    - 名称: %s
                    - 描述: %s
                    - 模板: %s
                    - 关键词: %s
                    - 步骤: %s

                    正面反馈率: %d/%d

                    请优化后按JSON格式输出:
                    {
                      "name": "优化后的名称",
                      "description": "优化后的描述",
                      "promptTemplate": "优化后的模板(更精准、更简洁)",
                      "keywords": "优化后的关键词",
                      "steps": "优化后的步骤(JSON数组字符串)"
                    }
                    只输出JSON。
                    """,
                    config.getName(), config.getDescription(), config.getPromptTemplate(),
                    config.getKeywords(), config.getSteps() != null ? config.getSteps() : "",
                    config.getPositiveCount(), config.getFeedbackCount());

            String aiResponse = mcpModelService.callModelJson(prompt, null);
            SkillConfig optimized = parseAiResponseToSkillConfig(aiResponse, config.getName());
            if (optimized != null) {
                config.setName(optimized.getName());
                config.setDescription(optimized.getDescription());
                config.setPromptTemplate(optimized.getPromptTemplate());
                config.setKeywords(optimized.getKeywords());
                config.setSteps(optimized.getSteps());
                config.setFeedbackCount(0);
                config.setPositiveCount(0);
                skillConfigRepository.save(config);
                skillService.registerToSkillManager(config);
                LOGGER.info("技能自动优化完成: {}", config.getName());
            }
        } catch (Exception e) {
            LOGGER.warn("技能自动优化失败: {}", e.getMessage());
        }
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
                  "keywords": "关键词1,关键词2,关键词3(5-10个，逗号分隔)",
                  "steps": "[\\"步骤1:理解用户问题\\",\\"步骤2:分析相关数据\\",\\"步骤3:生成结论\\"]"
                }

                要求:
                1. name要体现数据领域(如"销售分析"、"财务审计"、"用户画像")
                2. promptTemplate必须包含{{query}}，可选包含{{data}}
                3. promptTemplate要约束输出长度和格式，减少token消耗
                4. keywords要覆盖用户可能问的各种表述
                5. steps是JSON数组字符串，描述分析工作流的3-5个步骤
                6. 只输出JSON，不要输出markdown代码块标记
                """.formatted(
                dataDescription != null ? dataDescription : "未提供描述",
                truncatedData != null ? truncatedData : "无数据");
    }

    private String buildConversationGenerationPrompt(String conversation) {
        return """
                你是一个Skill生成器。分析以下对话历史，提取出可复用的分析模式，生成一个Skill配置。

                对话历史:
                %s

                请严格按照以下JSON格式输出，不要输出任何其他内容:
                {
                  "name": "Skill名称(简洁，2-6字，体现分析能力)",
                  "description": "Skill描述(说明能做什么分析，50字以内)",
                  "promptTemplate": "提示词模板(用{{query}}代表用户问题，用{{data}}代表数据。必须包含:1.有数据时分析数据 2.无数据时提示用户上传 3.绝不编造数据 4.简洁专业)",
                  "keywords": "关键词1,关键词2,关键词3(5-10个，逗号分隔)",
                  "steps": "[\\"步骤1:xxx\\",\\"步骤2:xxx\\",\\"步骤3:xxx\\"]"
                }

                要求:
                1. 从对话中提取用户反复需要的分析模式
                2. name要体现分析能力(如"趋势分析"、"异常检测"、"对比分析")
                3. promptTemplate要约束输出长度和格式，减少token消耗
                4. keywords要覆盖用户可能问的各种表述
                5. steps是JSON数组字符串，描述分析工作流的3-5个步骤
                6. 只输出JSON，不要输出markdown代码块标记
                """.formatted(conversation);
    }

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onFileUploaded(FileUploadedEvent event) {
        try {
            String description = "上传文件: " + event.getFilename();
            LOGGER.info("为上传文件自动生成技能: {}", event.getFilename());
            generateFromData(event.getContent(), description);
        } catch (Exception e) {
            LOGGER.warn("为文件自动生成技能失败: {} - {}", event.getFilename(), e.getMessage());
        }
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
            config.setKeywords(String.valueOf(parsed.getOrDefault(KEY_KEYWORDS, EMPTY_VALUE)));

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
            config.setDefault(false);

            return config;
        } catch (Exception e) {
            LOGGER.warn("解析 AI 响应为 SkillConfig 失败: {}", e.getMessage());
            LOGGER.debug("AI 响应内容: {}", aiResponse);
            return null;
        }
    }

    private void persistGeneratedSkill(SkillConfig config, String source) {
        config.setSource(source);
        config.setAutoAttach(AUTO_ATTACH_KEYWORD);
        config.setTenantId(securityContextHelper.getCurrentTenantId());
        config.setCreatedBy(securityContextHelper.getCurrentUserId());
        skillConfigRepository.save(config);
        skillService.registerToSkillManager(config);
    }

    private Map<String, Object> generatedResponse(SkillConfig config, String message, boolean includeAutoAttach) {
        if (includeAutoAttach) {
            return Map.of(
                    KEY_SUCCESS, true,
                    KEY_SKILL_ID, config.getSkillId(),
                    KEY_NAME, config.getName(),
                    KEY_DESCRIPTION, config.getDescription(),
                    KEY_PROMPT_TEMPLATE, config.getPromptTemplate(),
                    KEY_KEYWORDS, config.getKeywords(),
                    KEY_STEPS, nullToEmpty(config.getSteps()),
                    KEY_AUTO_ATTACH, nullToEmpty(config.getAutoAttach()),
                    KEY_MESSAGE, message);
        }
        return Map.of(
                KEY_SUCCESS, true,
                KEY_SKILL_ID, config.getSkillId(),
                KEY_NAME, config.getName(),
                KEY_DESCRIPTION, config.getDescription(),
                KEY_PROMPT_TEMPLATE, config.getPromptTemplate(),
                KEY_KEYWORDS, config.getKeywords(),
                KEY_STEPS, nullToEmpty(config.getSteps()),
                KEY_MESSAGE, message);
    }

    private Map<String, Object> failure(String message) {
        return Map.of(KEY_SUCCESS, false, KEY_MESSAGE, message);
    }

    private boolean shouldOptimize(int feedbackCount, int positiveCount) {
        return feedbackCount >= MIN_FEEDBACK_COUNT_FOR_OPTIMIZE
                && positiveCount < feedbackCount * LOW_POSITIVE_RATE_THRESHOLD;
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
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
