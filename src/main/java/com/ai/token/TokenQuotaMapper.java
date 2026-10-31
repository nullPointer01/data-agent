package com.ai.token;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/** 日账本与实际调用预占；所有变更按“日账本→预占单”的固定顺序加行锁。 */
@Mapper
public interface TokenQuotaMapper {
    QuotaUser findUser(@Param("tenant") String tenant, @Param("user") String user);
    int ensureDaily(@Param("tenant") String tenant, @Param("user") String user, @Param("day") LocalDate day);
    Daily lockDaily(@Param("tenant") String tenant, @Param("user") String user, @Param("day") LocalDate day);
    int insertReservation(@Param("ticket") TokenReservation ticket, @Param("ttlSeconds") long ttlSeconds);
    TokenReservation lockReservation(@Param("id") String id);
    int markDispatched(@Param("id") String id);
    List<TokenReservation> findExpired(@Param("tenant") String tenant, @Param("user") String user);
    int finishReservation(@Param("id") String id, @Param("state") String state, @Param("charged") long charged);
    int adjustDaily(@Param("tenant") String tenant, @Param("user") String user, @Param("day") LocalDate day,
            @Param("chargedDelta") long chargedDelta, @Param("reservedDelta") long reservedDelta,
            @Param("uncertainDelta") long uncertainDelta);

    /** 配额只需账号状态和个人上限，不加载密码或角色集合。 */
    record QuotaUser(boolean enabled, long dailyTokenLimit) { }
    /** charged 包括暂按上界扣除的 UNKNOWN；uncertain 用于向用户解释这部分。 */
    record Daily(long chargedTokens, long reservedTokens, long uncertainTokens) { }
}
