package com.ai.controller;

import com.ai.mcp.TokenMonitor;
import com.ai.model.TokenUsage;
import com.ai.repository.TokenUsageRepository;
import com.ai.security.SecurityContextHelper;
import com.ai.service.TokenQuotaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Token usage and quota API.
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/token")
public class TokenController {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final String KEY_TOTAL_TOKENS = "totalTokens";
    private static final String KEY_BY_MODEL = "byModel";
    private static final String KEY_BY_SKILL = "bySkill";
    private static final String KEY_RECORDS = "records";
    private static final String KEY_TOTAL_ELEMENTS = "totalElements";
    private static final String KEY_TOTAL_PAGES = "totalPages";
    private static final String KEY_CURRENT_PAGE = "currentPage";
    private static final String KEY_SUCCESS = "success";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_REMAINING_TOKENS = "remainingTokens";
    private static final int TENANT_SUMMARY_INITIAL_CAPACITY = 3;
    private static final int USAGE_RECORDS_INITIAL_CAPACITY = 4;

    private final TokenMonitor tokenMonitor;
    private final TokenUsageRepository tokenUsageRepository;
    private final SecurityContextHelper securityContextHelper;
    private final TokenQuotaService tokenQuotaService;

    public TokenController(TokenMonitor tokenMonitor, TokenUsageRepository tokenUsageRepository,
            SecurityContextHelper securityContextHelper, TokenQuotaService tokenQuotaService) {
        this.tokenMonitor = tokenMonitor;
        this.tokenUsageRepository = tokenUsageRepository;
        this.securityContextHelper = securityContextHelper;
        this.tokenQuotaService = tokenQuotaService;
    }

    @GetMapping("/skill-usage")
    public Map<String, Long> getSkillTokenUsage() {
        return tokenMonitor.getSkillTokenUsage();
    }

    @GetMapping("/model-usage")
    public Map<String, Long> getModelTokenUsage() {
        return tokenMonitor.getModelTokenUsage();
    }

    @GetMapping("/tenant-summary")
    public Map<String, Object> getTenantTokenSummary() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        Long total = tokenUsageRepository.sumTotalTokensByTenantId(tenantId);
        List<Object[]> byModel = tokenUsageRepository.sumTokensByModelGroupedByTenant(tenantId);
        List<Object[]> bySkill = tokenUsageRepository.sumTokensBySkillGroupedByTenant(tenantId);

        Map<String, Object> result = new HashMap<>(TENANT_SUMMARY_INITIAL_CAPACITY);
        result.put(KEY_TOTAL_TOKENS, total != null ? total : 0);
        result.put(KEY_BY_MODEL, byModel);
        result.put(KEY_BY_SKILL, bySkill);
        return result;
    }

    @GetMapping("/user-summary")
    public Map<String, Object> getUserTokenSummary() {
        String userId = securityContextHelper.getCurrentUserId();
        Long total = tokenUsageRepository.sumTotalTokensByUserId(userId);
        return Map.of(KEY_TOTAL_TOKENS, total != null ? total : 0);
    }

    @GetMapping("/usage-records")
    public Map<String, Object> getUsageRecords(
            @RequestParam(defaultValue = "" + DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        Page<TokenUsage> records = tokenUsageRepository.findByTenantIdOrderByCreatedAtDesc(
                tenantId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        Map<String, Object> result = new HashMap<>(USAGE_RECORDS_INITIAL_CAPACITY);
        result.put(KEY_RECORDS, records.getContent());
        result.put(KEY_TOTAL_ELEMENTS, records.getTotalElements());
        result.put(KEY_TOTAL_PAGES, records.getTotalPages());
        result.put(KEY_CURRENT_PAGE, records.getNumber());
        return result;
    }

    @GetMapping("/reset")
    public Map<String, Object> resetTokenUsage() {
        tokenMonitor.resetTokenUsage();
        return Map.of(KEY_SUCCESS, true, KEY_MESSAGE, "Token usage statistics reset successfully");
    }

    @GetMapping("/remaining-quota")
    public Map<String, Object> getRemainingQuota() {
        String userId = securityContextHelper.getCurrentUserId();
        long remaining = tokenQuotaService.getRemainingQuota(userId);
        return Map.of(KEY_SUCCESS, true, KEY_REMAINING_TOKENS, remaining == Long.MAX_VALUE ? -1 : remaining);
    }
}
