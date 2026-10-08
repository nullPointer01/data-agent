package com.ai.integration.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 标准 MCP 客户端的部署配置；凭据只用于指定服务，不接受模型生成的连接参数。 */
@ConfigurationProperties(prefix = "app.mcp")
public record McpIntegrationProperties(Map<String, Server> servers) {
    public McpIntegrationProperties {
        servers = servers == null ? Map.of() : Map.copyOf(servers);
        if (servers.size() > 8) throw new IllegalArgumentException("最多配置 8 个 MCP 服务");
        servers.keySet().forEach(McpIntegrationProperties::requireId);
    }

    /** 服务共享凭据限定到显式租户；默认不暴露任何远端工具。 */
    public record Server(boolean enabled, String url, String authorization, Set<String> allowedTenants,
            Duration discoveryTimeout, Duration toolTimeout, Duration reconnectCooldown, Map<String, Tool> tools) {
        public Server {
            authorization = authorization == null ? "" : authorization;
            allowedTenants = allowedTenants == null ? Set.of() : Set.copyOf(allowedTenants);
            tools = tools == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(tools));
            discoveryTimeout = duration(discoveryTimeout, Duration.ofSeconds(3), 30);
            toolTimeout = duration(toolTimeout, Duration.ofSeconds(20), 120);
            reconnectCooldown = duration(reconnectCooldown, Duration.ofSeconds(30), 300);
            if (tools.size() > 32) throw new IllegalArgumentException("每个 MCP 服务最多映射 32 个工具");
            tools.keySet().forEach(McpIntegrationProperties::requireId);
            if (enabled) {
                if (url == null || url.isBlank()) throw new IllegalArgumentException("启用 MCP 必须提供 URL");
                URI endpoint = URI.create(url);
                if (endpoint.getHost() == null || endpoint.getRawUserInfo() != null || endpoint.getRawQuery() != null
                        || endpoint.getRawFragment() != null
                        || !("http".equals(endpoint.getScheme()) || "https".equals(endpoint.getScheme()))) {
                    throw new IllegalArgumentException("MCP URL 必须是无凭据、查询参数和片段的 HTTP(S) 地址");
                }
                if (authorization.contains("\r") || authorization.contains("\n")) {
                    throw new IllegalArgumentException("MCP Authorization 不能包含换行");
                }
                if (!authorization.isBlank() && !"https".equals(endpoint.getScheme())
                        && !Set.of("localhost", "127.0.0.1", "[::1]").contains(endpoint.getHost())) {
                    throw new IllegalArgumentException("远程 MCP 凭据只允许通过 HTTPS 发送");
                }
                if (allowedTenants.isEmpty() || allowedTenants.stream().anyMatch(t -> t.isBlank() || "*".equals(t))) {
                    throw new IllegalArgumentException("MCP 服务必须指定精确 allowed-tenants，禁止通配租户");
                }
                if (tools.isEmpty()) throw new IllegalArgumentException("启用 MCP 必须配置精确工具映射");
            }
        }

        /** 配置可能含凭据，禁止默认 record.toString 将它打印进诊断日志。 */
        @Override public String toString() { return "McpServer[enabled=" + enabled + ", tools=" + tools.size() + "]"; }
    }

    /** 风险合同由部署者确认，远端只读提示不能替代授权；本阶段明确不支持写工具。 */
    public record Tool(String remoteName, String description, boolean readOnly, String requiredPermission) {
        public Tool {
            if (remoteName == null || remoteName.isBlank() || remoteName.length() > 128) {
                throw new IllegalArgumentException("MCP remote-name 必须有效且不超过 128 字符");
            }
            if (description == null || description.isBlank() || description.length() > 1000) {
                throw new IllegalArgumentException("MCP 工具需要不超过 1000 字符的本地用途描述");
            }
            if (!readOnly) throw new IllegalArgumentException("当前 MCP 接入只允许显式确认的只读工具");
            if (requiredPermission == null || requiredPermission.isBlank()) {
                throw new IllegalArgumentException("MCP 工具必须配置 required-permission");
            }
        }
    }

    /** 稳定模型工具名称；显式限制名称总长，避免不同服务器间碰撞或厂商拒绝。 */
    public static String toolName(String server, String alias) {
        requireId(server);
        requireId(alias);
        String name = "mcp_" + server + "_" + alias;
        if (name.length() > 64) throw new IllegalArgumentException("MCP 映射后的工具名超过 64 字符");
        return name;
    }

    private static void requireId(String id) {
        if (id == null || !id.matches("[a-z][a-z0-9-]{0,27}")) {
            throw new IllegalArgumentException("MCP 服务名/别名须以小写字母开头，只含小写字母、数字和短横线，最长 28 字符");
        }
    }

    private static Duration duration(Duration value, Duration fallback, int maxSeconds) {
        Duration result = value == null ? fallback : value;
        if (result.toMillis() < 1 || result.compareTo(Duration.ofSeconds(maxSeconds)) > 0) {
            throw new IllegalArgumentException("MCP 超时/冷却必须为正数且不超过 " + maxSeconds + " 秒");
        }
        return result;
    }
}
