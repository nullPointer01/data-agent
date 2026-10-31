package com.ai.controller;

import com.ai.api.ListPageResponse;
import com.ai.api.ListPaging;
import com.ai.model.TokenUsage;
import com.ai.repository.TokenUsageRepository;
import com.ai.security.SecurityContextHelper;
import com.ai.service.TokenQuotaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 令牌使用量和配额接口。
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
    private static final int TENANT_SUMMARY_INITIAL_CAPACITY = 3;

    private final TokenUsageRepository tokenUsageRepository;
    private final SecurityContextHelper securityContextHelper;
    private final TokenQuotaService tokenQuotaService;

    public TokenController(TokenUsageRepository tokenUsageRepository,
            SecurityContextHelper securityContextHelper, TokenQuotaService tokenQuotaService) {
        this.tokenUsageRepository = tokenUsageRepository;
        this.securityContextHelper = securityContextHelper;
        this.tokenQuotaService = tokenQuotaService;
    }

    /**
     * 汇总当前租户已持久化的 Token 用量，并按模型和 Skill 分组。
     *
     * @return 租户 Token 汇总
     */
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

    /**
     * 查询当前用户累计的 Token 用量。
     *
     * @return 用户 Token 汇总
     */
    @GetMapping("/user-summary")
    public Map<String, Object> getUserTokenSummary() {
        String userId = securityContextHelper.getCurrentUserId();
        Long total = tokenUsageRepository.sumTotalTokensByOwner(securityContextHelper.getCurrentTenantId(), userId);
        return Map.of(KEY_TOTAL_TOKENS, total != null ? total : 0);
    }

    /**
     * 分页查询当前租户的 Token 使用明细。
     *
     * @param page 从零开始的页码
     * @param size 每页记录数
     * @return Token 明细和分页信息
     */
    @GetMapping("/usage-records")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ListPageResponse<TokenUsage> getUsageRecords(
            @RequestParam(defaultValue = "" + DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        ListPaging.page(page, size);
        String tenantId = securityContextHelper.getCurrentTenantId();
        return ListPageResponse.fetch(page, size, tokenUsageRepository.countByTenantId(tenantId),
                pageable -> tokenUsageRepository.selectUsagePage(tenantId, pageable));
    }

    /**
     * 查询当前用户当天的剩余 Token 配额。
     *
     * @return 剩余配额，返回 -1 表示未限制
     */
    @GetMapping("/remaining-quota")
    public TokenQuotaService.QuotaSnapshot getRemainingQuota() {
        return tokenQuotaService.current(securityContextHelper.getCurrentTenantId(), securityContextHelper.getCurrentUserId());
    }
}
