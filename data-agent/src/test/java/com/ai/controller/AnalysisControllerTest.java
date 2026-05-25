package com.ai.controller;

import com.ai.agent.DataAnalysisAgent;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.security.auth.JwtTokenProvider;
import com.ai.security.SecurityContextHelper;
import com.ai.service.AnalysisStreamService;
import com.ai.service.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AnalysisController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@AutoConfigureMockMvc(addFilters = false)
class AnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DataAnalysisAgent dataAnalysisAgent;

    @MockBean
    private AnalysisStreamService analysisStreamService;

    @MockBean
    private SecurityContextHelper securityContextHelper;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private RateLimitService rateLimitService;

    @Test
    void analyzeDelegatesToAgent() throws Exception {
        when(dataAnalysisAgent.analyze(any(AnalysisRequest.class))).thenReturn(AnalysisResponse.ok("ok"));

        mockMvc.perform(post("/api/v1/analysis/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("question", "hello"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result").value("ok"));

        verify(dataAnalysisAgent).analyze(any(AnalysisRequest.class));
    }

    @Test
    void analyzeStreamDelegatesToStreamService() throws Exception {
        when(analysisStreamService.stream(any(AnalysisRequest.class))).thenReturn(new SseEmitter());

        mockMvc.perform(post("/api/v1/analysis/analyze/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("question", "hello"))))
                .andExpect(status().isOk());

        verify(analysisStreamService).stream(any(AnalysisRequest.class));
    }
}
