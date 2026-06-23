package com.ai.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * 出站 URL 的 SSRF 防护：校验用户提供的 URL 是否允许服务端发起请求。
 *
 * <p>用户可控的出站请求入口（模型列表拉取、知识库外部同步）必须先经过本校验，
 * 防止借服务端探测或读取内网资源（如 actuator、云元数据端点）。</p>
 *
 * <p>本地 Ollama 与企业内网私有化模型是合法场景，可通过
 * {@code app.security.outbound.allow-private-network=true} 放行私网地址
 * （local 开发环境建议开启，公网生产环境保持关闭）。</p>
 *
 * @author data-agent
 */
@Component
public class OutboundUrlGuard {

    private static final String SCHEME_HTTP = "http";
    private static final String SCHEME_HTTPS = "https";
    /** IPv6 ULA 前缀 fc00::/7 的首字节掩码结果。 */
    private static final int IPV6_ULA_PREFIX = 0xfc;
    private static final int IPV6_ULA_MASK = 0xfe;

    private final boolean allowPrivateNetwork;

    public OutboundUrlGuard(
            @Value("${app.security.outbound.allow-private-network:false}") boolean allowPrivateNetwork) {
        this.allowPrivateNetwork = allowPrivateNetwork;
    }

    /**
     * 校验出站 URL，不允许时抛出异常。
     *
     * <p>注意：校验时与实际请求时各做一次 DNS 解析，存在极小的 DNS 重绑定窗口；
     * 如需彻底防护需在 HTTP 客户端层钉住解析结果，当前风险可接受。</p>
     *
     * @param url 目标地址
     * @throws IllegalArgumentException URL 非法或指向被禁止的网络
     */
    public void assertSafe(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            throw new IllegalArgumentException("地址格式错误: " + url);
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!SCHEME_HTTP.equals(scheme) && !SCHEME_HTTPS.equals(scheme)) {
            throw new IllegalArgumentException("仅支持 http/https 协议: " + url);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("地址缺少主机名: " + url);
        }
        if (allowPrivateNetwork) {
            return;
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("无法解析主机名: " + host);
        }
        for (InetAddress address : addresses) {
            if (isForbidden(address)) {
                throw new IllegalArgumentException(
                        "目标地址指向内网或保留网段，已被安全策略拦截: " + host + " -> " + address.getHostAddress());
            }
        }
    }

    private boolean isForbidden(InetAddress address) {
        if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                || address.isLinkLocalAddress() || address.isAnyLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        // isSiteLocalAddress 不覆盖 IPv6 ULA（fc00::/7），手动补判
        byte[] raw = address.getAddress();
        return raw.length == 16 && (raw[0] & IPV6_ULA_MASK) == IPV6_ULA_PREFIX;
    }
}
