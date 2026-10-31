package com.ai.controller;

import com.ai.service.AuditLogService;
import com.ai.api.CursorPageResponse;
import com.ai.model.AuditLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


/**
 * 审计日志查询接口。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {


    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * 查询当前租户下的审计日志。
     *
     * @param size 每页数量
     * @param userId 可选用户编号
     * @param query 可选搜索内容
     * @param after 上一页游标
     * @return 审计日志列表
     */
    @GetMapping
    public CursorPageResponse<AuditLog> listAuditLogs(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String after) {
        return auditLogService.listCurrentTenant(size, userId, query, after);
    }
}
