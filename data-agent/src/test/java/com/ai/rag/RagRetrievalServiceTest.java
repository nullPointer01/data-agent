package com.ai.rag;

import com.ai.rag.dto.RagContextResponse;
import com.ai.security.SecurityContextHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RagRetrievalServiceTest {

    @Test
    void retrieveDelegatesToPipelineWithCurrentTenant() {
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        EnhancedRagPipeline enhancedRagPipeline = mock(EnhancedRagPipeline.class);
        RagRetrievalService service = new RagRetrievalService(securityContextHelper, enhancedRagPipeline);
        RagContextResponse expected = new RagContextResponse("context", 1);
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-1");
        when(enhancedRagPipeline.execute("查询客户流失原因", "tenant-1")).thenReturn(expected);

        RagContextResponse response = service.retrieve("查询客户流失原因");

        assertSame(expected, response);
        verify(enhancedRagPipeline).execute("查询客户流失原因", "tenant-1");
    }

    @Test
    void retrieveSkipsPipelineWhenTenantMissing() {
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        EnhancedRagPipeline enhancedRagPipeline = mock(EnhancedRagPipeline.class);
        RagRetrievalService service = new RagRetrievalService(securityContextHelper, enhancedRagPipeline);

        RagContextResponse response = service.retrieve("查询客户流失原因");

        assertTrue(response.getContext().isBlank());
        verifyNoInteractions(enhancedRagPipeline);
    }
}
