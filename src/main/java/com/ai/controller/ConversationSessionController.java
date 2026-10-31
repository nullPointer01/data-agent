package com.ai.controller;

import com.ai.conversation.dto.SessionCreateResponse;
import com.ai.conversation.dto.SessionListResponse;
import com.ai.conversation.dto.SessionMessagesResponse;
import com.ai.conversation.dto.SessionMutationResponse;
import com.ai.conversation.dto.SessionRenameRequest;
import com.ai.conversation.ConversationLifecycleService;
import com.ai.conversation.ConversationAccessGuard;
import com.ai.model.ConversationSessionEntity;
import com.ai.security.SecurityContextHelper;
import com.ai.service.SessionManager;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对话会话接口。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/analysis")
public class ConversationSessionController {

    private static final String DESTROYED_MESSAGE = "会话已删除";
    private static final String RENAMED_MESSAGE = "会话已重命名";

    private final SessionManager sessionManager;
    private final SecurityContextHelper securityContextHelper;
    private final ConversationLifecycleService lifecycle;
    private final ConversationAccessGuard accessGuard;

    public ConversationSessionController(SessionManager sessionManager, SecurityContextHelper securityContextHelper,
            ConversationLifecycleService lifecycle, ConversationAccessGuard accessGuard) {
        this.sessionManager = sessionManager;
        this.securityContextHelper = securityContextHelper;
        this.lifecycle = lifecycle;
        this.accessGuard = accessGuard;
    }

    /**
     * 为当前用户创建一个空白对话会话。
     *
     * @return 新会话编号
     */
    @PostMapping("/session")
    public SessionCreateResponse createSession() {
        return new SessionCreateResponse(true, sessionManager.createSession());
    }

    /**
     * 删除当前用户的会话，立即撤销后续执行；内容在保留期后分批清理。
     *
     * @param sessionId 会话编号
     * @return 删除结果
     */
    @DeleteMapping("/session/{sessionId}")
    public SessionMutationResponse destroySession(@PathVariable String sessionId) {
        lifecycle.changeState(sessionId, "DELETED");
        return new SessionMutationResponse(true, DESTROYED_MESSAGE);
    }

    /** 归档保留历史，恢复后才能继续对话。 */
    @PostMapping("/session/{sessionId}/archive")
    public SessionMutationResponse archive(@PathVariable String sessionId) {
        lifecycle.changeState(sessionId, "ARCHIVED");
        return new SessionMutationResponse(true, "会话已归档");
    }

    @PostMapping("/session/{sessionId}/restore")
    public SessionMutationResponse restore(@PathVariable String sessionId) {
        lifecycle.changeState(sessionId, "ACTIVE");
        return new SessionMutationResponse(true, "会话已恢复");
    }

    /** 用于重登恢复选中项，即便它尚未出现在列表首屏。 */
    @GetMapping("/session/{sessionId}")
    public ConversationSessionEntity getSession(@PathVariable String sessionId) {
        return accessGuard.requireReadable(sessionId, securityContextHelper.getCurrentTenantId(),
                securityContextHelper.getCurrentUserId());
    }

    /**
     * 修改当前用户拥有的会话标题。
     *
     * @param sessionId 会话编号
     * @param request 重命名请求
     * @return 修改结果
     */
    @PutMapping("/session/{sessionId}/title")
    public SessionMutationResponse renameSession(@PathVariable String sessionId,
            @Valid @RequestBody SessionRenameRequest request) {
        sessionManager.renameSession(sessionId, request.title());
        return new SessionMutationResponse(true, RENAMED_MESSAGE);
    }

    /**
     * 查询当前用户的会话列表。
     *
     * @return 会话列表
     */
    @GetMapping("/sessions")
    public SessionListResponse listSessions(@RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "5") int limit) {
        return lifecycle.list(status, cursor, limit);
    }

    /**
     * 查询当前用户拥有的指定会话消息。
     *
     * @param sessionId 会话编号
     * @return 会话消息或失败信息
     */
    @GetMapping("/session/{sessionId}/messages")
    public SessionMessagesResponse getSessionMessages(@PathVariable String sessionId,
            @RequestParam(required = false) Long beforeId, @RequestParam(defaultValue = "50") int limit) {
        try {
            return sessionManager.getSessionMessages(sessionId, beforeId, limit);
        } catch (Exception e) {
            return SessionMessagesResponse.fail(e.getMessage());
        }
    }
}
