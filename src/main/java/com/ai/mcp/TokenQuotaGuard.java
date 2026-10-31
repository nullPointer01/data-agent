package com.ai.mcp;

import com.ai.agent.runtime.AgentRunScope;
import com.ai.security.SecurityContextHelper;
import com.ai.service.TokenQuotaService;
import com.ai.token.TokenAccountingException;
import com.ai.token.TokenReservation;
import com.ai.exception.QuotaExceededException;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 当前用户模型令牌配额保护器。
 *
 * @author data-agent
 */
@Component
public class TokenQuotaGuard {

    private final SecurityContextHelper securityContextHelper;
    private final TokenQuotaService tokenQuotaService;
    private final TokenMonitor tokenMonitor;
    private final int defaultOutputTokens;

    public TokenQuotaGuard(SecurityContextHelper securityContextHelper, TokenQuotaService tokenQuotaService,
            TokenMonitor tokenMonitor, @Value("${app.token-quota.default-output-tokens:4096}") int defaultOutputTokens) {
        this.securityContextHelper = securityContextHelper;
        this.tokenQuotaService = tokenQuotaService;
        this.tokenMonitor = tokenMonitor;
        if (defaultOutputTokens < 1) throw new IllegalArgumentException("模型默认输出上限必须为正整数");
        this.defaultOutputTokens = defaultOutputTokens;
    }

    /**
     * 为一次实际模型尝试预占；原生 Builder 只覆盖输出上限，保留 Schema/工具和厂商参数。
     */
    public ReservedRequest reserve(ChatRequest request, long estimatedInput, Integer modelOutputLimit,
            String modelId, String modelName) {
        String tenant = securityContextHelper.getCurrentTenantId();
        String user = securityContextHelper.getCurrentUserId();
        var run = AgentRunScope.current().orElse(null);
        if (run != null && (!java.util.Objects.equals(run.tenantId(), tenant)
                || !java.util.Objects.equals(run.userId(), user))) {
            throw new SecurityException("模型预占与根 Run 身份不一致");
        }
        Integer configured = request.maxOutputTokens() == null ? modelOutputLimit : request.maxOutputTokens();
        int output = configured == null ? defaultOutputTokens : configured;
        if (output < 1) throw new IllegalArgumentException("模型输出上限必须为正整数");
        long input = Math.addExact(estimatedInput, request.responseFormat() == null ? 0
                : tokenMonitor.estimateTokens(request.responseFormat().toString(), modelId));
        TokenReservation ticket;
        try {
            ticket = tokenQuotaService.reserve(tenant, user, input, output, modelId, modelName,
                    run == null ? null : run.sessionId(), run == null ? null : run.runId());
        } catch (QuotaExceededException | SecurityException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new TokenAccountingException("Token 预占暂不可用，模型请求未发送", error);
        }
        try {
            return new ReservedRequest(ticket, request.toBuilder().maxOutputTokens(ticket.maxOutputTokens()).build(),
                    new java.util.concurrent.atomic.AtomicLong());
        } catch (RuntimeException error) {
            try {
                tokenQuotaService.fail(ticket);
            } catch (RuntimeException accounting) {
                accounting.addSuppressed(error);
                throw new TokenAccountingException("请求构建失败且 Token 释放未确认", accounting);
            }
            throw error;
        }
    }

    /** 必须在网络调用紧邻之前完成此短事务。 */
    public void dispatch(TokenReservation ticket) {
        try {
            tokenQuotaService.dispatch(ticket);
        } catch (QuotaExceededException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new TokenAccountingException("Token 预占无法确认，模型请求未发送", error);
        }
    }

    /** 单次尝试的 Run 记账水位独立于日账本，防止失败/迟到回调或过期恢复造成重复/漏计。 */
    public record ReservedRequest(TokenReservation ticket, ChatRequest request,
            java.util.concurrent.atomic.AtomicLong runChargedTokens) { }
}
