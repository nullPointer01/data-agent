package com.ai.service;

import com.ai.model.ModelConfig;
import com.ai.modelconfig.dto.ModelConfigListResponse;
import com.ai.modelconfig.dto.ModelConfigMutationResponse;
import com.ai.modelconfig.dto.ModelConfigRequest;
import com.ai.repository.ModelConfigRepository;
import com.ai.security.SecurityContextHelper;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelConfigServiceTest {

    @Test
    void listModelsMasksApiKey() {
        ModelConfigRepository repository = mock(ModelConfigRepository.class);
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        ModelConfig config = new ModelConfig();
        config.setModelId("model-1");
        config.setName("Qwen");
        config.setProvider("qwen");
        config.setApiKey("sk-1234567890");

        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-1");
        when(repository.findByTenantId("tenant-1")).thenReturn(List.of(config));
        when(repository.findByTenantId("default")).thenReturn(List.of());

        ModelConfigService service = new ModelConfigService(
                repository, mock(ApplicationEventPublisher.class), securityContextHelper);

        ModelConfigListResponse response = service.listModels();

        assertTrue(response.success());
        assertEquals("sk-1****7890", response.models().get(0).apiKey());
    }

    @Test
    void updateKeepsExistingApiKeyWhenMaskedSecretSubmitted() {
        ModelConfigRepository repository = mock(ModelConfigRepository.class);
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        ModelConfig existing = new ModelConfig();
        existing.setModelId("model-1");
        existing.setTenantId("tenant-1");
        existing.setName("Old");
        existing.setProvider("qwen");
        existing.setApiKey("real-secret");

        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-1");
        when(repository.findByModelIdAndTenantId("model-1", "tenant-1")).thenReturn(Optional.of(existing));

        ModelConfigService service = new ModelConfigService(repository, eventPublisher, securityContextHelper);
        ModelConfigRequest request = new ModelConfigRequest(
                "New", "qwen", "******", null, "qwen-plus", null, null, true, false);

        ModelConfigMutationResponse response = service.updateModel("model-1", request);

        assertTrue(response.success());
        assertEquals("real-secret", existing.getApiKey());
        assertEquals("New", existing.getName());
        verify(repository).save(existing);
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void updateClearsOtherDefaultModelsWhenModelBecomesDefault() {
        ModelConfigRepository repository = mock(ModelConfigRepository.class);
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        ModelConfig existing = new ModelConfig();
        existing.setModelId("model-1");
        existing.setTenantId("tenant-1");
        existing.setName("Old");
        existing.setProvider("qwen");

        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-1");
        when(repository.findByModelIdAndTenantId("model-1", "tenant-1")).thenReturn(Optional.of(existing));

        ModelConfigService service = new ModelConfigService(
                repository, mock(ApplicationEventPublisher.class), securityContextHelper);
        ModelConfigRequest request = new ModelConfigRequest(
                "New", "qwen", null, null, "qwen-plus", null, null, true, true);

        ModelConfigMutationResponse response = service.updateModel("model-1", request);

        assertTrue(response.success());
        verify(repository).clearOtherDefaults("tenant-1", "model-1");
    }

    @Test
    void getFirstEnabledModelPrefersTenantDefault() {
        ModelConfigRepository repository = mock(ModelConfigRepository.class);
        ModelConfig defaultModel = new ModelConfig();
        defaultModel.setModelId("default-model");
        ModelConfig firstEnabled = new ModelConfig();
        firstEnabled.setModelId("first-enabled");

        when(repository.findByTenantIdAndEnabledTrueAndIsDefaultTrue("tenant-1"))
                .thenReturn(List.of(defaultModel));
        when(repository.findByTenantIdAndEnabledTrue("tenant-1"))
                .thenReturn(List.of(firstEnabled));

        ModelConfigService service = new ModelConfigService(
                repository, mock(ApplicationEventPublisher.class), mock(SecurityContextHelper.class));

        assertEquals("default-model", service.getFirstEnabledModel("tenant-1").getModelId());
    }

    @Test
    void updateReturnsFailureWhenModelDoesNotBelongToTenant() {
        ModelConfigRepository repository = mock(ModelConfigRepository.class);
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-1");
        when(repository.findByModelIdAndTenantId("model-1", "tenant-1")).thenReturn(Optional.empty());

        ModelConfigService service = new ModelConfigService(
                repository, mock(ApplicationEventPublisher.class), securityContextHelper);
        ModelConfigRequest request = new ModelConfigRequest(
                "New", "qwen", "secret", null, "qwen-plus", null, null, true, false);

        ModelConfigMutationResponse response = service.updateModel("model-1", request);

        assertFalse(response.success());
    }

    @Test
    void updateDoesNotMutateDefaultTenantModelFromBusinessTenant() {
        ModelConfigRepository repository = mock(ModelConfigRepository.class);
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-1");
        when(repository.findByModelIdAndTenantId("model-1", "tenant-1")).thenReturn(Optional.empty());

        ModelConfigService service = new ModelConfigService(
                repository, mock(ApplicationEventPublisher.class), securityContextHelper);
        ModelConfigRequest request = new ModelConfigRequest(
                "New", "qwen", "secret", null, "qwen-plus", null, null, true, false);

        ModelConfigMutationResponse response = service.updateModel("model-1", request);

        assertFalse(response.success());
        verify(repository, org.mockito.Mockito.never()).findByModelIdAndTenantId("model-1", "default");
    }
}
