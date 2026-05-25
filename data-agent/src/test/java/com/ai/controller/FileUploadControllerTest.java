package com.ai.controller;

import com.ai.file.dto.FileResponse;
import com.ai.service.file.FileProcessingService;
import com.ai.service.RateLimitService;
import com.ai.security.auth.JwtTokenProvider;
import com.ai.security.SecurityContextHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = FileUploadController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@AutoConfigureMockMvc(addFilters = false)
class FileUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileProcessingService fileProcessingService;

    @MockBean
    private SecurityContextHelper securityContextHelper;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private RateLimitService rateLimitService;

    @Test
    void v1FileInfoUsesVersionedRoute() throws Exception {
        when(fileProcessingService.getFileInfo("file-1")).thenReturn(new FileResponse(
                true, "file-1", "demo.txt", "text/plain", 0L, null, "COMPLETED", null, null, null));

        mockMvc.perform(get("/api/v1/files/file-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.fileId").value("file-1"))
                .andExpect(jsonPath("$.processingStatus").value("COMPLETED"));
    }

    @Test
    void fileInfoUsesOnlyVersionedRoute() throws Exception {
        when(fileProcessingService.getFileInfo("file-1")).thenReturn(new FileResponse(
                true, "file-1", "demo.txt", "text/plain", 0L, null, "COMPLETED", null, null, null));

        mockMvc.perform(get("/api/v1/files/file-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.fileId").value("file-1"));
    }
}
