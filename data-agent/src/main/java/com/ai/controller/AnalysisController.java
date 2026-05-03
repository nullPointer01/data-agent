package com.ai.controller;

import com.ai.agent.DataAnalysisAgent;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.security.SecurityContextHelper;
import com.ai.service.SessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/agent/analysis")
public class AnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final DataAnalysisAgent agent;
    private final SessionManager sessionManager;
    private final SecurityContextHelper securityContextHelper;

    public AnalysisController(DataAnalysisAgent agent, SessionManager sessionManager,
                              SecurityContextHelper securityContextHelper) {
        this.agent = agent;
        this.sessionManager = sessionManager;
        this.securityContextHelper = securityContextHelper;
    }

    @PreDestroy
    public void shutdown() {
        sseExecutor.shutdown();
        try {
            if (!sseExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                sseExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            sseExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @PostMapping("/analyze")
    public AnalysisResponse analyze(@RequestBody AnalysisRequest request) {
        log.info("Analysis request from user, question: {}", request.getQuestion());
        return agent.analyze(request);
    }

    @PostMapping(value = "/analyze/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyzeStream(@RequestBody AnalysisRequest request) {
        SseEmitter emitter = new SseEmitter(180_000L);

        emitter.onCompletion(() -> log.debug("SSE stream completed"));
        emitter.onTimeout(() -> log.warn("SSE stream timed out"));

        sseExecutor.execute(() -> {
            try {
                emitter.send(SseEmitter.event().name("start").data(
                        Map.of("message", "开始分析...", "sessionId", request.getSessionId() != null ? request.getSessionId() : "")));

                AnalysisResponse response = agent.analyze(request);

                if (response.getThinkingSteps() != null) {
                    for (AnalysisResponse.ThinkingStep step : response.getThinkingSteps()) {
                        emitter.send(SseEmitter.event().name("thinking").data(objectMapper.writeValueAsString(step)));
                    }
                }

                emitter.send(SseEmitter.event().name("result").data(objectMapper.writeValueAsString(response)));
                emitter.send(SseEmitter.event().name("done").data(""));
                emitter.complete();
            } catch (Exception e) {
                log.error("SSE stream error", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(
                            Map.of("message", "分析过程出错: " + e.getMessage())));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @PostMapping("/session")
    public Map<String, Object> createSession() {
        String sessionId = sessionManager.createSession();
        return Map.of("success", true, "sessionId", sessionId);
    }

    @DeleteMapping("/session/{sessionId}")
    public Map<String, Object> destroySession(@PathVariable String sessionId) {
        sessionManager.destroySession(sessionId);
        return Map.of("success", true, "message", "会话已销毁");
    }

    @GetMapping("/sessions")
    public Map<String, Object> listSessions() {
        try {
            String userId = securityContextHelper.getCurrentUserId();
            var sessions = sessionManager.getUserSessions(userId);
            return Map.of("success", true, "sessions", sessions);
        } catch (Exception e) {
            return Map.of("success", true, "sessions", java.util.List.of());
        }
    }
}
