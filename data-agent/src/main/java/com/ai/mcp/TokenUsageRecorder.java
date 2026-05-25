package com.ai.mcp;

import com.ai.model.ModelConfig;
import com.ai.model.SkillConfig;
import com.ai.model.TokenUsage;
import com.ai.repository.SkillConfigRepository;
import com.ai.repository.TokenUsageRepository;
import com.ai.security.SecurityContextHelper;
import com.ai.service.ModelConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists model token usage records.
 *
 * @author data-agent
 */
@Component
public class TokenUsageRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenUsageRecorder.class);
    private static final String DIRECT_SKILL_ID = "direct";
    private static final String DIRECT_SKILL_NAME = "直接调用";
    private static final String DEFAULT_MODEL_NAME = "默认模型";

    private final ModelConfigService modelConfigService;
    private final SkillConfigRepository skillConfigRepository;
    private final TokenUsageRepository tokenUsageRepository;
    private final SecurityContextHelper securityContextHelper;

    public TokenUsageRecorder(ModelConfigService modelConfigService,
            SkillConfigRepository skillConfigRepository,
            TokenUsageRepository tokenUsageRepository,
            SecurityContextHelper securityContextHelper) {
        this.modelConfigService = modelConfigService;
        this.skillConfigRepository = skillConfigRepository;
        this.tokenUsageRepository = tokenUsageRepository;
        this.securityContextHelper = securityContextHelper;
    }

    /**
     * Records one token usage item.
     *
     * @param modelId model id
     * @param skillId skill id
     * @param promptTokens prompt token count
     * @param completionTokens completion token count
     * @param totalTokens total token count
     * @param sessionId session id
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(String modelId, String skillId, long promptTokens,
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
            usage.setModelName(resolveModelName(modelId));
            usage.setSkillName(resolveSkillName(skillId));
            tokenUsageRepository.save(usage);
        } catch (Exception e) {
            LOGGER.warn("Failed to record token usage", e);
        }
    }

    private String resolveModelName(String modelId) {
        if (modelId == null) {
            return DEFAULT_MODEL_NAME;
        }
        ModelConfig config = modelConfigService.getModel(modelId);
        return config != null ? config.getName() : modelId;
    }

    private String resolveSkillName(String skillId) {
        if (skillId == null || DIRECT_SKILL_ID.equals(skillId)) {
            return DIRECT_SKILL_NAME;
        }
        String tenantId = securityContextHelper.getCurrentTenantId();
        return skillConfigRepository.findBySkillIdAndTenantId(skillId, tenantId)
                .map(SkillConfig::getName)
                .orElse(skillId);
    }
}
