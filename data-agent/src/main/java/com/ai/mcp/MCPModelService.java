package com.ai.mcp;

import com.ai.event.ModelConfigChangeEvent;
import com.ai.event.ModelConfigChangeEvent.ChangeType;
import com.ai.model.ModelConfig;
import com.ai.model.SkillConfig;
import com.ai.model.TokenUsage;
import com.ai.repository.SkillConfigRepository;
import com.ai.repository.TokenUsageRepository;
import com.ai.security.SecurityContextHelper;
import com.ai.service.ModelConfigService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MCPModelService {

    private static final Logger log = LoggerFactory.getLogger(MCPModelService.class);

    private final ChatLanguageModel defaultModel;
    private final MCPContextManager contextManager;
    private final TokenMonitor tokenMonitor;
    private final ModelConfigService modelConfigService;
    private final SkillConfigRepository skillConfigRepository;
    private final TokenUsageRepository tokenUsageRepository;
    private final SecurityContextHelper securityContextHelper;
    private final Map<String, ChatLanguageModel> modelCache = new ConcurrentHashMap<>();

    public MCPModelService(ChatLanguageModel defaultModel, MCPContextManager contextManager,
                           TokenMonitor tokenMonitor, ModelConfigService modelConfigService,
                           SkillConfigRepository skillConfigRepository,
                           TokenUsageRepository tokenUsageRepository,
                           SecurityContextHelper securityContextHelper) {
        this.defaultModel = defaultModel;
        this.contextManager = contextManager;
        this.tokenMonitor = tokenMonitor;
        this.modelConfigService = modelConfigService;
        this.skillConfigRepository = skillConfigRepository;
        this.tokenUsageRepository = tokenUsageRepository;
        this.securityContextHelper = securityContextHelper;
    }

    @EventListener
    public void onModelConfigChange(ModelConfigChangeEvent event) {
        String modelId = event.getModelId();
        ChangeType changeType = event.getChangeType();
        if (changeType == ChangeType.UPDATED || changeType == ChangeType.TOGGLED || changeType == ChangeType.DELETED) {
            modelCache.remove(modelId);
            log.info("Model cache refreshed: {} ({})", modelId, changeType);
        }
    }

    private ChatLanguageModel getModel(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return defaultModel;
        }
        return modelCache.computeIfAbsent(modelId, id -> {
            ModelConfig config = modelConfigService.getModel(id);
            if (config == null || !config.isEnabled()) {
                log.warn("Model not found or disabled: {}, falling back to default", id);
                return defaultModel;
            }
            try {
                ChatLanguageModel model = buildModel(config);
                if (model != null) return model;
            } catch (Exception e) {
                log.warn("Failed to build model: {}, falling back to default", id, e);
            }
            return defaultModel;
        });
    }

    private ChatLanguageModel buildModel(ModelConfig config) {
        String provider = config.getProvider();
        if (provider == null) {
            log.warn("Model provider not specified: {}", config.getModelId());
            return null;
        }

        switch (provider.toLowerCase()) {
            case "openai":
            case "qwen":
            case "deepseek":
            case "zhipu":
                var builder = OpenAiChatModel.builder()
                        .apiKey(config.getApiKey())
                        .modelName(config.getModelName())
                        .baseUrl(config.getBaseUrl())
                        .timeout(Duration.ofSeconds(30));
                if (config.getTemperature() != null) builder.temperature(config.getTemperature());
                if (config.getMaxTokens() != null) builder.maxTokens(config.getMaxTokens());
                return builder.build();
            default:
                log.warn("Unsupported model provider: {}, model: {}", provider, config.getModelId());
                return null;
        }
    }

    public void refreshModelCache(String modelId) {
        modelCache.remove(modelId);
    }

    public void clearModelCache() {
        modelCache.clear();
    }

    public String callModelWithContext(String contextId, String prompt) {
        return callModelWithContext(contextId, prompt, null, null);
    }

    public String callModelWithContext(String contextId, String prompt, String modelId) {
        return callModelWithContext(contextId, prompt, modelId, null);
    }

    public String callModelWithContext(String contextId, String prompt, String modelId, String skillId) {
        MCPContext context = contextManager.getContext(contextId);
        if (context == null) {
            throw new IllegalArgumentException("Context not found: " + contextId);
        }

        context.setLastAccessTime(new java.util.Date());
        String enhancedPrompt = buildEnhancedPrompt(context, prompt);
        long inputTokens = tokenMonitor.estimateTokens(enhancedPrompt);

        ChatLanguageModel model = getModel(modelId);
        try {
            String response = model.generate(enhancedPrompt);
            long outputTokens = tokenMonitor.estimateTokens(response);
            long totalTokens = inputTokens + outputTokens;

            tokenMonitor.recordSkillTokenUsage(skillId != null ? skillId : "direct", totalTokens);
            tokenMonitor.recordModelTokenUsage(modelId != null ? modelId : "default", totalTokens);

            recordTokenUsage(modelId, skillId, inputTokens, outputTokens, totalTokens, null);

            return response;
        } catch (Exception e) {
            log.error("Model call failed", e);
            return "模型调用失败: " + e.getMessage();
        }
    }

    public String callModel(String prompt) {
        return callModel(prompt, null);
    }

    public String callModel(String prompt, String modelId) {
        long inputTokens = tokenMonitor.estimateTokens(prompt);
        ChatLanguageModel model = getModel(modelId);
        try {
            String response = model.generate(prompt);
            long outputTokens = tokenMonitor.estimateTokens(response);
            long totalTokens = inputTokens + outputTokens;
            tokenMonitor.recordModelTokenUsage(modelId != null ? modelId : "default", totalTokens);
            recordTokenUsage(modelId, null, inputTokens, outputTokens, totalTokens, null);
            return response;
        } catch (Exception e) {
            log.error("Model call failed", e);
            return "模型调用失败: " + e.getMessage();
        }
    }

    private void recordTokenUsage(String modelId, String skillId, long promptTokens,
                                   long completionTokens, long totalTokens, String sessionId) {
        try {
            TokenUsage usage = new TokenUsage();
            usage.setUserId(securityContextHelper.getCurrentUserId());
            usage.setTenantId(securityContextHelper.getCurrentTenantId());
            usage.setModelId(modelId);
            usage.setSkillId(skillId);
            usage.setPromptTokens(promptTokens);
            usage.setCompletionTokens(completionTokens);
            usage.setTotalTokens(totalTokens);
            usage.setSessionId(sessionId);

            if (modelId != null) {
                ModelConfig config = modelConfigService.getModel(modelId);
                if (config != null) usage.setModelName(config.getName());
            } else {
                usage.setModelName("默认模型");
            }

            if (skillId != null) {
                if ("direct".equals(skillId)) {
                    usage.setSkillName("直接调用");
                } else {
                    String tenantId = securityContextHelper.getCurrentTenantId();
                    var skills = skillConfigRepository.findByTenantIdAndEnabledTrue(tenantId);
                    for (SkillConfig skill : skills) {
                        if (skill.getName().equals(skillId)) {
                            usage.setSkillName(skill.getName());
                            break;
                        }
                    }
                    if (usage.getSkillName() == null) {
                        usage.setSkillName(skillId);
                    }
                }
            } else {
                usage.setSkillName("直接调用");
            }

            tokenUsageRepository.save(usage);
        } catch (Exception e) {
            log.warn("Failed to record token usage", e);
        }
    }

    private String buildEnhancedPrompt(MCPContext context, String prompt) {
        return prompt;
    }
}
