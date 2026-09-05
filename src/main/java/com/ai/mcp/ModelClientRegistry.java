package com.ai.mcp;

import com.ai.model.ModelConfig;
import com.ai.security.SecurityContextHelper;
import com.ai.service.ModelConfigService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型客户端注册中心：统一管理 LangChain4j 同步与流式客户端的构建、缓存与失效。
 *
 * <p>模型调用统一走 LangChain4j（同步 {@link ChatModel}、流式
 * {@link StreamingChatModel}）；未指定 modelId 时按"租户数据库默认模型 →
 * yml 默认配置"的顺序解析，保证后台配置的默认模型真正生效。</p>
 *
 * @author data-agent
 */
@Component
public class ModelClientRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelClientRegistry.class);
    private static final int DEFAULT_MODEL_TIMEOUT_SECONDS = 60;
    private static final String DEFAULT_MODEL_KEY = "default";

    private final ChatModel defaultModel;
    private final StreamingChatModel defaultStreamingModel;
    private final ModelConfigService modelConfigService;
    private final SecurityContextHelper securityContextHelper;
    private final ModelEndpointResolver endpointResolver;
    private final ModelClientFactory clientFactory;
    private final Map<String, ChatModel> modelCache = new ConcurrentHashMap<>();
    private final Map<String, ChatModel> jsonModelCache = new ConcurrentHashMap<>();
    private final Map<String, StreamingChatModel> streamingModelCache = new ConcurrentHashMap<>();

    public ModelClientRegistry(ChatModel defaultModel,
            StreamingChatModel defaultStreamingModel,
            ModelConfigService modelConfigService,
            SecurityContextHelper securityContextHelper,
            ModelEndpointResolver endpointResolver,
            ModelClientFactory clientFactory) {
        this.defaultModel = defaultModel;
        this.defaultStreamingModel = defaultStreamingModel;
        this.modelConfigService = modelConfigService;
        this.securityContextHelper = securityContextHelper;
        this.endpointResolver = endpointResolver;
        this.clientFactory = clientFactory;
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
    public ChatModel getChatModel(String modelId) {
        ModelConfig config = resolveEffectiveConfig(modelId);
        if (config == null) {
            return defaultModel;
        }
        // 缓存键带租户，防止其他租户凭 modelId 直接命中缓存、复用他人 API Key 构建的客户端
        return modelCache.computeIfAbsent(tenantCacheKey(config.getModelId()),
                key -> buildChatModel(config, false));
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
    public ChatModel getJsonChatModel(String modelId) {
        ModelConfig config = resolveEffectiveConfig(modelId);
        if (config == null) {
            return defaultModel;
        }
        return jsonModelCache.computeIfAbsent(tenantCacheKey(config.getModelId()),
                key -> buildChatModel(config, true));
    }

    /**
     * 获取流式聊天模型客户端。
     *
     * @param modelId 模型编号，空值表示默认模型
     * @return 流式聊天模型
     */
    public StreamingChatModel getStreamingChatModel(String modelId) {
        ModelConfig config = resolveEffectiveConfig(modelId);
        if (config == null) {
            return defaultStreamingModel;
        }
        return streamingModelCache.computeIfAbsent(tenantCacheKey(config.getModelId()),
                key -> buildStreamingChatModel(config));
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
            throw new IllegalArgumentException("模型不存在、无权限或已停用: modelId=" + modelId);
        }
        return config;
    }

    private ChatModel buildChatModel(ModelConfig config, boolean jsonMode) {
        try {
            ResolvedModelEndpoint endpoint = endpointResolver.resolve(config);
            LOGGER.info("Building chat model: {} | provider={} | baseUrl={} | modelName={} | jsonMode={}",
                    config.getModelId(), endpoint.providerKey(), endpoint.baseUrl(), endpoint.modelName(), jsonMode);
            return clientFactory.createChatModel(endpoint, config.getTemperature(), config.getMaxTokens(),
                    Duration.ofSeconds(DEFAULT_MODEL_TIMEOUT_SECONDS), jsonMode);
        } catch (Exception e) {
            throw new IllegalStateException("模型客户端构建失败: modelId=" + config.getModelId(), e);
        }
    }

    private StreamingChatModel buildStreamingChatModel(ModelConfig config) {
        try {
            ResolvedModelEndpoint endpoint = endpointResolver.resolve(config);
            LOGGER.info("Building streaming chat model: {} | provider={} | baseUrl={} | modelName={}",
                    config.getModelId(), endpoint.providerKey(), endpoint.baseUrl(), endpoint.modelName());
            return clientFactory.createStreamingChatModel(endpoint, config.getTemperature(), config.getMaxTokens(),
                    Duration.ofSeconds(DEFAULT_MODEL_TIMEOUT_SECONDS));
        } catch (Exception e) {
            throw new IllegalStateException("流式模型客户端构建失败: modelId=" + config.getModelId(), e);
        }
    }

    private String tenantCacheKey(String modelId) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        return (tenantId == null ? "-" : tenantId) + ":" + modelId;
    }

}
