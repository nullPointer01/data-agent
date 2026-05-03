package com.ai.service;

import com.ai.mcp.MCPModelService;
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

@Service
public class SkillGenerator {

    private static final Logger log = LoggerFactory.getLogger(SkillGenerator.class);

    private final MCPModelService mcpModelService;
    private final SkillConfigRepository skillConfigRepository;
    private final SkillServiceImpl skillService;
    private final SecurityContextHelper securityContextHelper;
    private final SessionManager sessionManager;

    public SkillGenerator(MCPModelService mcpModelService,
                          SkillConfigRepository skillConfigRepository,
                          SkillServiceImpl skillService,
                          SecurityContextHelper securityContextHelper,
                          SessionManager sessionManager) {
        this.mcpModelService = mcpModelService;
        this.skillConfigRepository = skillConfigRepository;
        this.skillService = skillService;
        this.securityContextHelper = securityContextHelper;
        this.sessionManager = sessionManager;
    }

    @Transactional
    public Map<String, Object> generateFromData(String data, String dataDescription) {
        String prompt = buildDataGenerationPrompt(data, dataDescription);
        String aiResponse = mcpModelService.callModel(prompt);

        SkillConfig config = parseAiResponseToSkillConfig(aiResponse, dataDescription);
        if (config == null) {
            return Map.of("success", false, "message", "AI无法生成有效的Skill配置");
        }

        config.setSource("data");
        config.setAutoAttach("keyword");
        config.setTenantId(securityContextHelper.getCurrentTenantId());
        config.setCreatedBy(securityContextHelper.getCurrentUserId());
        skillConfigRepository.save(config);
        skillService.registerToSkillManager(config);

        log.info("Auto-generated skill from data: {}", config.getName());
        return Map.of(
                "success", true,
                "skillId", config.getSkillId(),
                "name", config.getName(),
                "description", config.getDescription(),
                "promptTemplate", config.getPromptTemplate(),
                "keywords", config.getKeywords(),
                "steps", config.getSteps() != null ? config.getSteps() : "",
                "autoAttach", config.getAutoAttach() != null ? config.getAutoAttach() : "",
                "message", "Skill自动生成成功");
    }

    @Transactional
    public Map<String, Object> generateFromConversation(String sessionId) {
        ConversationSession session = sessionManager.getSession(sessionId);
        if (session == null || session.getHistory().isEmpty()) {
            return Map.of("success", false, "message", "会话不存在或没有对话历史");
        }

        StringBuilder conversationText = new StringBuilder();
        List<ConversationSession.Message> history = session.getHistory();
        int start = Math.max(0, history.size() - 10);
        for (int i = start; i < history.size(); i++) {
            ConversationSession.Message msg = history.get(i);
            String role = msg.getRole().equals("user") ? "用户" : "助手";
            String content = msg.getContent();
            if (content.length() > 300) content = content.substring(0, 300) + "...";
            conversationText.append(role).append(": ").append(content).append("\n\n");
        }

        String prompt = buildConversationGenerationPrompt(conversationText.toString());
        String aiResponse = mcpModelService.callModel(prompt);

        SkillConfig config = parseAiResponseToSkillConfig(aiResponse, "对话生成Skill");
        if (config == null) {
            return Map.of("success", false, "message", "AI无法从对话中提取有效的Skill配置");
        }

        config.setSource("conversation");
        config.setAutoAttach("keyword");
        config.setTenantId(securityContextHelper.getCurrentTenantId());
        config.setCreatedBy(securityContextHelper.getCurrentUserId());
        skillConfigRepository.save(config);
        skillService.registerToSkillManager(config);

        log.info("Generated skill from conversation: {}", config.getName());
        return Map.of(
                "success", true,
                "skillId", config.getSkillId(),
                "name", config.getName(),
                "description", config.getDescription(),
                "promptTemplate", config.getPromptTemplate(),
                "keywords", config.getKeywords(),
                "steps", config.getSteps() != null ? config.getSteps() : "",
                "message", "从对话生成Skill成功");
    }

    @Transactional
    public Map<String, Object> recordFeedback(String skillId, boolean positive) {
        var opt = skillConfigRepository.findById(skillId);
        if (opt.isEmpty()) {
            return Map.of("success", false, "message", "Skill不存在");
        }

        SkillConfig config = opt.get();
        config.setFeedbackCount(config.getFeedbackCount() + 1);
        if (positive) {
            config.setPositiveCount(config.getPositiveCount() + 1);
        }
        skillConfigRepository.save(config);

        if (config.getFeedbackCount() >= 5 && config.getPositiveCount() < config.getFeedbackCount() * 0.3) {
            log.info("Skill {} has low positive rate, triggering auto-optimization", config.getName());
            optimizeSkill(config);
        }

        return Map.of("success", true, "message", positive ? "感谢正面反馈" : "已记录反馈，Skill将自动优化");
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

            String aiResponse = mcpModelService.callModel(prompt);
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
                log.info("Skill auto-optimized: {}", config.getName());
            }
        } catch (Exception e) {
            log.warn("Failed to auto-optimize skill: {}", e.getMessage());
        }
    }

    private String buildDataGenerationPrompt(String data, String dataDescription) {
        String truncatedData = data;
        if (data != null && data.length() > 2000) {
            truncatedData = data.substring(0, 2000) + "\n...[数据已截断]";
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
    @Transactional
    public void onFileUploaded(FileUploadedEvent event) {
        try {
            String description = "上传文件: " + event.getFilename();
            log.info("Auto-generating skill for uploaded file: {}", event.getFilename());
            generateFromData(event.getContent(), description);
        } catch (Exception e) {
            log.warn("Failed to auto-generate skill for file: {} - {}", event.getFilename(), e.getMessage());
        }
    }

    private SkillConfig parseAiResponseToSkillConfig(String aiResponse, String dataDescription) {
        try {
            String json = aiResponse.trim();
            log.info("AI response for skill generation (first 200 chars): {}", json.substring(0, Math.min(200, json.length())));
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> parsed = mapper.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});

            SkillConfig config = new SkillConfig();
            config.setName(String.valueOf(parsed.getOrDefault("name", dataDescription != null ? dataDescription : "自动生成Skill")));
            config.setDescription(String.valueOf(parsed.getOrDefault("description", "")));
            config.setPromptTemplate(String.valueOf(parsed.getOrDefault("promptTemplate", "简洁回答: {{query}}")));
            config.setKeywords(String.valueOf(parsed.getOrDefault("keywords", "")));

            Object stepsObj = parsed.get("steps");
            if (stepsObj != null) {
                config.setSteps(mapper.writeValueAsString(stepsObj));
            } else {
                config.setSteps("");
            }
            config.setVersion("1.0");
            config.setApiUrl("");
            config.setApiMethod("POST");
            config.setEnabled(true);
            config.setDefault(false);

            return config;
        } catch (Exception e) {
            log.warn("Failed to parse AI response as SkillConfig: {}", e.getMessage());
            log.debug("AI response was: {}", aiResponse);
            return null;
        }
    }
}
