package com.ai.mcp;

/**
 * 已经过目录归一和安全校验的模型连接参数。
 *
 * <p>API Key 只供客户端构建和 HTTP 鉴权使用，禁止记录整个对象或将其返回给前端。</p>
 *
 * @author data-agent
 */
public record ResolvedModelEndpoint(
        String providerKey,
        ModelProviderCatalog.Protocol protocol,
        String baseUrl,
        String modelName,
        String apiKey,
        boolean apiKeyRequired,
        boolean modelDiscoverySupported) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String toString() {
        return "ResolvedModelEndpoint[providerKey=" + providerKey
                + ", protocol=" + protocol
                + ", baseUrl=" + baseUrl
                + ", modelName=" + modelName
                + ", apiKey=<redacted>"
                + ", apiKeyRequired=" + apiKeyRequired
                + ", modelDiscoverySupported=" + modelDiscoverySupported + "]";
    }
}
