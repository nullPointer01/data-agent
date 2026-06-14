package com.ai.service.knowledge;

import com.ai.exception.KnowledgeSyncException;
import com.ai.security.OutboundUrlGuard;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * 为知识同步获取外部内容。
 *
 * @author data-agent
 */
@Component
public class KnowledgeSyncContentFetcher {

    private static final int CONNECT_TIMEOUT_SECONDS = 15;
    private static final int REQUEST_TIMEOUT_SECONDS = 30;
    private static final int HTTP_SUCCESS_MIN = 200;
    private static final int HTTP_SUCCESS_MAX_EXCLUSIVE = 300;
    private static final int HTTP_REDIRECT_MIN = 300;
    private static final int HTTP_REDIRECT_MAX_EXCLUSIVE = 400;
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String USER_AGENT_VALUE = "DataAgent/1.0";

    private final OutboundUrlGuard outboundUrlGuard;

    // 禁止自动跟随重定向：302 跳转到内网地址是绕过 SSRF 校验的常见手法
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public KnowledgeSyncContentFetcher(OutboundUrlGuard outboundUrlGuard) {
        this.outboundUrlGuard = outboundUrlGuard;
    }

    /**
     * Fetches content according to sync type.
     *
     * @param syncType sync type
     * @param sourceUrl source URL
     * @return fetched content
     */
    public String fetch(String syncType, String sourceUrl) {
        if (KnowledgeSyncPolicy.SYNC_TYPE_URL.equals(syncType)) {
            return fetchUrl(sourceUrl);
        }
        if (KnowledgeSyncPolicy.SYNC_TYPE_GIT.equals(syncType)) {
            return fetchGitRaw(sourceUrl);
        }
        throw new IllegalArgumentException("不支持的同步类型: " + syncType);
    }

    private String fetchUrl(String url) {
        // 同步地址由用户配置，请求前必须做 SSRF 校验
        outboundUrlGuard.assertSafe(url);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .header(USER_AGENT_HEADER, USER_AGENT_VALUE)
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (isRedirect(response.statusCode())) {
                throw new KnowledgeSyncException("目标地址发生重定向（HTTP " + response.statusCode()
                        + "），请直接配置最终地址");
            }
            if (response.statusCode() < HTTP_SUCCESS_MIN || response.statusCode() >= HTTP_SUCCESS_MAX_EXCLUSIVE) {
                throw new KnowledgeSyncException("HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IllegalArgumentException e) {
            throw new KnowledgeSyncException("同步地址不可用: " + e.getMessage(), e);
        } catch (HttpTimeoutException e) {
            throw new KnowledgeSyncException("同步请求超时", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KnowledgeSyncException("同步请求被中断", e);
        } catch (KnowledgeSyncException e) {
            throw e;
        } catch (Exception e) {
            throw new KnowledgeSyncException("同步请求失败: " + e.getMessage(), e);
        }
    }

    private boolean isRedirect(int statusCode) {
        return statusCode >= HTTP_REDIRECT_MIN && statusCode < HTTP_REDIRECT_MAX_EXCLUSIVE;
    }

    private String fetchGitRaw(String gitUrl) {
        String rawUrl = gitUrl
                .replace("github.com", "raw.githubusercontent.com")
                .replace("/blob/", "/");
        return fetchUrl(rawUrl);
    }
}
