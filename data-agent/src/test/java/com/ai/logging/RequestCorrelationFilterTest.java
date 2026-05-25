package com.ai.logging;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestCorrelationFilterTest {

    @Test
    void doFilterUsesIncomingRequestIdAndClearsMdc() throws Exception {
        RequestCorrelationFilter filter = new RequestCorrelationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/analysis/analyze");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(RequestCorrelationContext.HEADER_REQUEST_ID, "req-1");
        AtomicReference<String> requestIdInChain = new AtomicReference<>();
        AtomicReference<String> pathInChain = new AtomicReference<>();

        filter.doFilter(request, response, capturingChain(requestIdInChain, pathInChain));

        assertEquals("req-1", response.getHeader(RequestCorrelationContext.HEADER_REQUEST_ID));
        assertEquals("req-1", requestIdInChain.get());
        assertEquals("/api/v1/analysis/analyze", pathInChain.get());
        assertNull(MDC.get(RequestCorrelationContext.MDC_REQUEST_ID));
    }

    @Test
    void doFilterGeneratesRequestIdWhenHeaderIsMissing() throws Exception {
        RequestCorrelationFilter filter = new RequestCorrelationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/analysis/analyze");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdInChain = new AtomicReference<>();

        filter.doFilter(request, response, capturingChain(requestIdInChain, new AtomicReference<>()));

        assertNotNull(response.getHeader(RequestCorrelationContext.HEADER_REQUEST_ID));
        assertEquals(response.getHeader(RequestCorrelationContext.HEADER_REQUEST_ID), requestIdInChain.get());
        assertNull(MDC.get(RequestCorrelationContext.MDC_REQUEST_ID));
    }

    @Test
    void doFilterRegeneratesUnsafeIncomingRequestId() throws Exception {
        RequestCorrelationFilter filter = new RequestCorrelationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/analysis/analyze");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(RequestCorrelationContext.HEADER_REQUEST_ID, "bad request id");
        AtomicReference<String> requestIdInChain = new AtomicReference<>();

        filter.doFilter(request, response, capturingChain(requestIdInChain, new AtomicReference<>()));

        assertNotEquals("bad request id", response.getHeader(RequestCorrelationContext.HEADER_REQUEST_ID));
        assertEquals(response.getHeader(RequestCorrelationContext.HEADER_REQUEST_ID), requestIdInChain.get());
        assertNull(MDC.get(RequestCorrelationContext.MDC_REQUEST_ID));
    }

    private FilterChain capturingChain(AtomicReference<String> requestIdInChain,
            AtomicReference<String> pathInChain) {
        return (request, response) -> {
            requestIdInChain.set(MDC.get(RequestCorrelationContext.MDC_REQUEST_ID));
            pathInChain.set(MDC.get(RequestCorrelationContext.MDC_PATH));
        };
    }
}
