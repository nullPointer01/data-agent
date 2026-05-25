package com.ai.mcp;

import com.ai.model.ModelConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelHttpClientTest {

    @Test
    void resolveChatCompletionsUrlNormalizesKimiCodeBaseUrl() {
        ModelHttpClient client = newClient();
        ModelConfig config = new ModelConfig();
        config.setProvider("kimi");
        config.setBaseUrl("https://api.kimi.com/coding/");

        String url = (String) ReflectionTestUtils.invokeMethod(client, "resolveChatCompletionsUrl", config);

        assertEquals("https://api.kimi.com/coding/v1/chat/completions", url);
    }

    @Test
    void resolveModelNameMapsKimiCodeAliasToOpenAiCompatibleModel() {
        ModelHttpClient client = newClient();
        ModelConfig config = new ModelConfig();
        config.setProvider("kimi");
        config.setBaseUrl("https://api.kimi.com/coding/");
        config.setModelName("kimi-2.6");

        String modelName = (String) ReflectionTestUtils.invokeMethod(client, "resolveModelName", config);

        assertEquals("kimi-for-coding", modelName);
    }

    private ModelHttpClient newClient() {
        return new ModelHttpClient(new ObjectMapper(), "sk-test", "gpt-test",
                "https://api.openai.com/v1", 0.7, 30_000);
    }
}
