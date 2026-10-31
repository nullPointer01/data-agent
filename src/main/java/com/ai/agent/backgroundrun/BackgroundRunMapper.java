package com.ai.agent.backgroundrun;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Optional;

/** MyBatis 承担首次任务领取、租约围栏和有界事件查询。 */
@Mapper
public interface BackgroundRunMapper {
    Optional<RunSubmission> find(@Param("runId") String runId);
    Optional<RunSubmission> findKey(@Param("tenantId") String tenantId, @Param("userId") String userId,
            @Param("requestKey") String requestKey);
    void insert(RunSubmission submission);
    int claim(@Param("runId") String runId, @Param("owner") String owner, @Param("leaseMicros") long leaseMicros);
    int renew(@Param("runId") String runId, @Param("owner") String owner, @Param("leaseMicros") long leaseMicros);
    boolean validLease(@Param("runId") String runId, @Param("owner") String owner);
    boolean validEventLease(@Param("runId") String runId, @Param("owner") String owner);
    boolean expiredLease(@Param("runId") String runId);
    Optional<String> lockRun(@Param("runId") String runId);
    Optional<RunSubmission> lockSubmission(@Param("runId") String runId);
    int chargeEvent(@Param("runId") String runId, @Param("bytes") int bytes);
    void append(@Param("runId") String runId, @Param("payload") String payload);
    List<RunEventRow> events(@Param("runId") String runId, @Param("after") long after);
    List<String> queued();
    List<String> expired();
    void release(@Param("runId") String runId, @Param("owner") String owner, @Param("retentionDays") int retentionDays);
    List<String> expiredLogs();
    int purgeEvents(@Param("runId") String runId, @Param("limit") int limit);
    void clearLogExpiry(@Param("runId") String runId);
}
