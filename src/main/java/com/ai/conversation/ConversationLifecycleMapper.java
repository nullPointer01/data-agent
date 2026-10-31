package com.ai.conversation;

import com.ai.model.ConversationSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 会话状态、关联任务取消及到期内容清理；不承担模型或向量调用。 */
@Mapper
public interface ConversationLifecycleMapper {
    List<ConversationSessionEntity> list(@Param("tenantId") String tenantId, @Param("userId") String userId,
            @Param("status") String status, @Param("cursor") ConversationCursor cursor, @Param("limit") int limit);
    int changeState(@Param("sessionId") String sessionId, @Param("status") String status);
    boolean hasPendingRun(@Param("sessionId") String sessionId);
    int cancelRuns(@Param("sessionId") String sessionId);
    int cancelApprovals(@Param("sessionId") String sessionId);
    int cancelExtraction(@Param("sessionId") String sessionId);
    List<String> expired(@Param("retentionDays") int retentionDays, @Param("limit") int limit);
    ConversationSessionEntity lockExpired(@Param("sessionId") String sessionId, @Param("retentionDays") int retentionDays);
    int purgeMessages(@Param("sessionId") String sessionId, @Param("limit") int limit);
    int purgeTasks(@Param("sessionId") String sessionId, @Param("limit") int limit);
    int purgeApprovals(@Param("sessionId") String sessionId, @Param("limit") int limit);
    int purgeRunEvents(@Param("sessionId") String sessionId, @Param("limit") int limit);
    int purgeRunSubmissions(@Param("sessionId") String sessionId, @Param("limit") int limit);
    int purgeRuns(@Param("sessionId") String sessionId, @Param("limit") int limit);
    int purgeTraces(@Param("sessionId") String sessionId, @Param("limit") int limit);
    int purgeSummaries(@Param("sessionId") String sessionId, @Param("limit") int limit);
    int purgeSessionIfEmpty(@Param("sessionId") String sessionId);
}
