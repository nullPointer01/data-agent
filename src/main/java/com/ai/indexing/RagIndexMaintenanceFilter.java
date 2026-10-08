package com.ai.indexing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 维护期间只允许认证、来源/任务管理和健康检查，避免读取或修改半更新索引。 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class RagIndexMaintenanceFilter extends OncePerRequestFilter {
    private final RagIndexMaintenance maintenance;
    public RagIndexMaintenanceFilter(RagIndexMaintenance maintenance) { this.maintenance = maintenance; }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean allowed = path.startsWith("/api/v1/auth/") || path.startsWith("/api/v1/rag/indexes/")
                || (request.getMethod().equals("GET") && path.equals("/api/v1/rag/health"));
        if (maintenance.isEnabled() && path.startsWith("/api/v1/") && !allowed) {
            response.setStatus(503);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write("{\"success\":false,\"message\":\"知识索引维护中，请稍后重试\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
