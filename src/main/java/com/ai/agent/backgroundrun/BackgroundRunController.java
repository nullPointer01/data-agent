package com.ai.agent.backgroundrun;

import com.ai.model.AnalysisRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/** 产品聊天先提交、再订阅；提交重试和订阅重连具有不同语义。 */
@RestController
@RequestMapping("/api/v1/agent-runs")
public class BackgroundRunController {
    private final BackgroundRunStore store;
    private final RunEventStream events;

    public BackgroundRunController(BackgroundRunStore store, RunEventStream events) {
        this.store = store; this.events = events;
    }

    @PostMapping
    public BackgroundRunStore.Submitted submit(@RequestBody Submission submission) {
        return store.submit(submission.request(), submission.requestKey());
    }

    @GetMapping(value="/{runId}/events", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> events(@PathVariable String runId,
            @RequestParam(defaultValue="0") long after, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Accel-Buffering", "no");
        return events.open(runId, after);
    }

    public record Submission(String requestKey, AnalysisRequest request) { }
}
