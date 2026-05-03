package com.ai.controller;

import com.ai.mcp.TokenMonitor;
import com.ai.model.TokenUsage;
import com.ai.repository.TokenUsageRepository;
import com.ai.security.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/token")
public class TokenController {

    private static final Logger log = LoggerFactory.getLogger(TokenController.class);

    private final TokenMonitor tokenMonitor;
    private final TokenUsageRepository tokenUsageRepository;
    private final SecurityContextHelper securityContextHelper;

    public TokenController(TokenMonitor tokenMonitor, TokenUsageRepository tokenUsageRepository,
            SecurityContextHelper securityContextHelper) {
        this.tokenMonitor = tokenMonitor;
        this.tokenUsageRepository = tokenUsageRepository;
        this.securityContextHelper = securityContextHelper;
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

        Map<String, Object> result = new HashMap<>();
        result.put("totalTokens", total != null ? total : 0);
        result.put("byModel", byModel);
        result.put("bySkill", bySkill);
        return result;
    }

    @GetMapping("/user-summary")
    public Map<String, Object> getUserTokenSummary() {
        String userId = securityContextHelper.getCurrentUserId();
        Long total = tokenUsageRepository.sumTotalTokensByUserId(userId);
        return Map.of("totalTokens", total != null ? total : 0);
    }

    @GetMapping("/usage-records")
    public Map<String, Object> getUsageRecords(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        Page<TokenUsage> records = tokenUsageRepository.findByTenantIdOrderByCreatedAtDesc(
                tenantId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        Map<String, Object> result = new HashMap<>();
        result.put("records", records.getContent());
        result.put("totalElements", records.getTotalElements());
        result.put("totalPages", records.getTotalPages());
        result.put("currentPage", records.getNumber());
        return result;
    }

    @GetMapping("/reset")
    public Map<String, Object> resetTokenUsage() {
        tokenMonitor.resetTokenUsage();
        return Map.of("success", true, "message", "Token usage statistics reset successfully");
    }
}
