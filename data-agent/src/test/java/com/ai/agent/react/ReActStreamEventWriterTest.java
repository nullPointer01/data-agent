package com.ai.agent.react;

import com.ai.rag.dto.RagCitation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReActStreamEventWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void emitToolCallSerializesExpectedFields() throws Exception {
        ReActStreamEventWriter writer = new ReActStreamEventWriter(objectMapper);
        List<String> events = new ArrayList<>();

        writer.emitToolCall(events::add, "calculate", "42");

        JsonNode event = objectMapper.readTree(events.get(0));
        assertEquals("tool_call", event.path("type").asText());
        assertEquals("calculate", event.path("toolName").asText());
        assertEquals("42", event.path("result").asText());
    }

    @Test
    void emitDoneUsesEmptySessionWhenSessionIdIsNull() throws Exception {
        ReActStreamEventWriter writer = new ReActStreamEventWriter(objectMapper);
        List<String> events = new ArrayList<>();

        writer.emitDone(events::add, null);

        JsonNode event = objectMapper.readTree(events.get(0));
        assertEquals("done", event.path("type").asText());
        assertEquals("", event.path("sessionId").asText());
        assertEquals("", event.path("traceId").asText());
    }

    @Test
    void emitDoneSerializesTraceId() throws Exception {
        ReActStreamEventWriter writer = new ReActStreamEventWriter(objectMapper);
        List<String> events = new ArrayList<>();

        writer.emitDone(events::add, "session-1", "trace-1");

        JsonNode event = objectMapper.readTree(events.get(0));
        assertEquals("done", event.path("type").asText());
        assertEquals("session-1", event.path("sessionId").asText());
        assertEquals("trace-1", event.path("traceId").asText());
    }

    @Test
    void emitRagContextSerializesCitations() throws Exception {
        ReActStreamEventWriter writer = new ReActStreamEventWriter(objectMapper);
        List<String> events = new ArrayList<>();

        writer.emitRagContext(events::add, 1,
                List.of(new RagCitation("R1", "knowledge", "knowledge-1", "chunk-1", 0.91D, "客户流失原因")));

        JsonNode event = objectMapper.readTree(events.get(0));
        assertEquals("rag_context", event.path("type").asText());
        assertEquals(1, event.path("hitCount").asInt());
        assertEquals("R1", event.path("citations").get(0).path("referenceId").asText());
        assertEquals("knowledge-1", event.path("citations").get(0).path("sourceId").asText());
    }

    @Test
    void emitReflectionSerializesExpectedFields() throws Exception {
        ReActStreamEventWriter writer = new ReActStreamEventWriter(objectMapper);
        List<String> events = new ArrayList<>();

        writer.emitReflection(events::add, "需要调整策略");

        JsonNode event = objectMapper.readTree(events.get(0));
        assertEquals("reflection", event.path("type").asText());
        assertEquals("需要调整策略", event.path("content").asText());
    }

    @Test
    void emitExecutionPlanSerializesExpectedFields() throws Exception {
        ReActStreamEventWriter writer = new ReActStreamEventWriter(objectMapper);
        List<String> events = new ArrayList<>();

        writer.emitExecutionPlan(events::add, "阶段 1: 检索知识库");

        JsonNode event = objectMapper.readTree(events.get(0));
        assertEquals("execution_plan", event.path("type").asText());
        assertEquals("执行计划", event.path("title").asText());
        assertEquals("阶段 1: 检索知识库", event.path("content").asText());
    }

    @Test
    void emitParallelPrecheckSerializesExpectedFields() throws Exception {
        ReActStreamEventWriter writer = new ReActStreamEventWriter(objectMapper);
        List<String> events = new ArrayList<>();

        writer.emitParallelPrecheck(events::add, "searchKnowledge 成功");

        JsonNode event = objectMapper.readTree(events.get(0));
        assertEquals("parallel_precheck", event.path("type").asText());
        assertEquals("并行预检", event.path("title").asText());
        assertEquals("searchKnowledge 成功", event.path("content").asText());
    }

    @Test
    void emitOrchestrationSerializesExpectedFields() throws Exception {
        ReActStreamEventWriter writer = new ReActStreamEventWriter(objectMapper);
        List<String> events = new ArrayList<>();

        writer.emitOrchestration(events::add, "编排决策", "选择数据专家");

        JsonNode event = objectMapper.readTree(events.get(0));
        assertEquals("orchestration", event.path("type").asText());
        assertEquals("编排决策", event.path("title").asText());
        assertEquals("选择数据专家", event.path("content").asText());
    }
}
