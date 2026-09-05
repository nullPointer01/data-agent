package com.ai.controller;

import com.ai.service.AuditLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 审计日志查询接口。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private static final int DEFAULT_LIMIT = 50;
    private static final String KEY_SUCCESS = "success";
    private static final String KEY_LOGS = "logs";

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * 查询当前租户下的审计日志。
     *
     * @param limit 返回条数
     * @param userId 可选用户编号
     * @return 审计日志列表
     */
    @GetMapping
    public Map<String, Object> listAuditLogs(
            @RequestParam(value = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit,
            @RequestParam(value = "userId", required = false) String userId) {
        return Map.of(KEY_SUCCESS, true, KEY_LOGS, auditLogService.listCurrentTenant(limit, userId));
    }
}
