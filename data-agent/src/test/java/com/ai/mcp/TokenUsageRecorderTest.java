package com.ai.mcp;

import com.ai.model.ModelConfig;
import com.ai.model.SkillConfig;
import com.ai.model.TokenUsage;
import com.ai.repository.SkillConfigRepository;
import com.ai.repository.TokenUsageRepository;
import com.ai.security.SecurityContextHelper;
import com.ai.service.ModelConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenUsageRecorderTest {

    @Test
    void recordResolvesModelAndSkillNamesWithinCurrentTenant() {
        ModelConfigService modelConfigService = mock(ModelConfigService.class);
        SkillConfigRepository skillConfigRepository = mock(SkillConfigRepository.class);
        TokenUsageRepository tokenUsageRepository = mock(TokenUsageRepository.class);
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        ModelConfig modelConfig = new ModelConfig();
        modelConfig.setModelId("model-1");
        modelConfig.setName("Qwen");
        SkillConfig skillConfig = new SkillConfig();
        skillConfig.setSkillId("skill-1");
        skillConfig.setName("销售分析");

        when(securityContextHelper.getCurrentUserId()).thenReturn("user-1");
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-1");
        when(modelConfigService.getModel("model-1")).thenReturn(modelConfig);
        when(skillConfigRepository.findBySkillIdAndTenantId("skill-1", "tenant-1"))
                .thenReturn(Optional.of(skillConfig));

        TokenUsageRecorder recorder = new TokenUsageRecorder(
                modelConfigService, skillConfigRepository, tokenUsageRepository, securityContextHelper);

        recorder.record("model-1", "skill-1", 3L, 5L, 8L, "session-1");

        ArgumentCaptor<TokenUsage> captor = ArgumentCaptor.forClass(TokenUsage.class);
        verify(tokenUsageRepository).save(captor.capture());
        TokenUsage usage = captor.getValue();
        assertEquals("user-1", usage.getUserId());
        assertEquals("tenant-1", usage.getTenantId());
        assertEquals("Qwen", usage.getModelName());
        assertEquals("销售分析", usage.getSkillName());
        assertEquals(8L, usage.getTotalTokens());
    }

    @Test
    void recordUsesDirectSkillNameForDirectCall() {
        ModelConfigService modelConfigService = mock(ModelConfigService.class);
        SkillConfigRepository skillConfigRepository = mock(SkillConfigRepository.class);
        TokenUsageRepository tokenUsageRepository = mock(TokenUsageRepository.class);
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        when(securityContextHelper.getCurrentUserId()).thenReturn("user-1");
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-1");

        TokenUsageRecorder recorder = new TokenUsageRecorder(
                modelConfigService, skillConfigRepository, tokenUsageRepository, securityContextHelper);

        recorder.record(null, "direct", 1L, 2L, 3L, null);

        ArgumentCaptor<TokenUsage> captor = ArgumentCaptor.forClass(TokenUsage.class);
        verify(tokenUsageRepository).save(captor.capture());
        assertEquals("默认模型", captor.getValue().getModelName());
        assertEquals("直接调用", captor.getValue().getSkillName());
    }
}
