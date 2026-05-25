package com.ai.memory;

import com.ai.memory.dto.MemoryCaptureRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryCaptureRequestTest {

    @Test
    void normalizeAppliesSafeDefaults() {
        MemoryCaptureRequest request = new MemoryCaptureRequest(
                null, null, null, "session-1", "content", null, null, null, null, 0);

        MemoryCaptureRequest normalized = request.normalize();

        assertEquals(MemoryTier.SHORT_TERM, normalized.tier());
        assertEquals(MemoryType.CONVERSATION, normalized.type());
        assertEquals(MemorySource.SYSTEM_GENERATED, normalized.source());
        assertEquals(3, normalized.importance());
        assertTrue(normalized.metadata().isEmpty());
        assertEquals("content", normalized.effectiveContent());
    }
}
