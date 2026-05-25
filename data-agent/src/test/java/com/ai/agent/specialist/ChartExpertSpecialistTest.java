package com.ai.agent.specialist;

import com.ai.memory.MemoryContextPromptFormatter;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.ai.agent.AgentType;
import com.ai.agent.tool.AgentChartToolService;
import com.ai.agent.AgentExecutionRequest;
import com.ai.agent.AgentPromptComposer;

class ChartExpertSpecialistTest {

    @Test
    void executeGeneratesChartPayload() {
        AgentChartToolService chartToolService = mock(AgentChartToolService.class);
        when(chartToolService.generateChart(eq("line"), contains("sales"), eq("生成销售折线图")))
                .thenReturn("CHART:{\"series\":[]}");
        ChartExpertSpecialist specialist = new ChartExpertSpecialist(
                chartToolService,
                new AgentPromptComposer(new MemoryContextPromptFormatter()));

        AnalysisResponse response = specialist.execute(new AgentExecutionRequest(
                profile(), request("生成销售折线图"), "sales"));

        assertTrue(response.isSuccess());
        assertEquals("CHART:{\"series\":[]}", response.getResult());
        assertEquals("line", response.getExecutionMetadata().get("chartType"));
    }

    private AgentProfile profile() {
        AgentProfile profile = new AgentProfile();
        profile.setName("图表专家");
        profile.setType(AgentType.CHART);
        return profile;
    }

    private AnalysisRequest request(String question) {
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion(question);
        return request;
    }
}
