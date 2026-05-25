package com.ai.controller;

import com.ai.memory.MemoryGovernanceService;
import com.ai.memory.MemoryTier;
import com.ai.memory.dto.MemoryEntryResponse;
import com.ai.memory.dto.MemoryListResponse;
import com.ai.memory.dto.MemoryMutationResponse;
import com.ai.memory.dto.MemoryStatsResponse;
import com.ai.memory.dto.UserMemoryProfileResponse;
import com.ai.memory.dto.UserMemoryProfileSnapshotResponse;
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
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = MemoryController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@AutoConfigureMockMvc(addFilters = false)
class MemoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MemoryGovernanceService memoryGovernanceService;

    @MockBean
    private SecurityContextHelper securityContextHelper;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private RateLimitService rateLimitService;

    @Test
    void listMemoriesReturnsCurrentUserMemories() throws Exception {
        MemoryEntryResponse memory = new MemoryEntryResponse(
                "m-1", "session-1", MemoryTier.SHORT_TERM, null, null, "summary", 0.8D, 0, null, null);
        when(memoryGovernanceService.listCurrentUserMemories(MemoryTier.SHORT_TERM, 10))
                .thenReturn(new MemoryListResponse(true, List.of(memory), 1L));

        mockMvc.perform(get("/api/v1/memories").param("tier", "SHORT_TERM").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.memories[0].memoryId").value("m-1"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void getProfileReturnsAggregatedMemoryProfile() throws Exception {
        MemoryEntryResponse preference = new MemoryEntryResponse(
                "m-1", null, MemoryTier.LONG_TERM, null, null, "喜欢表格", 1D, 0, null, null);
        UserMemoryProfileSnapshotResponse profile = new UserMemoryProfileSnapshotResponse("张三", "运营",
                "星河电商", "电商", "简洁直接", "表格优先", List.of("运营分析"), List.of("销售分析"),
                List.of("MySQL"), 0.85D);
        when(memoryGovernanceService.getCurrentUserProfile())
                .thenReturn(new UserMemoryProfileResponse(true, profile, List.of(preference), List.of(), List.of(), 1L));

        mockMvc.perform(get("/api/v1/memories/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.profile.displayName").value("张三"))
                .andExpect(jsonPath("$.profile.preferredFormat").value("表格优先"))
                .andExpect(jsonPath("$.preferences[0].content").value("喜欢表格"));
    }

    @Test
    void getStatsReturnsOperationalMemoryMetrics() throws Exception {
        MemoryStatsResponse stats = new MemoryStatsResponse(true, 10L, 1L, 6L, 3L,
                Map.of("SUMMARY", 6L, "PREFERENCE", 2L), 1000L, 320L, 0.32D, 0.68D, 100, 0.1D, 30, 90,
                0.75D, 8L, 12.5D, 31D);
        when(memoryGovernanceService.getCurrentUserStats()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/memories/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.totalMemories").value(10))
                .andExpect(jsonPath("$.shortTermMemoryCount").value(6))
                .andExpect(jsonPath("$.longTermMemoryCount").value(3))
                .andExpect(jsonPath("$.typeCounts.SUMMARY").value(6))
                .andExpect(jsonPath("$.sourceContentChars").value(1000))
                .andExpect(jsonPath("$.storedContentChars").value(320))
                .andExpect(jsonPath("$.compressionRatio").value(0.32D))
                .andExpect(jsonPath("$.storageSavingRatio").value(0.68D))
                .andExpect(jsonPath("$.quotaUsage").value(0.1D))
                .andExpect(jsonPath("$.profileConfidence").value(0.75D))
                .andExpect(jsonPath("$.averageContextBuildMs").value(12.5D));
    }

    @Test
    void deleteMemoryDelegatesToGovernanceService() throws Exception {
        when(memoryGovernanceService.deleteCurrentUserMemory("m-1"))
                .thenReturn(MemoryMutationResponse.success("记忆已删除", 1));

        mockMvc.perform(delete("/api/v1/memories/m-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.affectedCount").value(1));

        verify(memoryGovernanceService).deleteCurrentUserMemory("m-1");
    }
}
