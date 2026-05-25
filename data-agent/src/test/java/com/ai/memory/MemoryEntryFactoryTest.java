package com.ai.memory;

import com.ai.memory.dto.MemoryCaptureRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MemoryEntryFactoryTest {

    @Test
    void createBuildsShortTermEntryWithThirtyDayExpiry() {
        MemoryProperties properties = new MemoryProperties();
        MemoryEntryFactory factory = new MemoryEntryFactory(new MemoryJsonCodec(new ObjectMapper()), properties);
        MemoryCaptureRequest request = new MemoryCaptureRequest(
                MemoryTier.SHORT_TERM,
                MemoryType.SUMMARY,
                MemorySource.AGENT_EXTRACTED,
                "session-1",
                "original",
                "summary",
                Map.of("importance", 4),
                List.of("张三"),
                List.of("电商"),
                4);

        MemoryEntry entry = factory.create("tenant-1", "user-1", request);

        assertEquals("tenant-1", entry.getTenantId());
        assertEquals("user-1", entry.getUserId());
        assertEquals(MemoryTier.SHORT_TERM, entry.getTier());
        assertNull(entry.getContent());
        assertEquals("summary", entry.getCompressedContent());
        assertEquals("original".length(), entry.getSourceContentLength());
        assertEquals("summary".length(), entry.getStoredContentLength());
        assertEquals(0.8D, entry.getDecayWeight());
        assertNotNull(entry.getExpiresAt());
    }

    @Test
    void createLeavesLongTermExpiryEmpty() {
        MemoryEntryFactory factory = new MemoryEntryFactory(new MemoryJsonCodec(new ObjectMapper()),
                new MemoryProperties());
        MemoryCaptureRequest request = new MemoryCaptureRequest(
                MemoryTier.LONG_TERM,
                MemoryType.PREFERENCE,
                MemorySource.USER_EXPLICIT,
                null,
                "用户喜欢图表",
                null,
                Map.of(),
                List.of(),
                List.of("偏好"),
                5);

        MemoryEntry entry = factory.create("tenant-1", "user-1", request);

        assertEquals(MemoryTier.LONG_TERM, entry.getTier());
        assertEquals("用户喜欢图表", entry.getContent());
        assertEquals("用户喜欢图表", entry.getCompressedContent());
        assertNull(entry.getExpiresAt());
        assertEquals(1D, entry.getDecayWeight());
    }

    @Test
    void createUsesConfiguredShortTermRetentionDays() {
        MemoryProperties properties = new MemoryProperties();
        properties.setShortTermRetentionDays(7);
        MemoryEntryFactory factory = new MemoryEntryFactory(new MemoryJsonCodec(new ObjectMapper()), properties);
        MemoryCaptureRequest request = new MemoryCaptureRequest(
                MemoryTier.SHORT_TERM,
                MemoryType.SUMMARY,
                MemorySource.SYSTEM_GENERATED,
                null,
                "original",
                "summary",
                Map.of(),
                List.of(),
                List.of(),
                3);

        MemoryEntry entry = factory.create("tenant-1", "user-1", request);

        assertNotNull(entry.getExpiresAt());
        long days = java.time.temporal.ChronoUnit.DAYS.between(
                java.time.LocalDate.now(), entry.getExpiresAt().toLocalDate());
        assertEquals(7L, days);
    }
}
