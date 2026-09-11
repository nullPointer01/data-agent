package com.ai.repository;

import com.ai.model.ConversationMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 持久化对话消息仓储。
 *
 * @author data-agent
 */
public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    /**
     * 查询一个会话中的所有消息。
     *
     * @param sessionId 会话 ID
     * @return 消息列表
     */
    List<ConversationMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    /**
     * 按角色查询一个会话中的消息。
     *
     * @param sessionId 会话 ID
     * @param role 消息角色
     * @return 消息列表
     */
    List<ConversationMessage> findBySessionIdAndRoleOrderByCreatedAtAsc(String sessionId, String role);

    /**
     * 统计一个会话中的消息数。
     *
     * @param sessionId 会话 ID
     * @return 消息数
     */
    long countBySessionId(String sessionId);

    /**
     * 删除一个会话中的消息。
     *
     * @param sessionId 会话 ID
     */
    void deleteBySessionId(String sessionId);

    /**
     * 按倒序时间顺序查询最近的消息。
     *
     * @param sessionId 会话 ID
     * @param pageable 分页请求
     * @return 最近消息
     */
    @Query("SELECT m FROM ConversationMessage m WHERE m.sessionId = :sessionId ORDER BY m.createdAt DESC")
    List<ConversationMessage> findRecentBySessionId(@Param("sessionId") String sessionId, Pageable pageable);

    /**
     * 查询一个 Run 在会话中关联的助手消息。
     *
     * @param sessionId 会话 ID
     * @param runId Agent Run ID
     * @param role 消息角色
     * @return 最早关联的消息
     */
    Optional<ConversationMessage> findFirstBySessionIdAndRunIdAndRoleOrderByCreatedAtAsc(
            String sessionId, String runId, String role);
}
