package com.ai.service;

import com.ai.model.AuditLog;
import com.ai.api.CursorPageResponse;
import com.ai.api.ListPaging;
import com.ai.api.RecordCursor;
import com.ai.repository.AuditLogRepository;
import com.ai.security.SecurityContextHelper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;


/**
 * 租户业务操作审计日志服务。
 *
 * @author data-agent
 */
@Service
public class AuditLogService {

    private static final int MAX_AUDIT_MESSAGE_LENGTH = 1024;
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

    /** 当前租户的历史日志按游标翻页；用户和关键词过滤在数据库执行。 */
    @Transactional(readOnly = true)
    public CursorPageResponse<AuditLog> listCurrentTenant(int size, String userId, String query, String after) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        int pageSize = ListPaging.size(size);
        var cursor = RecordCursor.decode(after);
        if (cursor != null && Long.parseLong(cursor.id()) <= 0) throw new IllegalArgumentException("审计游标无效");
        var entries = auditLogRepository.findCursorPage(tenantId, userId,
                ListPaging.keyword(query), cursor, pageSize + 1);
        return CursorPageResponse.fromFetched(entries, pageSize,
                entry -> new RecordCursor(entry.getCreatedAt(), entry.getId().toString()));
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
