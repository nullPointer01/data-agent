package com.ai.service;

import com.ai.exception.QuotaExceededException;
import com.ai.model.TokenUsage;
import com.ai.repository.TokenUsageRepository;
import com.ai.token.TokenAccountingStatus;
import com.ai.token.TokenQuotaMapper;
import com.ai.token.TokenReservation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * 用户日配额的持久预占与幂等结算，网络调用始终在事务外。
 *
 * @author data-agent
 */
@Service
public class TokenQuotaService {

    private final long globalDailyLimit;
    private final ZoneId zone;
    private final long ttlSeconds;
    private final long inputSafetyTokens;
    private final TokenQuotaMapper mapper;
    private final TokenUsageRepository tokenUsageRepository;

    public TokenQuotaService(TokenQuotaMapper mapper, TokenUsageRepository tokenUsageRepository,
            @Value("${app.token-quota.daily-limit-per-user:1000000}") long globalDailyLimit,
            @Value("${app.token-quota.zone-id:Asia/Shanghai}") String zoneId,
            @Value("${app.token-quota.reservation-ttl:PT10M}") Duration ttl,
            @Value("${app.token-quota.input-safety-tokens:256}") long inputSafetyTokens) {
        if (globalDailyLimit < -1 || globalDailyLimit == 0 || ttl.toSeconds() < 600
                || inputSafetyTokens < 0 || inputSafetyTokens > 100000) {
            throw new IllegalArgumentException("Token 配额配置无效，预占期限至少 10 分钟");
        }
        this.mapper = mapper;
        this.tokenUsageRepository = tokenUsageRepository;
        this.globalDailyLimit = globalDailyLimit;
        this.zone = ZoneId.of(zoneId);
        this.ttlSeconds = ttl.toSeconds();
        this.inputSafetyTokens = inputSafetyTokens;
    }

