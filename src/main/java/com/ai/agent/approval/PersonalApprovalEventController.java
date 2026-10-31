package com.ai.agent.approval;

import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/** 个人会话只订阅本人审批提示，管理员使用独立的租户审批入口。 */
@RestController
public class PersonalApprovalEventController {
    private final ApprovalEventSubscriptions subscriptions;

    public PersonalApprovalEventController(ApprovalEventSubscriptions subscriptions) {
        this.subscriptions = subscriptions;
    }

    /** 订阅本人可读会话的审批变化。 */
    @GetMapping(value = "/api/v1/my/approval-events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<String>>> events(@RequestParam String sessionId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Accel-Buffering", "no")
                .body(subscriptions.open(sessionId, false));
    }
}
