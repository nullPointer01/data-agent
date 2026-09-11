package com.ai.service;

import com.ai.config.CacheNames;
import com.ai.event.ModelConfigChangeEvent;
import com.ai.mcp.ModelHttpClient;
import com.ai.mcp.ModelEndpointResolver;
import com.ai.mcp.ResolvedModelEndpoint;
import com.ai.mcp.ModelProviderCatalog;
import com.ai.model.ModelConfig;
import com.ai.modelconfig.dto.AvailableModelsRequest;
import com.ai.modelconfig.dto.AvailableModelsResponse;
import com.ai.modelconfig.dto.ModelConfigDetailResponse;
import com.ai.modelconfig.dto.ModelConfigListResponse;
import com.ai.modelconfig.dto.ModelConfigMutationResponse;
import com.ai.modelconfig.dto.ModelConfigRequest;
import com.ai.modelconfig.dto.ModelConfigResponse;
import com.ai.modelconfig.dto.PersonalModelOptionResponse;
import com.ai.modelconfig.dto.PersonalModelOptionsResponse;
import com.ai.repository.ModelConfigRepository;
import com.ai.security.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 租户维度的模型配置应用服务。
 *
 * @author data-agent
 */
@Service
public class ModelConfigService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelConfigService.class);
    private static final String DEFAULT_TENANT_ID = "default";
    private static final String MASKED_SECRET_SHORT = "****";
    private static final String MASKED_SECRET_LONG = "******";

    private final ModelConfigRepository modelConfigRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SecurityContextHelper securityContextHelper;
    private final ModelHttpClient modelHttpClient;
    private final ModelEndpointResolver endpointResolver;

    public ModelConfigService(ModelConfigRepository modelConfigRepository,
            ApplicationEventPublisher eventPublisher,
            SecurityContextHelper securityContextHelper,
            ModelHttpClient modelHttpClient,
            ModelEndpointResolver endpointResolver) {
        this.modelConfigRepository = modelConfigRepository;
        this.eventPublisher = eventPublisher;
        this.securityContextHelper = securityContextHelper;
        this.modelHttpClient = modelHttpClient;
        this.endpointResolver = endpointResolver;
    }

    /**
     * 调用厂商接口拉取可用模型列表，用于配置页一键选择模型名。
     *
     * <p>编辑场景下前端传来的 apiKey 是掩码，此时根据 modelId 读取库中真实密钥；
     * provider 与 baseUrl 同样按"请求优先、库中兜底"的顺序解析。</p>
     *
     * @param request 拉取请求
     * @return 模型列表响应
     */
    public AvailableModelsResponse fetchAvailableModels(AvailableModelsRequest request) {
        ModelConfig probe = new ModelConfig();
        probe.setProvider(trimToNull(request.provider()));
        probe.setBaseUrl(trimToNull(request.baseUrl()));
        probe.setApiKey(shouldUpdateSecret(request.apiKey()) ? request.apiKey().trim() : null);

        if (probe.getApiKey() == null && StringUtils.hasText(request.modelId())) {
            ModelConfig existing = getModel(request.modelId());
            if (existing != null) {
                if (probe.getProvider() == null) {
                    probe.setProvider(existing.getProvider());
                }
                boolean sameEndpoint = sameEndpoint(existing.getProvider(), existing.getBaseUrl(),
                        probe.getProvider(), probe.getBaseUrl());
                if (sameEndpoint) {
                    probe.setApiKey(existing.getApiKey());
                }
                if (sameEndpoint && probe.getBaseUrl() == null) {
                    probe.setBaseUrl(existing.getBaseUrl());
                }
            }
        }
        try {
            ResolvedModelEndpoint endpoint = endpointResolver.resolveForDiscovery(
                    probe.getProvider(), probe.getBaseUrl(), probe.getApiKey());
            if (!endpoint.modelDiscoverySupported()) {
                return AvailableModelsResponse.failure("该厂商不支持自动获取模型列表，请手动填写模型名");
            }
            return AvailableModelsResponse.success(modelHttpClient.listAvailableModels(endpoint));
        } catch (Exception e) {
            LOGGER.warn("Fetch available models failed: provider={}, baseUrl={}",
                    probe.getProvider(), probe.getBaseUrl(), e);
            return AvailableModelsResponse.failure("获取模型列表失败（该厂商可能不支持自动获取，请手动填写模型名）: "
                    + e.getMessage());
        }
    }

    @Caching(evict = {
            @CacheEvict(value = CacheNames.MODELS, allEntries = true),
            @CacheEvict(value = CacheNames.MODEL_DETAIL, allEntries = true)
    })
    @Transactional(rollbackFor = Exception.class)
    public ModelConfigMutationResponse addModel(ModelConfigRequest request) {
        ModelConfig modelConfig = new ModelConfig();
        modelConfig.setTenantId(securityContextHelper.getCurrentTenantId());
        modelConfig.setCreatedBy(securityContextHelper.getCurrentUserId());
        applyRequest(modelConfig, request, false);
        validateResolved(modelConfig);
        modelConfigRepository.save(modelConfig);
        enforceSingleDefault(modelConfig);
        LOGGER.info("模型已添加: {}, 租户: {}", modelConfig.getName(), modelConfig.getTenantId());
        return ModelConfigMutationResponse.created(modelConfig.getModelId(), modelConfig.getName());
    }

    @Caching(evict = {
            @CacheEvict(value = CacheNames.MODELS, allEntries = true),
            @CacheEvict(value = CacheNames.MODEL_DETAIL, allEntries = true)
    })
    @Transactional(rollbackFor = Exception.class)
    public ModelConfigMutationResponse updateModel(String modelId, ModelConfigRequest request) {
        ModelConfig existing = findMutableModelById(modelId);
        if (existing == null) {
            return ModelConfigMutationResponse.failure("模型不存在或无权限");
        }

        String requestedProvider = StringUtils.hasText(request.provider())
                ? request.provider()
                : existing.getProvider();
        String requestedBaseUrl = request.baseUrl() == null ? existing.getBaseUrl() : request.baseUrl();
        boolean connectionTargetChanged = !sameEndpoint(existing.getProvider(), existing.getBaseUrl(),
                requestedProvider, requestedBaseUrl);
        applyRequest(existing, request, true);
        if (connectionTargetChanged && !shouldUpdateSecret(request.apiKey())) {
            existing.setApiKey(null);
        }
        validateResolved(existing);
        modelConfigRepository.save(existing);
        enforceSingleDefault(existing);
        publishChange(modelId, ModelConfigChangeEvent.ChangeType.UPDATED);
        LOGGER.info("模型已更新: {}", modelId);
        return ModelConfigMutationResponse.updated(modelId);
    }

    @Caching(evict = {
            @CacheEvict(value = CacheNames.MODELS, allEntries = true),
            @CacheEvict(value = CacheNames.MODEL_DETAIL, allEntries = true)
    })
    @Transactional(rollbackFor = Exception.class)
    public ModelConfigMutationResponse deleteModel(String modelId) {
        ModelConfig existing = findMutableModelById(modelId);
        if (existing == null) {
            return ModelConfigMutationResponse.failure("模型不存在或无权限");
        }

        modelConfigRepository.delete(existing);
        publishChange(modelId, ModelConfigChangeEvent.ChangeType.DELETED);
        LOGGER.info("模型已删除: {}", modelId);
        return ModelConfigMutationResponse.deleted();
    }

    @Caching(evict = {
            @CacheEvict(value = CacheNames.MODELS, allEntries = true),
            @CacheEvict(value = CacheNames.MODEL_DETAIL, allEntries = true)
    })
    @Transactional(rollbackFor = Exception.class)
    public ModelConfigMutationResponse toggleModel(String modelId) {
        ModelConfig existing = findMutableModelById(modelId);
        if (existing == null) {
            return ModelConfigMutationResponse.failure("模型不存在或无权限");
        }

        existing.setEnabled(!existing.isEnabled());
        modelConfigRepository.save(existing);
        publishChange(modelId, ModelConfigChangeEvent.ChangeType.TOGGLED);
        return ModelConfigMutationResponse.toggled(modelId, existing.isEnabled());
    }

    @Cacheable(value = CacheNames.MODELS, key = "@securityContextHelper.currentTenantId")
    @Transactional(readOnly = true)
    public ModelConfigListResponse listModels() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        List<ModelConfig> models = new ArrayList<>(modelConfigRepository.findByTenantId(tenantId));
        if (!DEFAULT_TENANT_ID.equals(tenantId)) {
            models.addAll(modelConfigRepository.findByTenantId(DEFAULT_TENANT_ID));
        }
        return new ModelConfigListResponse(true, models.stream()
                .map(ModelConfigResponse::from)
                .collect(Collectors.toList()));
    }

    /**
     * 返回个人 Agent 可选择的启用模型，不暴露连接地址和凭据。
     *
     * @return 当前租户及共享租户的模型选项
     */
    @Transactional(readOnly = true)
    public PersonalModelOptionsResponse listPersonalModelOptions() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        Map<String, ModelConfig> models = new LinkedHashMap<>();
        if (StringUtils.hasText(tenantId)) {
            addEnabledModels(models, tenantId);
        }
        if (!DEFAULT_TENANT_ID.equals(tenantId)) {
            addEnabledModels(models, DEFAULT_TENANT_ID);
        }
        List<PersonalModelOptionResponse> options = models.values().stream()
                .map(PersonalModelOptionResponse::from)
                .toList();
        return new PersonalModelOptionsResponse(true, options);
    }

    @Cacheable(value = CacheNames.MODEL_DETAIL, key = "#modelId + ':' + @securityContextHelper.currentTenantId")
    @Transactional(readOnly = true)
    public ModelConfigDetailResponse getModelDetail(String modelId) {
        ModelConfig config = getModel(modelId);
        if (config == null) {
            return ModelConfigDetailResponse.failure("模型不存在或无权限");
        }
        return ModelConfigDetailResponse.success(ModelConfigResponse.from(config));
    }

    // 注意：实体含明文 apiKey（JPA 解密后），禁止上 Redis 缓存，避免密钥以明文落入缓存层
    @Transactional(readOnly = true)
    public ModelConfig getModel(String modelId) {
        return findModelById(modelId);
    }

    /**
     * 解析当前租户应使用的默认模型：优先本租户的默认/启用模型，其次共享租户（default），均无则返回 null。
     *
     * <p>供未指定 modelId 的调用链路使用，保证后台配置的默认模型真正生效，
     * 而不是直接落到 yml 占位配置。</p>
     *
     * @return 默认模型配置，数据库无可用配置时返回 null
     */
    @Transactional(readOnly = true)
    public ModelConfig resolveTenantDefaultModel() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        if (StringUtils.hasText(tenantId)) {
            ModelConfig tenantModel = getFirstEnabledModel(tenantId);
            if (tenantModel != null) {
                return tenantModel;
            }
        }
        if (!DEFAULT_TENANT_ID.equals(tenantId)) {
            return getFirstEnabledModel(DEFAULT_TENANT_ID);
        }
        return null;
    }

    @Transactional(readOnly = true)
    public ModelConfig getFirstEnabledModel(String tenantId) {
        List<ModelConfig> defaults = modelConfigRepository.findByTenantIdAndEnabledTrueAndIsDefaultTrue(tenantId);
        if (!defaults.isEmpty()) {
            return defaults.get(0);
        }
        List<ModelConfig> enabled = modelConfigRepository.findByTenantIdAndEnabledTrue(tenantId);
        return enabled.isEmpty() ? null : enabled.get(0);
    }

    @Transactional(readOnly = true)
    public List<ModelConfig> getEnabledModels(String tenantId) {
        return modelConfigRepository.findByTenantIdAndEnabledTrue(tenantId);
    }

    private void addEnabledModels(Map<String, ModelConfig> models, String tenantId) {
        modelConfigRepository.findByTenantIdAndEnabledTrue(tenantId)
                .forEach(model -> models.putIfAbsent(model.getModelId(), model));
    }

    private ModelConfig findModelById(String modelId) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        var existingOptional = modelConfigRepository.findByModelIdAndTenantId(modelId, tenantId);
        if (existingOptional.isPresent()) {
            return existingOptional.get();
        }
        if (!DEFAULT_TENANT_ID.equals(tenantId)) {
            existingOptional = modelConfigRepository.findByModelIdAndTenantId(modelId, DEFAULT_TENANT_ID);
            if (existingOptional.isPresent()) {
                return existingOptional.get();
            }
        }
        return null;
    }

    private ModelConfig findMutableModelById(String modelId) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        return modelConfigRepository.findByModelIdAndTenantId(modelId, tenantId)
                .orElse(null);
    }

    private void applyRequest(ModelConfig target, ModelConfigRequest request, boolean partialUpdate) {
        applyRequiredFields(target, request, partialUpdate);
        if (shouldUpdateSecret(request.apiKey())) {
            target.setApiKey(request.apiKey().trim());
        }
        if (request.baseUrl() != null) {
            target.setBaseUrl(trimToNull(request.baseUrl()));
        }
        if (request.modelName() != null) {
            target.setModelName(trimToNull(request.modelName()));
        }
        if (request.temperature() != null) {
            target.setTemperature(request.temperature());
        }
        if (request.maxTokens() != null) {
            target.setMaxTokens(request.maxTokens());
        }
        if (request.enabled() != null) {
            target.setEnabled(request.enabled());
        } else if (!partialUpdate) {
            target.setEnabled(true);
        }
        if (request.isDefault() != null) {
            target.setDefault(request.isDefault());
        }
    }

    private void applyRequiredFields(ModelConfig target, ModelConfigRequest request, boolean partialUpdate) {
        if (StringUtils.hasText(request.name())) {
            target.setName(request.name().trim());
        } else if (!partialUpdate) {
            target.setName(request.name());
        }
        if (StringUtils.hasText(request.provider())) {
            target.setProvider(request.provider().trim());
        } else if (!partialUpdate) {
            target.setProvider(request.provider());
        }
    }

    private void validateResolved(ModelConfig config) {
        if (!StringUtils.hasText(config.getName())) {
            throw new IllegalArgumentException("模型名称不能为空");
        }
        if (!StringUtils.hasText(config.getProvider())) {
            throw new IllegalArgumentException("模型供应商不能为空");
        }
        if (config.getTemperature() != null
                && (!Double.isFinite(config.getTemperature())
                || config.getTemperature() < 0 || config.getTemperature() > 2)) {
            throw new IllegalArgumentException("Temperature 必须在 0 到 2 之间");
        }
        if (config.getMaxTokens() != null
                && (config.getMaxTokens() < 1 || config.getMaxTokens() > 1_000_000)) {
            throw new IllegalArgumentException("Max Tokens 必须在 1 到 1000000 之间");
        }

        ResolvedModelEndpoint endpoint = endpointResolver.resolve(config);
        config.setProvider(endpoint.providerKey());
        config.setBaseUrl(endpoint.baseUrl());
        config.setModelName(endpoint.modelName());
    }

    private boolean shouldUpdateSecret(String apiKey) {
        return StringUtils.hasText(apiKey)
                && !Objects.equals(apiKey, MASKED_SECRET_SHORT)
                && !Objects.equals(apiKey, MASKED_SECRET_LONG)
                && !apiKey.contains(MASKED_SECRET_SHORT);
    }

    private boolean providerChanged(String existingProvider, String requestedProvider) {
        if (!StringUtils.hasText(requestedProvider)) {
            return false;
        }
        String existingKey = ModelProviderCatalog.resolve(existingProvider)
                .map(ModelProviderCatalog::key)
                .orElse(existingProvider == null ? "" : existingProvider.trim().toLowerCase());
        String requestedKey = ModelProviderCatalog.resolve(requestedProvider)
                .map(ModelProviderCatalog::key)
                .orElse(requestedProvider.trim().toLowerCase());
        return !Objects.equals(existingKey, requestedKey);
    }

    private boolean sameEndpoint(String firstProvider, String firstBaseUrl,
            String secondProvider, String secondBaseUrl) {
        if (providerChanged(firstProvider, secondProvider)) {
            return false;
        }
        if (secondBaseUrl == null) {
            return true;
        }
        return Objects.equals(effectiveBaseUrl(firstProvider, firstBaseUrl),
                effectiveBaseUrl(secondProvider, secondBaseUrl));
    }

    private String effectiveBaseUrl(String provider, String baseUrl) {
        String resolved = StringUtils.hasText(baseUrl)
                ? baseUrl.trim()
                : ModelProviderCatalog.resolve(provider)
                        .map(ModelProviderCatalog::defaultBaseUrl)
                        .orElse(null);
        while (resolved != null && resolved.endsWith("/")) {
            resolved = resolved.substring(0, resolved.length() - 1);
        }
        return resolved;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private void publishChange(String modelId, ModelConfigChangeEvent.ChangeType changeType) {
        eventPublisher.publishEvent(new ModelConfigChangeEvent(this, modelId, changeType));
    }

    private void enforceSingleDefault(ModelConfig modelConfig) {
        if (!modelConfig.isDefault()) {
            return;
        }
        modelConfigRepository.clearOtherDefaults(modelConfig.getTenantId(), modelConfig.getModelId());
    }
}
