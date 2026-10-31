package com.ai.conversation.dto;

import com.ai.model.ConversationMessage;

import java.util.List;

/**
 * 包含会话消息的响应。
 *
 * @param success 查询是否成功
 * @param messages 消息列表
 * @param message 失败消息
 * @param nextBeforeId 更早消息游标，为空表示已加载完毕
 * @param sessionStatus 会话状态，归档会话只读
 * @author data-agent
 */
public record SessionMessagesResponse(boolean success, List<ConversationMessage> messages, String message,
        Long nextBeforeId, String sessionStatus) {

    /**
     * 创建成功响应。
     *
     * @param messages 消息列表
     * @param nextBeforeId 更早消息游标
     * @param status 会话状态
     * @return 响应
     */
    public static SessionMessagesResponse ok(List<ConversationMessage> messages, Long nextBeforeId, String status) {
        return new SessionMessagesResponse(true, messages, null, nextBeforeId, status);
    }

    /**
     * 创建失败响应。
     *
     * @param message 失败消息
     * @return 响应
     */
    public static SessionMessagesResponse fail(String message) {
        return new SessionMessagesResponse(false, List.of(), message, null, null);
    }
}
