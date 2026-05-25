package com.ai.service.knowledge;

import com.ai.exception.KnowledgeSyncException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Fetches external content for knowledge synchronization.
 *
 * @author data-agent
 */
@Component
public class KnowledgeSyncContentFetcher {

    private static final int CONNECT_TIMEOUT_SECONDS = 15;
    private static final int REQUEST_TIMEOUT_SECONDS = 30;
    private static final int HTTP_SUCCESS_MIN = 200;
    private static final int HTTP_SUCCESS_MAX_EXCLUSIVE = 300;
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String USER_AGENT_VALUE = "DataAgent/1.0";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

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
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .header(USER_AGENT_HEADER, USER_AGENT_VALUE)
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < HTTP_SUCCESS_MIN || response.statusCode() >= HTTP_SUCCESS_MAX_EXCLUSIVE) {
                throw new KnowledgeSyncException("HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IllegalArgumentException e) {
            throw new KnowledgeSyncException("同步地址格式错误", e);
        } catch (HttpTimeoutException e) {
            throw new KnowledgeSyncException("同步请求超时", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KnowledgeSyncException("同步请求被中断", e);
        } catch (Exception e) {
            throw new KnowledgeSyncException("同步请求失败: " + e.getMessage(), e);
        }
    }

    private String fetchGitRaw(String gitUrl) {
        String rawUrl = gitUrl
                .replace("github.com", "raw.githubusercontent.com")
                .replace("/blob/", "/");
        return fetchUrl(rawUrl);
    }
}
