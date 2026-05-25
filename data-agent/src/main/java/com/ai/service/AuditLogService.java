package com.ai.service;

import com.ai.model.AuditLog;
import com.ai.repository.AuditLogRepository;
import com.ai.security.SecurityContextHelper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * 租户业务操作审计日志服务。
 *
 * @author data-agent
 */
@Service
public class AuditLogService {

    private static final int MAX_AUDIT_MESSAGE_LENGTH = 1024;
    private static final int DEFAULT_PAGE = 0;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 200;
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String IP_DELIMITER = ",";
    private static final String METRIC_AUDIT_EVENTS_TOTAL = "data_agent_audit_events_total";
    private static final String TAG_ACTION = "action";
    private static final String TAG_STATUS = "status";

    private final AuditLogRepository auditLogRepository;
    private final SecurityContextHelper securityContextHelper;
    private final MeterRegistry meterRegistry;

    public AuditLogService(AuditLogRepository auditLogRepository,
            SecurityContextHelper securityContextHelper,
            MeterRegistry meterRegistry) {
        this.auditLogRepository = auditLogRepository;
        this.securityContextHelper = securityContextHelper;
        this.meterRegistry = meterRegistry;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(String action, String resourceType, String resourceId, String status, String message) {
        record(action, resourceType, resourceId, status, message,
                securityContextHelper.getCurrentTenantId(),
                securityContextHelper.getCurrentUserId(),
                securityContextHelper.getCurrentUsername(),
                resolveClientIp());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(String action, String resourceType, String resourceId, String status, String message,
            String tenantId, String userId, String username, String clientIp) {
        AuditLog log = new AuditLog();
        log.setTenantId(tenantId);
        log.setUserId(userId);
        log.setUsername(username);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setStatus(status);
        log.setMessage(truncate(message, MAX_AUDIT_MESSAGE_LENGTH));
        log.setClientIp(clientIp);
        auditLogRepository.save(log);
        meterRegistry.counter(METRIC_AUDIT_EVENTS_TOTAL, TAG_ACTION, action, TAG_STATUS, status).increment();
    }

    @Transactional(readOnly = true)
    public List<AuditLog> listCurrentTenant(int limit, String userId) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        PageRequest page = PageRequest.of(DEFAULT_PAGE, Math.max(MIN_LIMIT, Math.min(limit, MAX_LIMIT)));
        if (userId != null && !userId.isBlank()) {
            return auditLogRepository.findByTenantIdAndUserIdOrderByCreatedAtDesc(tenantId, userId, page);
        }
        return auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, page);
    }

    private String resolveClientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String forwardedFor = request.getHeader(HEADER_X_FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(IP_DELIMITER)[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
