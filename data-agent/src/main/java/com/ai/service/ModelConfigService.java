package com.ai.service;

import com.ai.config.CacheNames;
import com.ai.event.ModelConfigChangeEvent;
import com.ai.mcp.ModelHttpClient;
import com.ai.model.ModelConfig;
import com.ai.modelconfig.dto.AvailableModelsRequest;
import com.ai.modelconfig.dto.AvailableModelsResponse;
import com.ai.modelconfig.dto.ModelConfigDetailResponse;
import com.ai.modelconfig.dto.ModelConfigListResponse;
import com.ai.modelconfig.dto.ModelConfigMutationResponse;
import com.ai.modelconfig.dto.ModelConfigRequest;
import com.ai.modelconfig.dto.ModelConfigResponse;
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
import java.util.List;
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

    public ModelConfigService(ModelConfigRepository modelConfigRepository,
            ApplicationEventPublisher eventPublisher,
            SecurityContextHelper securityContextHelper,
            ModelHttpClient modelHttpClient) {
        this.modelConfigRepository = modelConfigRepository;
        this.eventPublisher = eventPublisher;
        this.securityContextHelper = securityContextHelper;
        this.modelHttpClient = modelHttpClient;
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
                probe.setApiKey(existing.getApiKey());
                if (probe.getProvider() == null) {
                    probe.setProvider(existing.getProvider());
                }
                if (probe.getBaseUrl() == null) {
                    probe.setBaseUrl(existing.getBaseUrl());
                }
            }
        }
        if (!StringUtils.hasText(probe.getApiKey())) {
            return AvailableModelsResponse.failure("请先填写 API Key");
        }

        try {
            return AvailableModelsResponse.success(modelHttpClient.listAvailableModels(probe));
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
        validate(request);
        ModelConfig modelConfig = new ModelConfig();
        modelConfig.setTenantId(securityContextHelper.getCurrentTenantId());
        modelConfig.setCreatedBy(securityContextHelper.getCurrentUserId());
        applyRequest(modelConfig, request, false);
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

        applyRequest(existing, request, true);
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

    private void validate(ModelConfigRequest request) {
        if (!StringUtils.hasText(request.name())) {
            throw new IllegalArgumentException("模型名称不能为空");
        }
        if (!StringUtils.hasText(request.provider())) {
            throw new IllegalArgumentException("模型供应商不能为空");
        }
    }

    private boolean shouldUpdateSecret(String apiKey) {
        return StringUtils.hasText(apiKey)
                && !Objects.equals(apiKey, MASKED_SECRET_SHORT)
                && !Objects.equals(apiKey, MASKED_SECRET_LONG);
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
