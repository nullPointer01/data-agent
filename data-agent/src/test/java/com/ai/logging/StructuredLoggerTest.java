package com.ai.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredLoggerTest {

    private static final int MAX_LOG_VALUE_LENGTH = 2048;

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void logEventWritesJsonPayload() throws Exception {
        ListAppender<ILoggingEvent> appender = attachAppender();
        StructuredLogger structuredLogger = new StructuredLogger(new ObjectMapper());

        structuredLogger.logEvent(StructuredLogger.TYPE_AGENT_FEEDBACK, Map.of("rating", "UP"));

        ILoggingEvent event = readLastEvent(appender);
        Map<?, ?> payload = new ObjectMapper().readValue(event.getFormattedMessage(), Map.class);
        assertEquals(Level.INFO, event.getLevel());
        assertEquals(StructuredLogger.TYPE_AGENT_FEEDBACK, payload.get("type"));
        assertEquals("UP", ((Map<?, ?>) payload.get("data")).get("rating"));
    }

    @Test
    void logEventIncludesCorrelationContext() throws Exception {
        ListAppender<ILoggingEvent> appender = attachAppender();
        StructuredLogger structuredLogger = new StructuredLogger(new ObjectMapper());
        MDC.put(RequestCorrelationContext.MDC_REQUEST_ID, "req-1");
        MDC.put(RequestCorrelationContext.MDC_USER_ID, "user-1");
        MDC.put(RequestCorrelationContext.MDC_TENANT_ID, "tenant-1");

        structuredLogger.logEvent(StructuredLogger.TYPE_AGENT_TRACE, Map.of("traceId", "trace-1"));

        ILoggingEvent event = readLastEvent(appender);
        Map<?, ?> payload = new ObjectMapper().readValue(event.getFormattedMessage(), Map.class);
        assertEquals("req-1", payload.get("requestId"));
        assertEquals("user-1", payload.get("userId"));
        assertEquals("tenant-1", payload.get("tenantId"));
    }

    @Test
    void logToolCallTruncatesLongText() throws Exception {
        ListAppender<ILoggingEvent> appender = attachAppender();
        StructuredLogger structuredLogger = new StructuredLogger(new ObjectMapper());
        String longResult = "x".repeat(MAX_LOG_VALUE_LENGTH + 10);

        structuredLogger.logToolCall(null, "calculate", Map.of("expression", "1+1"), longResult, 3L, true);

        ILoggingEvent event = readLastEvent(appender);
        Map<?, ?> payload = new ObjectMapper().readValue(event.getFormattedMessage(), Map.class);
        assertEquals(MAX_LOG_VALUE_LENGTH, ((String) payload.get("result")).length());
    }

    private ListAppender<ILoggingEvent> attachAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(StructuredLogger.class);
        logger.detachAndStopAllAppenders();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
        return appender;
    }

    private ILoggingEvent readLastEvent(ListAppender<ILoggingEvent> appender) {
        assertTrue(!appender.list.isEmpty());
        return appender.list.get(appender.list.size() - 1);
    }
}
