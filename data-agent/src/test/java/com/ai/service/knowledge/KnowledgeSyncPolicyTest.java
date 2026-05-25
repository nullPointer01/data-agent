package com.ai.service.knowledge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeSyncPolicyTest {

    @Test
    void resolveSyncTypeDefaultsToUrl() {
        KnowledgeSyncPolicy policy = new KnowledgeSyncPolicy();

        String result = policy.resolveSyncType("");

        assertEquals(KnowledgeSyncPolicy.SYNC_TYPE_URL, result);
    }

    @Test
    void resolveSyncTypeRejectsUnsupportedType() {
        KnowledgeSyncPolicy policy = new KnowledgeSyncPolicy();

        assertThrows(IllegalArgumentException.class, () -> policy.resolveSyncType("ftp"));
    }

    @Test
    void resolveCronExpressionDefaultsWhenBlank() {
        KnowledgeSyncPolicy policy = new KnowledgeSyncPolicy();

        String result = policy.resolveCronExpression(null);

        assertEquals("0 0 * * *", result);
    }

    @Test
    void truncateKeepsConfiguredLength() {
        KnowledgeSyncPolicy policy = new KnowledgeSyncPolicy();

        String result = policy.truncate("abcdef", 3);

        assertEquals("abc", result);
    }
}
