package com.ai.agent.specialist;

import com.ai.memory.MemoryContextPromptFormatter;
import com.ai.memory.MemorySource;
import com.ai.memory.MemoryTier;
import com.ai.memory.MemoryType;
import com.ai.memory.dto.MemoryContext;
import com.ai.memory.dto.MemoryEntrySummary;
import com.ai.memory.dto.UserMemoryProfileSnapshotResponse;
import com.ai.mcp.McpModelService;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.service.connector.DataConnectorService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.ai.agent.AgentPromptComposer;
import com.ai.agent.AgentExecutionRequest;

class DataAgentSpecialistTest {

    @Test
    void executeFailsWhenDatasourceMissing() {
        DataAgentSpecialist specialist = new DataAgentSpecialist(mock(McpModelService.class),
                mock(DataConnectorService.class), promptComposer());
        AgentProfile profile = new AgentProfile();

        AnalysisResponse response = specialist.execute(new AgentExecutionRequest(profile, request(), null));

        assertEquals(false, response.isSuccess());
        assertEquals("Data Agent 未配置数据源", response.getError());
    }

    @Test
    void executeCallsModelWithDatasourcePreview() {
        McpModelService modelService = mock(McpModelService.class);
        DataConnectorService dataConnectorService = mock(DataConnectorService.class);
        when(dataConnectorService.preview("ds-1", 50)).thenReturn("preview");
        when(modelService.callModel(org.mockito.ArgumentMatchers.argThat(prompt -> prompt.contains("preview")
                && prompt.contains("[记忆上下文]")
                && prompt.contains("称呼=张三")), eq(null))).thenReturn("answer");
        DataAgentSpecialist specialist = new DataAgentSpecialist(modelService, dataConnectorService,
                promptComposer());
        AgentProfile profile = new AgentProfile();
        profile.setDatasourceId("ds-1");

        AnalysisResponse response = specialist.execute(new AgentExecutionRequest(profile, request(), null, null,
                memoryContext()));

        assertEquals("answer", response.getResult());
    }

    private AnalysisRequest request() {
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion("分析数据");
        return request;
    }

    private AgentPromptComposer promptComposer() {
        return new AgentPromptComposer(new MemoryContextPromptFormatter());
    }

    private MemoryContext memoryContext() {
        return new MemoryContext(
                "",
                List.of(new MemoryEntrySummary("m-1", MemoryTier.SHORT_TERM, MemoryType.SUMMARY,
                        MemorySource.SYSTEM_GENERATED, "用户关注复购率", 0.8D, null)),
                "",
                new UserMemoryProfileSnapshotResponse("张三", null, null, null, null, null,
                        List.of(), List.of(), List.of(), 0.8D));
    }
}
