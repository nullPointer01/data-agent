package com.ai.memory;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 用户级记忆写入栅栏：显式操作推进修订号，后台任务按来源消息顺序提交。 */
@Mapper
public interface MemoryWriteStateMapper {
    int ensure(@Param("tenant") String tenant, @Param("user") String user);
    State lock(@Param("tenant") String tenant, @Param("user") String user);
    State find(@Param("tenant") String tenant, @Param("user") String user);
    int advance(@Param("tenant") String tenant, @Param("user") String user);
    int processed(@Param("tenant") String tenant, @Param("user") String user,
            @Param("source") long source);

    /** 仅在同事务持有用户行锁时使用。 */
    record State(long revision, long lastSourceMessageId) { }
}
