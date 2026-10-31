package com.ai.indexing;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/** 索引任务 SQL 与租约条件；来源表只由 XML 中的固定分支选择。 */
@Mapper
public interface IndexTaskMapper {
    int enqueue(@Param("id") String id, @Param("kind") IndexTask.Kind kind,
            @Param("tenant") String tenant, @Param("user") String user,
            @Param("source") String source);

    List<IndexTask> ready(@Param("limit") int limit);

    IndexTask findReady(@Param("id") String id);

    int claim(@Param("task") IndexTask task, @Param("owner") String owner, @Param("deadline") Instant deadline);

    int renew(@Param("task") IndexTask task, @Param("deadline") Instant deadline);

    int finish(@Param("task") IndexTask task, @Param("state") String state,
            @Param("attempts") int attempts, @Param("nextAttempt") Instant nextAttempt, @Param("error") String error);

    int markDirty(@Param("id") String id);

    boolean failedVersion(@Param("task") IndexTask task);

    List<StateRow> knowledgeStatuses(@Param("tenant") String tenant, @Param("user") String user,
            @Param("ids") List<String> ids);

    List<StateRow> memoryStatuses(@Param("tenant") String tenant, @Param("user") String user,
            @Param("ids") List<String> ids);

    int retry(@Param("id") String id, @Param("tenant") String tenant, @Param("user") String user);

    List<IndexRebuildService.Source> sources(@Param("kind") IndexTask.Kind kind,
            @Param("tenant") String tenant, @Param("user") String user,
            @Param("after") String after, @Param("limit") int limit);

    List<StateRow> taskPage(@Param("kind") IndexTask.Kind kind,
            @Param("tenant") String tenant, @Param("user") String user,
            @Param("after") String after, @Param("limit") int limit);

    List<String> lockSource(@Param("kind") IndexTask.Kind kind,
            @Param("tenant") String tenant, @Param("user") String user, @Param("id") String id);

    /** 扁平数据库投影在边界转换为接口视图，不让 SQL 承担业务对象的嵌套装配。 */
    record StateRow(String taskId, String sourceId, String state, long desiredVersion,
            long completedVersion, int attempts, String lastError) {
        public IndexTask.Status status() {
            return new IndexTask.Status(state, desiredVersion, completedVersion, attempts);
        }

        public IndexRebuildService.TaskView view() {
            return new IndexRebuildService.TaskView(taskId, sourceId, status(), lastError);
        }
    }
}
