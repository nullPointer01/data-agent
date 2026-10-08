package com.ai.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** MVC 原生支持 Flux SSE；配置网络写入池与超时，无需把阻塞式 JPA 系统切换为 WebFlux。 */
@Configuration
public class MvcStreamingConfig implements WebMvcConfigurer {
    private final AsyncTaskExecutor executor;
    private final long timeoutMs;

    public MvcStreamingConfig(@Qualifier("mvcStreamingExecutor") AsyncTaskExecutor executor,
            @Value("${app.sse.timeout-ms:600000}") long timeoutMs) {
        this.executor = executor;
        this.timeoutMs = timeoutMs;
    }

    /** 让 Flux 的绝对时限先触发并取消 Run，再由 Servlet 超时兜底清理连接。 */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(executor).setDefaultTimeout(Math.addExact(timeoutMs, 1000L));
    }

    /** SSE 的 data 是 JSON 字符串；MVC 分段写入使用字符串转换器，显式保证中文按 UTF-8 编码。 */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.stream().filter(StringHttpMessageConverter.class::isInstance)
                .map(StringHttpMessageConverter.class::cast)
                .forEach(converter -> converter.setDefaultCharset(StandardCharsets.UTF_8));
    }
}