    /** 一次实际尝试独占日账本短事务；并发/多实例共享同一个余额。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TokenReservation reserve(String tenant, String user, long input, int requestedOutput,
            String modelId, String modelName, String sessionId, String runId) {
        if (input < 0 || requestedOutput < 1) throw new IllegalArgumentException("模型 Token 预占参数无效");
        long limit = resolveLimit(tenant, user);
        LocalDate day = LocalDate.now(zone);
        var balance = initialize(tenant, user, day);
        long inputCeiling = Math.addExact(input, inputSafetyTokens);
        long available = limit == -1 ? Long.MAX_VALUE
                : Math.max(0, limit - balance.chargedTokens() - balance.reservedTokens());
        if (available <= inputCeiling) {
            throw new QuotaExceededException("今日可用 Token 不足以发送本次上下文，请稍后重试或联系管理员调整配额");
        }
        int output = (int) Math.min(requestedOutput, available - inputCeiling);
        long reserved = Math.addExact(inputCeiling, output);
        var ticket = new TokenReservation(UUID.randomUUID().toString(), tenant, user, day,
                input, reserved, output, modelId, modelName, sessionId, runId, "PENDING", 0);
        mapper.insertReservation(ticket, ttlSeconds);
        adjust(ticket, 0, reserved, 0);
        return ticket;
    }

    /** 发送前持久标记；已过期/释放的预占绝不能继续访问厂商。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(TokenReservation ticket) {
        lock(ticket);
        if (mapper.markDispatched(ticket.invocationId()) != 1) {
            throw new QuotaExceededException("模型预占已失效，本次未发送请求，请重新发起");
        }
    }

    /** 同一预占只能结算一次；迟到完整响应可将 UNKNOWN 校正为厂商用量/完整估算。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long settle(TokenReservation ticket, long input, long output, long total, TokenAccountingStatus status) {
        if (input < 0 || output < 0 || total < Math.addExact(input, output)
                || (status != TokenAccountingStatus.ACTUAL && status != TokenAccountingStatus.ESTIMATED)) {
            throw new IllegalArgumentException("模型 Token 用量合同无效");
        }
        var row = lock(ticket);
        if ("SETTLED".equals(row.state()) || "RELEASED".equals(row.state())) return row.chargedTokens();
        if (!"DISPATCHED".equals(row.state()) && !"UNKNOWN".equals(row.state())) {
            throw new IllegalStateException("尚未发送的预占不能记模型用量");
        }
        return finish(row, "SETTLED", input, output, total, status);
    }

    /** 发送前失败释放；发送后失败不可假定免费，按预占上界扣额并保留 UNKNOWN。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long fail(TokenReservation ticket) {
        return finishFailure(lock(ticket));
    }

    /** 查询只恢复本人最多 25 个过期单，历史单仍记回原日；不增加全库高频扫描任务。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public QuotaSnapshot current(String tenant, String user) {
        long limit = resolveLimit(tenant, user);
        LocalDate day = LocalDate.now(zone);
        var balance = initialize(tenant, user, day);
        long remaining = limit == -1 ? -1 : Math.max(0, limit - balance.chargedTokens() - balance.reservedTokens());
        return new QuotaSnapshot(true, day, zone.getId(), limit, balance.chargedTokens(),
                balance.reservedTokens(), balance.uncertainTokens(), remaining);
    }

    private long resolveLimit(String tenant, String user) {
        if (tenant == null || tenant.isBlank() || user == null || user.isBlank()) {
            throw new SecurityException("模型调用需要有效租户和用户身份");
        }
        var account = mapper.findUser(tenant, user);
        if (account == null || !account.enabled()) throw new SecurityException("模型调用账号已失效");
        long limit = account.dailyTokenLimit() == 0 ? globalDailyLimit : account.dailyTokenLimit();
        if (limit < -1) throw new IllegalStateException("用户 Token 配额配置无效");
        return limit;
    }

    private TokenQuotaMapper.Daily initialize(String tenant, String user, LocalDate day) {
        // 先按配额日升序恢复历史单，再锁今天；所有多日操作保持同一加锁顺序。
        for (var expired : mapper.findExpired(tenant, user)) finishFailure(lock(expired));
        mapper.ensureDaily(tenant, user, day);
        return mapper.lockDaily(tenant, user, day);
    }

    private TokenReservation lock(TokenReservation ticket) {
        if (mapper.lockDaily(ticket.tenantId(), ticket.userId(), ticket.quotaDate()) == null) {
            throw new IllegalStateException("Token 日账本不存在");
        }
        var row = mapper.lockReservation(ticket.invocationId());
        if (row == null || !Objects.equals(ticket.tenantId(), row.tenantId())
                || !Objects.equals(ticket.userId(), row.userId()) || !Objects.equals(ticket.quotaDate(), row.quotaDate())) {
            throw new SecurityException("Token 预占身份不一致");
        }
        return row;
    }

    private long finishFailure(TokenReservation row) {
        if ("PENDING".equals(row.state())) {
            adjust(row, 0, -row.reservedTokens(), 0);
            mapper.finishReservation(row.invocationId(), "RELEASED", 0);
            return 0;
        }
        if (!"DISPATCHED".equals(row.state())) return row.chargedTokens();
        // UNKNOWN 只知道总预扣额，输入/输出填 0 占位，不能把额度分配冒充厂商用量。
        return finish(row, "UNKNOWN", 0, 0, row.reservedTokens(), TokenAccountingStatus.UNKNOWN);
    }

    private long finish(TokenReservation row, String target, long input, long output, long total,
            TokenAccountingStatus status) {
        long reservedDelta = "DISPATCHED".equals(row.state()) ? -row.reservedTokens() : 0;
        long uncertainDelta = ("UNKNOWN".equals(target) ? total : 0)
                - ("UNKNOWN".equals(row.state()) ? row.chargedTokens() : 0);
        long chargedDelta = total - row.chargedTokens();
        adjust(row, chargedDelta, reservedDelta, uncertainDelta);
        mapper.finishReservation(row.invocationId(), target, total);
        var usage = new TokenUsage();
        usage.setInvocationId(row.invocationId());
        usage.setQuotaDate(row.quotaDate());
        usage.setAccountingStatus(status);
        usage.setUserId(row.userId());
        usage.setTenantId(row.tenantId());
        usage.setModelId(row.modelId());
        usage.setModelName(row.modelName());
        usage.setSkillId("direct");
        usage.setSkillName("直接调用");
        usage.setSessionId(row.sessionId());
        usage.setPromptTokens(input);
        usage.setCompletionTokens(output);
        usage.setTotalTokens(total);
        usage.setCreatedAt(LocalDateTime.now(zone));
        tokenUsageRepository.upsertAccounting(usage);
        return total;
    }

    private void adjust(TokenReservation row, long charged, long reserved, long uncertain) {
        if (mapper.adjustDaily(row.tenantId(), row.userId(), row.quotaDate(), charged, reserved, uncertain) != 1) {
            throw new IllegalStateException("Token 日账本余额不一致");
        }
    }

    /** remaining 扣除了进行中预占；-1 表示不限额，UNKNOWN 是 charged 中尚待核账的部分。 */
    public record QuotaSnapshot(boolean success, LocalDate quotaDate, String zoneId, long dailyLimit,
            long chargedTokens, long reservedTokens, long uncertainTokens, long remainingTokens) { }
}
