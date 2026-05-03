package com.ai.service;
  
import com.ai.model.ModelConfig;
import com.ai.repository.ModelConfigRepository;
import com.ai.security.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ModelConfigService {

    private static final Logger log = LoggerFactory.getLogger(ModelConfigService.class);

    private final ModelConfigRepository modelConfigRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SecurityContextHelper securityContextHelper;

    public ModelConfigService(ModelConfigRepository modelConfigRepository,
            ApplicationEventPublisher eventPublisher,
            SecurityContextHelper securityContextHelper) {
        this.modelConfigRepository = modelConfigRepository;
        this.eventPublisher = eventPublisher;
        this.securityContextHelper = securityContextHelper;
    }

    @Transactional
    public Map<String, Object> addModel(ModelConfig modelConfig) {
        try {
            modelConfig.setTenantId(securityContextHelper.getCurrentTenantId());
            modelConfig.setCreatedBy(securityContextHelper.getCurrentUserId());
            modelConfig.setEnabled(true);
            modelConfigRepository.save(modelConfig);
            log.info("Model added: {}, tenant: {}", modelConfig.getName(), modelConfig.getTenantId());
            return Map.of("success", true, "modelId", modelConfig.getModelId(), "name", modelConfig.getName(),
                    "message", "模型添加成功");
        } catch (Exception e) {
            log.error("Failed to add model", e);
            return Map.of("success", false, "message", "模型添加失败: " + e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> updateModel(String modelId, ModelConfig updatedConfig) {
        ModelConfig existing = findModelById(modelId);
        if (existing == null) {
            return Map.of("success", false, "message", "模型不存在或无权限");
        }

        if (updatedConfig.getName() != null)
            existing.setName(updatedConfig.getName());
        if (updatedConfig.getProvider() != null)
            existing.setProvider(updatedConfig.getProvider());
        if (updatedConfig.getApiKey() != null)
            existing.setApiKey(updatedConfig.getApiKey());
        if (updatedConfig.getBaseUrl() != null)
            existing.setBaseUrl(updatedConfig.getBaseUrl());
        if (updatedConfig.getModelName() != null)
            existing.setModelName(updatedConfig.getModelName());
        if (updatedConfig.getTemperature() != null)
            existing.setTemperature(updatedConfig.getTemperature());
        if (updatedConfig.getMaxTokens() != null)
            existing.setMaxTokens(updatedConfig.getMaxTokens());

        modelConfigRepository.save(existing);
        eventPublisher.publishEvent(new com.ai.event.ModelConfigChangeEvent(this, modelId,
                com.ai.event.ModelConfigChangeEvent.ChangeType.UPDATED));
        log.info("Model updated: {}", modelId);
        return Map.of("success", true, "modelId", modelId, "message", "模型更新成功");
    }

    @Transactional
    public Map<String, Object> deleteModel(String modelId) {
        ModelConfig existing = findModelById(modelId);
        if (existing == null) {
            return Map.of("success", false, "message", "模型不存在或无权限");
        }

        modelConfigRepository.delete(existing);
        eventPublisher.publishEvent(new com.ai.event.ModelConfigChangeEvent(this, modelId,
                com.ai.event.ModelConfigChangeEvent.ChangeType.DELETED));
        log.info("Model deleted: {}", modelId);
        return Map.of("success", true, "message", "模型删除成功");
    }

    @Transactional
    public Map<String, Object> toggleModel(String modelId) {
        ModelConfig existing = findModelById(modelId);
        if (existing == null) {
            return Map.of("success", false, "message", "模型不存在或无权限");
        }

        existing.setEnabled(!existing.isEnabled());
        modelConfigRepository.save(existing);
        eventPublisher.publishEvent(new com.ai.event.ModelConfigChangeEvent(this, modelId,
                com.ai.event.ModelConfigChangeEvent.ChangeType.TOGGLED));
        return Map.of("success", true, "modelId", modelId, "enabled", existing.isEnabled(), "message", "模型状态更新成功");
    }

    public Map<String, Object> listModels() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        List<ModelConfig> tenantModels = modelConfigRepository.findByTenantId(tenantId);
        if (!tenantId.equals("default")) {
            List<ModelConfig> defaultModels = modelConfigRepository.findByTenantId("default");
            tenantModels.addAll(defaultModels);
        }
        return Map.of("success", true, "models", tenantModels);
    }

    public ModelConfig getModel(String modelId) {
        return findModelById(modelId);
    }

    public ModelConfig getFirstEnabledModel(String tenantId) {
        List<ModelConfig> enabled = modelConfigRepository.findByTenantIdAndEnabledTrue(tenantId);
        return enabled.isEmpty() ? null : enabled.get(0);
    }

    public List<ModelConfig> getEnabledModels(String tenantId) {
        return modelConfigRepository.findByTenantIdAndEnabledTrue(tenantId);
    }

    private ModelConfig findModelById(String modelId) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        var existingOpt = modelConfigRepository.findByModelIdAndTenantId(modelId, tenantId);
        if (existingOpt.isPresent()) {
            return existingOpt.get();
        }
        if (!tenantId.equals("default")) {
            existingOpt = modelConfigRepository.findByModelIdAndTenantId(modelId, "default");
            if (existingOpt.isPresent()) {
                return existingOpt.get();
            }
        }
        return null;
    }
}
