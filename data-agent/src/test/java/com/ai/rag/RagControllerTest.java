package com.ai.rag;

import com.ai.rag.dto.RagHealthResponse;
import com.ai.rag.dto.RagQualityEvaluationResponse;
import com.ai.rag.dto.RagRetrievalTrace;
import com.ai.security.auth.JwtTokenProvider;
import com.ai.security.SecurityContextHelper;
import com.ai.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = RagController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@AutoConfigureMockMvc(addFilters = false)
class RagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RagProperties ragProperties;

    @MockBean
    private RagRetrievalService ragRetrievalService;

    @MockBean
    private RagHealthService ragHealthService;

    @MockBean
    private RagQualityEvaluationService ragQualityEvaluationService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private RateLimitService rateLimitService;

    @MockBean
    private SecurityContextHelper securityContextHelper;

    @Test
    void healthReturnsRagHealth() throws Exception {
        RagHealthResponse health = new RagHealthResponse(true, true, "jpa", true,
                "milvus", true, true, true, true, 100L, 50L, 2000L,
                RagRetrievalTrace.empty(), List.of(), List.of());
        when(ragHealthService.getHealth()).thenReturn(health);

        mockMvc.perform(get("/api/v1/rag/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.fullTextProvider").value("jpa"))
                .andExpect(jsonPath("$.vectorProvider").value("milvus"));
    }

    @Test
    void evaluateReturnsQualityResult() throws Exception {
        RagQualityEvaluationResponse evaluation = new RagQualityEvaluationResponse(true, 0.92D, 1D,
                0.9D, 0.8D, 1D, 1D, 2, 2, 2, 2, List.of("销售", "制度"),
                List.of(), List.of(), List.of());
        when(ragQualityEvaluationService.evaluate(any())).thenReturn(evaluation);

        mockMvc.perform(post("/api/v1/rag/evaluate")
                        .contentType("application/json")
                        .content("""
                                {"query":"销售制度","response":{"context":"资料 [R1] 销售制度","hitCount":1}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.overallScore").value(0.92D))
                .andExpect(jsonPath("$.citationAccuracy").value(0.9D));
    }
}
