package com.ai.mcp;

import com.ai.logging.StructuredLogger;
import com.ai.model.ModelConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpModelServiceTest {

    @Test
    void callModelReturnsQuotaMessageWithoutInvokingModel() {
        TokenQuotaGuard quotaGuard = mock(TokenQuotaGuard.class);
        ModelHttpClient modelHttpClient = mock(ModelHttpClient.class);
        McpModelService service = newService(modelHttpClient, quotaGuard,
                mock(ModelClientRegistry.class), mock(TokenMonitor.class), mock(TokenUsageRecorder.class));
        when(quotaGuard.checkCurrentUserQuota())
                .thenReturn(TokenQuotaGuard.QuotaCheckResult.deny("额度已用完"));

        String result = service.callModel("hello");

        assertEquals("额度已用完", result);
        verify(modelHttpClient, never()).defaultConfig();
    }

    @Test
    void callModelRecordsDefaultModelTokenUsage() throws Exception {
        TokenQuotaGuard quotaGuard = mock(TokenQuotaGuard.class);
        TokenMonitor tokenMonitor = mock(TokenMonitor.class);
        TokenUsageRecorder tokenUsageRecorder = mock(TokenUsageRecorder.class);
        ModelClientRegistry modelClientRegistry = mock(ModelClientRegistry.class);
        ModelHttpClient modelHttpClient = mock(ModelHttpClient.class);
        ModelRetryExecutor retryExecutor = new ModelRetryExecutor(delayMs -> {
        });
        ModelConfig defaultConfig = new ModelConfig();

        when(quotaGuard.checkCurrentUserQuota()).thenReturn(TokenQuotaGuard.QuotaCheckResult.allow());
        when(modelClientRegistry.isDefaultModel(null)).thenReturn(true);
        when(modelClientRegistry.modelKey(null)).thenReturn("default");
        when(modelHttpClient.defaultConfig()).thenReturn(defaultConfig);
        when(modelClientRegistry.resolveHttpConfig(null, defaultConfig)).thenReturn(defaultConfig);
        when(modelHttpClient.call("hello", defaultConfig)).thenReturn("world");
        when(tokenMonitor.estimateTokens("hello")).thenReturn(2L);
        when(tokenMonitor.estimateTokens("world")).thenReturn(3L);

        StructuredLogger structuredLogger = mock(StructuredLogger.class);
        McpModelService service = new McpModelService(mock(McpContextManager.class), tokenMonitor,
                retryExecutor, modelHttpClient, tokenUsageRecorder, modelClientRegistry, quotaGuard, structuredLogger);

        String result = service.callModel("hello");

        assertEquals("world", result);
        verify(tokenMonitor).recordModelTokenUsage("default", 5L);
        verify(tokenUsageRecorder).record(null, null, 2L, 3L, 5L, null);
        verify(structuredLogger).logLlmCall(isNull(), eq("default"), eq(2L), eq(3L), anyLong(), eq(true), isNull());
    }

    @Test
    void callModelStreamingForDefaultModelRecordsNullModelId() throws Exception {
        TokenQuotaGuard quotaGuard = mock(TokenQuotaGuard.class);
        TokenMonitor tokenMonitor = mock(TokenMonitor.class);
        TokenUsageRecorder tokenUsageRecorder = mock(TokenUsageRecorder.class);
        ModelClientRegistry modelClientRegistry = mock(ModelClientRegistry.class);
        ModelHttpClient modelHttpClient = mock(ModelHttpClient.class);
        ModelConfig defaultConfig = new ModelConfig();
        List<String> streamed = new ArrayList<>();

        when(quotaGuard.checkCurrentUserQuota()).thenReturn(TokenQuotaGuard.QuotaCheckResult.allow());
        when(modelClientRegistry.modelKey(null)).thenReturn("default");
        when(modelClientRegistry.isDefaultModel(null)).thenReturn(true);
        when(modelHttpClient.defaultConfig()).thenReturn(defaultConfig);
        when(modelClientRegistry.resolveHttpConfig(null, defaultConfig)).thenReturn(defaultConfig);
        when(modelHttpClient.callStreaming(eq("hello"), eq(defaultConfig), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    invocation.<java.util.function.Consumer<String>>getArgument(2).accept("wo");
                    invocation.<java.util.function.Consumer<String>>getArgument(2).accept("rld");
                    return "world";
                });
        when(tokenMonitor.estimateTokens("hello")).thenReturn(2L);
        when(tokenMonitor.estimateTokens("world")).thenReturn(3L);

        McpModelService service = newService(modelHttpClient, quotaGuard,
                modelClientRegistry, tokenMonitor, tokenUsageRecorder);

        String result = service.callModelStreaming("hello", null, streamed::add);

        assertEquals("world", result);
        assertEquals(List.of("wo", "rld"), streamed);
        verify(tokenMonitor).recordModelTokenUsage("default", 5L);
        verify(tokenUsageRecorder).record(null, null, 2L, 3L, 5L, null);
    }

    @Test
    void callModelUsesHttpClientForKimiModelConfig() throws Exception {
        TokenQuotaGuard quotaGuard = mock(TokenQuotaGuard.class);
        TokenMonitor tokenMonitor = mock(TokenMonitor.class);
        TokenUsageRecorder tokenUsageRecorder = mock(TokenUsageRecorder.class);
        ModelClientRegistry modelClientRegistry = mock(ModelClientRegistry.class);
        ModelHttpClient modelHttpClient = mock(ModelHttpClient.class);
        ModelRetryExecutor retryExecutor = new ModelRetryExecutor(delayMs -> {
        });
        ModelConfig defaultConfig = new ModelConfig();
        ModelConfig kimiConfig = new ModelConfig();
        kimiConfig.setProvider("kimi");
        kimiConfig.setBaseUrl("https://api.moonshot.cn/v1");
        kimiConfig.setModelName("kimi-k2.6");

        when(quotaGuard.checkCurrentUserQuota()).thenReturn(TokenQuotaGuard.QuotaCheckResult.allow());
        when(modelClientRegistry.isDefaultModel("kimi-model")).thenReturn(false);
        when(modelClientRegistry.modelKey("kimi-model")).thenReturn("kimi-model");
        when(modelHttpClient.defaultConfig()).thenReturn(defaultConfig);
        when(modelClientRegistry.resolveHttpConfig("kimi-model", defaultConfig)).thenReturn(kimiConfig);
        when(modelClientRegistry.isHttpOnlyModel(kimiConfig)).thenReturn(true);
        when(modelHttpClient.call("hello", kimiConfig)).thenReturn("world");
        when(tokenMonitor.estimateTokens("hello")).thenReturn(2L);
        when(tokenMonitor.estimateTokens("world")).thenReturn(3L);

        McpModelService service = new McpModelService(mock(McpContextManager.class), tokenMonitor,
                retryExecutor, modelHttpClient, tokenUsageRecorder, modelClientRegistry, quotaGuard,
                mock(StructuredLogger.class));

        String result = service.callModel("hello", "kimi-model");

        assertEquals("world", result);
        verify(modelClientRegistry, never()).getChatModel("kimi-model");
        verify(modelHttpClient).call("hello", kimiConfig);
        verify(tokenUsageRecorder).record("kimi-model", null, 2L, 3L, 5L, null);
    }

    private McpModelService newService(ModelHttpClient modelHttpClient,
            TokenQuotaGuard quotaGuard,
            ModelClientRegistry modelClientRegistry,
            TokenMonitor tokenMonitor,
            TokenUsageRecorder tokenUsageRecorder) {
        return new McpModelService(mock(McpContextManager.class), tokenMonitor,
                mock(ModelRetryExecutor.class), modelHttpClient, tokenUsageRecorder, modelClientRegistry, quotaGuard,
                mock(StructuredLogger.class));
    }
}
