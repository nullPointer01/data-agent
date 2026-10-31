package com.ai.mcp;

import com.ai.agent.runtime.AgentRunControl;
import com.ai.agent.runtime.AgentRunScope;
import com.ai.agent.runtime.event.AgentEvent;
import com.ai.agent.runtime.event.AgentEventType;
import com.ai.service.TokenQuotaService;
import com.ai.token.TokenAccountingException;
import com.ai.token.TokenAccountingStatus;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 将厂商响应转换为用量合同，并通过日配额服务原子记账；不吞掉持久化失败。
 *
 * @author data-agent
 */
@Component
public class TokenUsageRecorder {

    private final TokenQuotaService quotas;
    private final TokenMonitor monitor;

    public TokenUsageRecorder(TokenQuotaService quotas, TokenMonitor monitor) {
        this.quotas = quotas;
        this.monitor = monitor;
    }

    /** 即使内容校验失败，厂商已返回的用量也必须先记账。 */
    public void complete(TokenQuotaGuard.ReservedRequest attempt, ChatResponse response, AgentRunControl control) {
        java.util.Objects.requireNonNull(attempt, "模型响应缺少预占身份");
        if (response == null) { failed(attempt, control); return; }
        var ticket = attempt.ticket();
        var usage = response.tokenUsage();
        var message = response.aiMessage();
        String text = message == null || message.text() == null ? "" : message.text();
        String tools = message != null && message.hasToolExecutionRequests() ? message.toolExecutionRequests().toString() : "";
        boolean reported = usage != null && usage.inputTokenCount() != null && usage.outputTokenCount() != null
                && usage.totalTokenCount() != null;
        if (usage != null && usage.totalTokenCount() != null && usage.totalTokenCount() < 0) {
            throw new TokenAccountingException("厂商返回了非法 Token 用量", null);
        }
        long input = usage != null && usage.inputTokenCount() != null ? usage.inputTokenCount() : ticket.estimatedInputTokens();
        long output = usage != null && usage.outputTokenCount() != null ? usage.outputTokenCount()
                : monitor.estimateTokens(text + tools, ticket.modelId());
        long total = Math.max(Math.addExact(input, output), usage != null && usage.totalTokenCount() != null
                ? usage.totalTokenCount() : 0L);
        long charged;
        try {
            charged = quotas.settle(ticket, input, output, total,
                    reported ? TokenAccountingStatus.ACTUAL : TokenAccountingStatus.ESTIMATED);
        } catch (RuntimeException error) {
            throw new TokenAccountingException("模型已响应，但 Token 结算未确认；预占仍保留", error);
        }
        budget(attempt, control, charged, !reported);
    }

    /** 失败用完整预占上界占住配额，不能只凭可见 partialText 低估思考/工具输出。 */
    public void failed(TokenQuotaGuard.ReservedRequest attempt, AgentRunControl control) {
        if (attempt == null) return;
        long charged;
        try {
            charged = quotas.fail(attempt.ticket());
        } catch (RuntimeException error) {
            throw new TokenAccountingException("Token 失败结算未确认；预占仍保留", error);
        }
        budget(attempt, control, charged, true);
    }

    private void budget(TokenQuotaGuard.ReservedRequest attempt, AgentRunControl control, long charged, boolean estimated) {
        if (control == null || charged <= 0) return;
        long previous = attempt.runChargedTokens().getAndAccumulate(charged, Math::max);
        long delta = charged - previous;
        if (delta <= 0) return;
        // 日账本允许迟到响应退款；已终止 Run 不恢复执行，运行计数保持保守值。
        control.afterModelCall(delta, estimated);
        AgentRunScope.current().ifPresent(context -> context.eventSink().emit(
                AgentEvent.of(context, AgentEventType.BUDGET_UPDATED, Map.of("usage", context.snapshot()))));
    }
}
