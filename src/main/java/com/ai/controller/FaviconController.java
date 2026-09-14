package com.ai.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 处理浏览器图标探测而不记录丢失的静态资源错误。
 *
 * @author data-agent
 */
@RestController
public class FaviconController {

    private static final Duration FAVICON_CACHE_DURATION = Duration.ofDays(1);

    /**
     * 响应浏览器的站点图标探测，并缓存空响应以避免重复请求。
     *
     * @return 可缓存的无内容响应
     */
    @GetMapping("/favicon.ico")
    public ResponseEntity<Void> favicon() {
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.maxAge(FAVICON_CACHE_DURATION).cachePublic())
                .build();
    }
}
