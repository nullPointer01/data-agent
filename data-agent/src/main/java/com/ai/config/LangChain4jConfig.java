package com.ai.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j default chat model configuration.
 *
 * @author data-agent
 */
@Configuration
public class LangChain4jConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(LangChain4jConfig.class);

    @Value("${langchain4j.open-ai.api-key}")
    private String apiKey;

    @Value("${langchain4j.open-ai.model-name}")
    private String modelName;

    @Value("${langchain4j.open-ai.base-url}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.temperature:0.7}")
    private Double temperature;

    @Value("${langchain4j.open-ai.timeout:30000}")
    private Integer timeout;

    @PostConstruct
    public void init() {
        // 清除系统代理设置，避免 macOS 系统代理（Clash/VPN 等）干扰内网 API 调用
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
        System.clearProperty("https.proxyHost");
        System.clearProperty("https.proxyPort");
        System.clearProperty("proxyHost");
        System.clearProperty("proxyPort");

        String maskedKey = apiKey != null && apiKey.length() > 8
                ? apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4)
                : "null/empty";
        LOGGER.info("Default model config | baseUrl={} | modelName={} | apiKeyPrefix={} | temperature={}",
                baseUrl, modelName, maskedKey, temperature);
    }

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .temperature(temperature)
                .timeout(Duration.ofMillis(timeout))
                .build();
    }
}
