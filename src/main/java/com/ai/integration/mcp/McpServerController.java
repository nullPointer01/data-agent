package com.ai.integration.mcp;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** MCP 连接诊断只开放给管理员；状态查询不触发网络访问，也不返回凭据和地址。 */
@RestController
@RequestMapping("/api/v1/mcp/servers")
@PreAuthorize("hasRole('ADMIN')")
public class McpServerController {
    private final McpToolCatalog catalog;

    public McpServerController(McpToolCatalog catalog) { this.catalog = catalog; }

    /** 返回本节点连接的最近状态；CONNECTED 不代表对远端业务可用性的实时探测。 */
    @GetMapping
    public Map<String, String> statuses() { return catalog.statuses(); }
}
