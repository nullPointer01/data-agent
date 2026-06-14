package com.ai.mcp;

import com.ai.model.ModelConfig;
import com.ai.security.SecurityContextHelper;
import com.ai.service.ModelConfigService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型客户端注册中心：统一管理 LangChain4j 同步与流式客户端的构建、缓存与失效。
 *
 * <p>模型调用统一走 LangChain4j（同步 {@link ChatLanguageModel}、流式
 * {@link StreamingChatLanguageModel}）；未指定 modelId 时按"租户数据库默认模型 →
 * yml 默认配置"的顺序解析，保证后台配置的默认模型真正生效。</p>
 *
 * @author data-agent
 */
@Component
public class ModelClientRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelClientRegistry.class);
    private static final int DEFAULT_MODEL_TIMEOUT_SECONDS = 60;
    private static final int API_KEY_VISIBLE_PREFIX_LENGTH = 4;
    private static final int API_KEY_VISIBLE_SUFFIX_LENGTH = 4;
    private static final int API_KEY_MASK_THRESHOLD = 8;
    private static final String DEFAULT_MODEL_KEY = "default";
    private static final String NULL_OR_EMPTY = "null/empty";
    private static final String MASK_SEGMENT = "****";

    private static final String RESPONSE_FORMAT_JSON = "json_object";

    private final ChatLanguageModel defaultModel;
    private final StreamingChatLanguageModel defaultStreamingModel;
    private final ModelConfigService modelConfigService;
    private final SecurityContextHelper securityContextHelper;
    private final Map<String, ChatLanguageModel> modelCache = new ConcurrentHashMap<>();
    private final Map<String, ChatLanguageModel> jsonModelCache = new ConcurrentHashMap<>();
    private final Map<String, StreamingChatLanguageModel> streamingModelCache = new ConcurrentHashMap<>();

    public ModelClientRegistry(ChatLanguageModel defaultModel,
            StreamingChatLanguageModel defaultStreamingModel,
            ModelConfigService modelConfigService,
            SecurityContextHelper securityContextHelper) {
        this.defaultModel = defaultModel;
        this.defaultStreamingModel = defaultStreamingModel;
        this.modelConfigService = modelConfigService;
        this.securityContextHelper = securityContextHelper;
    }

    /**
     * 获取用于监控和熔断器的稳定模型键。
     *
     * @param modelId 模型编号
     * @return 模型键
     */
    public String modelKey(String modelId) {
        return isDefaultModel(modelId) ? DEFAULT_MODEL_KEY : modelId;
    }

    /**
     * 检查请求的模型是否为应用默认模型。
     *
     * @param modelId 模型编号
     * @return 当应使用默认模型时返回 true
     */
    public boolean isDefaultModel(String modelId) {
        return !StringUtils.hasText(modelId);
    }

    /**
     * 获取同步聊天模型客户端。
     *
     * @param modelId 模型编号，空值表示默认模型
     * @return 聊天模型
     */
    public ChatLanguageModel getChatModel(String modelId) {
        ModelConfig config = resolveEffectiveConfig(modelId);
        if (config == null) {
            return defaultModel;
        }
        // 缓存键带租户，防止其他租户凭 modelId 直接命中缓存、复用他人 API Key 构建的客户端
        return modelCache.computeIfAbsent(tenantCacheKey(config.getModelId()),
                key -> buildChatModelOrDefault(config, false));
    }

    /**
     * 获取 JSON 强制输出模式的聊天模型客户端（response_format=json_object）。
     *
     * <p>用于任务规划、意图分析等结构化输出场景，由厂商端约束输出合法 JSON。
     * 注意：调用方的提示词中必须包含 "JSON" 字样（OpenAI 协议要求）。</p>
     *
     * @param modelId 模型编号，空值表示默认模型
     * @return JSON 模式聊天模型；数据库无可用配置时回退普通默认客户端
     */
    public ChatLanguageModel getJsonChatModel(String modelId) {
        ModelConfig config = resolveEffectiveConfig(modelId);
        if (config == null) {
            return defaultModel;
        }
        return jsonModelCache.computeIfAbsent(tenantCacheKey(config.getModelId()),
                key -> buildChatModelOrDefault(config, true));
    }

    /**
     * 获取流式聊天模型客户端。
     *
     * @param modelId 模型编号，空值表示默认模型
     * @return 流式聊天模型
     */
    public StreamingChatLanguageModel getStreamingChatModel(String modelId) {
        ModelConfig config = resolveEffectiveConfig(modelId);
        if (config == null) {
            return defaultStreamingModel;
        }
        return streamingModelCache.computeIfAbsent(tenantCacheKey(config.getModelId()),
                key -> buildStreamingChatModelOrDefault(config));
    }

    /**
     * 移除一个模型在所有租户下的缓存客户端（同步与流式）。
     *
     * @param modelId 模型编号
     */
    public void refreshModelCache(String modelId) {
        // 缓存键为 tenantId:modelId，按后缀清理该模型在所有租户下的条目
        modelCache.keySet().removeIf(key -> key.endsWith(":" + modelId));
        jsonModelCache.keySet().removeIf(key -> key.endsWith(":" + modelId));
        streamingModelCache.keySet().removeIf(key -> key.endsWith(":" + modelId));
    }

    /**
     * 解析生效的模型配置：modelId 为空时取租户数据库默认模型，非空时按编号查询。
     *
     * @param modelId 模型编号
     * @return 生效配置；数据库无可用配置时返回 null（调用方回退 yml 默认）
     */
    private ModelConfig resolveEffectiveConfig(String modelId) {
        if (isDefaultModel(modelId)) {
            return modelConfigService.resolveTenantDefaultModel();
        }
        ModelConfig config = modelConfigService.getModel(modelId);
        if (config == null || !config.isEnabled()) {
            return null;
        }
        return config;
    }

    private ChatLanguageModel buildChatModelOrDefault(ModelConfig config, boolean jsonMode) {
        ResolvedEndpoint endpoint = resolveEndpoint(config);
        if (endpoint == null) {
            return defaultModel;
        }
        LOGGER.info("Building chat model: {} | provider={} | baseUrl={} | modelName={} | jsonMode={} | apiKeyPrefix={}",
                config.getModelId(), config.getProvider(), endpoint.baseUrl(), endpoint.modelName(), jsonMode,
                maskApiKey(config.getApiKey()));
        try {
            var builder = OpenAiChatModel.builder()
                    .apiKey(config.getApiKey())
                    .modelName(endpoint.modelName())
                    .baseUrl(endpoint.baseUrl())
                    // 重试统一由 ModelRetryExecutor 管理，关闭内置重试避免双层放大
                    .maxRetries(1)
                    .timeout(Duration.ofSeconds(DEFAULT_MODEL_TIMEOUT_SECONDS));
            if (jsonMode) {
                // 厂商端约束输出合法 JSON，结构化输出场景的解析失败率趋零
                builder.responseFormat(RESPONSE_FORMAT_JSON);
            }
            if (config.getTemperature() != null) {
                builder.temperature(config.getTemperature());
            }
            if (config.getMaxTokens() != null) {
                builder.maxTokens(config.getMaxTokens());
            }
            return builder.build();
        } catch (Exception e) {
            LOGGER.warn("Failed to build chat model: {}, falling back to default", config.getModelId(), e);
            return defaultModel;
        }
    }

    private StreamingChatLanguageModel buildStreamingChatModelOrDefault(ModelConfig config) {
        ResolvedEndpoint endpoint = resolveEndpoint(config);
        if (endpoint == null) {
            return defaultStreamingModel;
        }
        LOGGER.info("Building streaming chat model: {} | provider={} | baseUrl={} | modelName={}",
                config.getModelId(), config.getProvider(), endpoint.baseUrl(), endpoint.modelName());
        try {
            var builder = OpenAiStreamingChatModel.builder()
                    .apiKey(config.getApiKey())
                    .modelName(endpoint.modelName())
                    .baseUrl(endpoint.baseUrl())
                    .timeout(Duration.ofSeconds(DEFAULT_MODEL_TIMEOUT_SECONDS));
            if (config.getTemperature() != null) {
                builder.temperature(config.getTemperature());
            }
            if (config.getMaxTokens() != null) {
                builder.maxTokens(config.getMaxTokens());
            }
            return builder.build();
        } catch (Exception e) {
            LOGGER.warn("Failed to build streaming chat model: {}, falling back to default",
                    config.getModelId(), e);
            return defaultStreamingModel;
        }
    }

    /**
     * 解析模型的最终接入端点：已登记厂商自动补默认值，未登记厂商填了 Base URL 同样可用。
     */
    private ResolvedEndpoint resolveEndpoint(ModelConfig config) {
        Optional<ModelProviderCatalog> catalogEntry = ModelProviderCatalog.resolve(config.getProvider());
        String baseUrl = StringUtils.hasText(config.getBaseUrl())
                ? config.getBaseUrl()
                : catalogEntry.map(ModelProviderCatalog::defaultBaseUrl).orElse(null);
        if (!StringUtils.hasText(baseUrl)) {
            LOGGER.warn("Model baseUrl missing and provider has no default: provider={}, model={}",
                    config.getProvider(), config.getModelId());
            return null;
        }
        String modelName = StringUtils.hasText(config.getModelName())
                ? config.getModelName()
                : catalogEntry.map(ModelProviderCatalog::defaultModelName).orElse(null);
        if (!StringUtils.hasText(modelName)) {
            LOGGER.warn("Model name missing and provider has no default: provider={}, model={}",
                    config.getProvider(), config.getModelId());
            return null;
        }
        return new ResolvedEndpoint(baseUrl, modelName);
    }

    private String tenantCacheKey(String modelId) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        return (tenantId == null ? "-" : tenantId) + ":" + modelId;
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= API_KEY_MASK_THRESHOLD) {
            return NULL_OR_EMPTY;
        }
        return apiKey.substring(0, API_KEY_VISIBLE_PREFIX_LENGTH)
                + MASK_SEGMENT
                + apiKey.substring(apiKey.length() - API_KEY_VISIBLE_SUFFIX_LENGTH);
    }

    /** 解析后的模型接入端点。 */
    private record ResolvedEndpoint(String baseUrl, String modelName) {
    }
}
