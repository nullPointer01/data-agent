package com.ai.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryWorthinessEvaluatorTest {

    @Test
    void shouldStoreConversationRequiresUserOrAssistantContent() {
        MemoryWorthinessEvaluator evaluator = new MemoryWorthinessEvaluator();

        assertFalse(evaluator.shouldStoreConversation(" ", null));
        assertTrue(evaluator.shouldStoreConversation("分析销售数据", null));
        assertTrue(evaluator.shouldStoreConversation(null, "已完成分析"));
    }

    @Test
    void hasExplicitMemoryIntentMatchesUserMemoryStatements() {
        MemoryWorthinessEvaluator evaluator = new MemoryWorthinessEvaluator();

        assertTrue(evaluator.hasExplicitMemoryIntent("请记住，我喜欢图表"));
        assertFalse(evaluator.hasExplicitMemoryIntent("分析一下销售数据"));
    }

    @Test
    void classifyExplicitMemoryUsesSemanticType() {
        MemoryWorthinessEvaluator evaluator = new MemoryWorthinessEvaluator();

        assertEquals(MemoryType.PREFERENCE, evaluator.classifyExplicitMemory("我偏好表格"));
        assertEquals(MemoryType.ENTITY, evaluator.classifyExplicitMemory("我是电商运营"));
        assertEquals(MemoryType.CONCLUSION, evaluator.classifyExplicitMemory("请记住这个结论"));
    }
}
