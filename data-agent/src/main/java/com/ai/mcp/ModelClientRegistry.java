package com.ai.mcp;

import com.ai.model.ModelConfig;
import com.ai.service.ModelConfigService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for model clients and model invocation configuration.
 *
 * @author data-agent
 */
@Component
public class ModelClientRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelClientRegistry.class);
    private static final int DEFAULT_MODEL_TIMEOUT_SECONDS = 30;
    private static final int API_KEY_VISIBLE_PREFIX_LENGTH = 4;
    private static final int API_KEY_VISIBLE_SUFFIX_LENGTH = 4;
    private static final int API_KEY_MASK_THRESHOLD = 8;
    private static final String DEFAULT_MODEL_KEY = "default";
    private static final String PROVIDER_OPENAI = "openai";
    private static final String PROVIDER_QWEN = "qwen";
    private static final String PROVIDER_DEEPSEEK = "deepseek";
    private static final String PROVIDER_ZHIPU = "zhipu";
    private static final String PROVIDER_MOONSHOT = "moonshot";
    private static final String PROVIDER_KIMI = "kimi";
    private static final String KIMI_DEFAULT_BASE_URL = "https://api.moonshot.cn/v1";
    private static final String KIMI_DEFAULT_MODEL_NAME = "kimi-k2.6";
    private static final String NULL_OR_EMPTY = "null/empty";
    private static final String MASK_SEGMENT = "****";

    private final ChatLanguageModel defaultModel;
    private final ModelConfigService modelConfigService;
    private final Map<String, ChatLanguageModel> modelCache = new ConcurrentHashMap<>();

    public ModelClientRegistry(ChatLanguageModel defaultModel, ModelConfigService modelConfigService) {
        this.defaultModel = defaultModel;
        this.modelConfigService = modelConfigService;
    }

    /**
     * Gets stable model key for monitoring and circuit breaker.
     *
     * @param modelId model id
     * @return model key
     */
    public String modelKey(String modelId) {
        return isDefaultModel(modelId) ? DEFAULT_MODEL_KEY : modelId;
    }

    /**
     * Checks whether the requested model is the application default model.
     *
     * @param modelId model id
     * @return true when default model should be used
     */
    public boolean isDefaultModel(String modelId) {
        return !StringUtils.hasText(modelId);
    }

    /**
     * Resolves an OpenAI-compatible HTTP model config.
     *
     * @param modelId model id
     * @param defaultConfig default model config
     * @return resolved config
     */
    public ModelConfig resolveHttpConfig(String modelId, ModelConfig defaultConfig) {
        if (isDefaultModel(modelId)) {
            return defaultConfig;
        }
        ModelConfig config = modelConfigService.getModel(modelId);
        if (config == null || !config.isEnabled()) {
            LOGGER.warn("Model config not found or disabled: {}, falling back to default config", modelId);
            return defaultConfig;
        }
        return config;
    }

    /**
     * Gets a cached LangChain4j chat model.
     *
     * @param modelId model id
     * @return chat model
     */
    public ChatLanguageModel getChatModel(String modelId) {
        if (isDefaultModel(modelId)) {
            return defaultModel;
        }
        return modelCache.computeIfAbsent(modelId, this::buildModelOrDefault);
    }

    /**
     * Removes one cached model client.
     *
     * @param modelId model id
     */
    public void refreshModelCache(String modelId) {
        modelCache.remove(modelId);
    }

    public boolean isHttpOnlyModel(ModelConfig config) {
        return config != null && isKimiProvider(config.getProvider());
    }

    private ChatLanguageModel buildModelOrDefault(String modelId) {
        ModelConfig config = modelConfigService.getModel(modelId);
        if (config == null || !config.isEnabled()) {
            LOGGER.warn("Model not found or disabled: {}, falling back to default", modelId);
            return defaultModel;
        }
        try {
            ChatLanguageModel model = buildModel(config);
            if (model != null) {
                return model;
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to build model: {}, falling back to default", modelId, e);
        }
        return defaultModel;
    }

    private ChatLanguageModel buildModel(ModelConfig config) {
        String provider = config.getProvider();
        if (!StringUtils.hasText(provider)) {
            LOGGER.warn("Model provider not specified: {}", config.getModelId());
            return null;
        }

        switch (provider.toLowerCase(Locale.ROOT)) {
            case PROVIDER_OPENAI:
            case PROVIDER_QWEN:
            case PROVIDER_DEEPSEEK:
            case PROVIDER_ZHIPU:
            case PROVIDER_MOONSHOT:
            case PROVIDER_KIMI:
                return buildOpenAiCompatibleModel(config, provider);
            default:
                LOGGER.warn("Unsupported model provider: {}, model: {}", provider, config.getModelId());
                return null;
        }
    }

    private ChatLanguageModel buildOpenAiCompatibleModel(ModelConfig config, String provider) {
        String apiKey = config.getApiKey();
        String baseUrl = resolveBaseUrl(config, provider);
        String modelName = resolveModelName(config, provider);
        LOGGER.info("Building model: {} | provider={} | baseUrl={} | modelName={} | apiKeyPrefix={}",
                config.getModelId(), provider, baseUrl, modelName, maskApiKey(apiKey));
        var builder = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .timeout(Duration.ofSeconds(DEFAULT_MODEL_TIMEOUT_SECONDS));
        if (config.getTemperature() != null) {
            builder.temperature(config.getTemperature());
        }
        if (config.getMaxTokens() != null) {
            builder.maxTokens(config.getMaxTokens());
        }
        return builder.build();
    }

    private String resolveBaseUrl(ModelConfig config, String provider) {
        if (StringUtils.hasText(config.getBaseUrl())) {
            return config.getBaseUrl();
        }
        if (isKimiProvider(provider)) {
            return KIMI_DEFAULT_BASE_URL;
        }
        return config.getBaseUrl();
    }

    private String resolveModelName(ModelConfig config, String provider) {
        if (StringUtils.hasText(config.getModelName())) {
            return config.getModelName();
        }
        if (isKimiProvider(provider)) {
            return KIMI_DEFAULT_MODEL_NAME;
        }
        return config.getModelName();
    }

    private boolean isKimiProvider(String provider) {
        String normalizedProvider = provider == null ? "" : provider.toLowerCase(Locale.ROOT);
        return PROVIDER_KIMI.equals(normalizedProvider) || PROVIDER_MOONSHOT.equals(normalizedProvider);
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= API_KEY_MASK_THRESHOLD) {
            return NULL_OR_EMPTY;
        }
        return apiKey.substring(0, API_KEY_VISIBLE_PREFIX_LENGTH)
                + MASK_SEGMENT
                + apiKey.substring(apiKey.length() - API_KEY_VISIBLE_SUFFIX_LENGTH);
    }
}
