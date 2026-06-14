package com.ai.conversation.dto;

import com.ai.model.ConversationMessage;

import java.util.List;

/**
 * 包含会话消息的响应。
 *
 * @param success 查询是否成功
 * @param messages 消息列表
 * @param message 失败消息
 * @author data-agent
 */
public record SessionMessagesResponse(boolean success, List<ConversationMessage> messages, String message) {

    /**
     * 创建成功响应。
     *
     * @param messages 消息列表
     * @return 响应
     */
    public static SessionMessagesResponse ok(List<ConversationMessage> messages) {
        return new SessionMessagesResponse(true, messages, null);
    }

    /**
     * 创建失败响应。
     *
     * @param message 失败消息
     * @return 响应
     */
    public static SessionMessagesResponse fail(String message) {
        return new SessionMessagesResponse(false, List.of(), message);
    }
}
