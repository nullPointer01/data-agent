package com.ai.conversation.dto;

import com.ai.model.ConversationSessionEntity;

import java.util.List;

/**
 * 包含当前用户会话的响应。
 *
 * @param success 查询是否成功
 * @param sessions 会话列表
 * @author data-agent
 */
public record SessionListResponse(boolean success, List<ConversationSessionEntity> sessions) {
}
