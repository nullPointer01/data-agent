package com.ai.memory;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.Instant;
import java.util.List;

/** 提取任务的去重、状态和租约 SQL，不在数据库事务内调用模型。 */
@Mapper
public interface MemoryExtractionTaskMapper {
    int insert(@Param("task") MemoryExtractionTask task);
    List<MemoryExtractionTask> ready(@Param("limit") int limit);
    MemoryExtractionTask findReady(@Param("id") String id);
    int claim(@Param("id") String id, @Param("owner") String owner, @Param("deadline") Instant deadline);
    int renew(@Param("task") MemoryExtractionTask task, @Param("deadline") Instant deadline);
    int finish(@Param("task") MemoryExtractionTask task, @Param("status") String status,
            @Param("attempts") int attempts, @Param("next") Instant next, @Param("error") String error);
    int purge(@Param("before") Instant before);
    String lockOwned(@Param("task") MemoryExtractionTask task);
}
