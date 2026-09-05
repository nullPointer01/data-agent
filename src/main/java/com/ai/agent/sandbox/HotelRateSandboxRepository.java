package com.ai.agent.sandbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 酒店价格沙箱仓储，写入使用数据库唯一键完成幂等竞争。
 *
 * @author data-agent
 */
public interface HotelRateSandboxRepository extends JpaRepository<HotelRateSandboxEntity, Long> {

    Optional<HotelRateSandboxEntity> findByTenantIdAndActionKey(String tenantId, String actionKey);

    Optional<HotelRateSandboxEntity> findFirstByTenantIdAndHotelIdAndRoomTypeAndStayDateOrderByCreatedAtDesc(
            String tenantId, String hotelId, String roomType, LocalDate stayDate);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO hotel_rate_sandbox (
                tenant_id, hotel_id, room_type, stay_date, currency,
                before_price, after_price, action_key, approval_id, tool_call_id, demo, created_at
            ) VALUES (
                :tenantId, :hotelId, :roomType, :stayDate, :currency,
                :beforePrice, :afterPrice, :actionKey, :approvalId, :toolCallId, 1, :createdAt
            )
            """, nativeQuery = true)
    int insertOnce(@Param("tenantId") String tenantId,
            @Param("hotelId") String hotelId,
            @Param("roomType") String roomType,
            @Param("stayDate") LocalDate stayDate,
            @Param("currency") String currency,
            @Param("beforePrice") BigDecimal beforePrice,
            @Param("afterPrice") BigDecimal afterPrice,
            @Param("actionKey") String actionKey,
            @Param("approvalId") String approvalId,
            @Param("toolCallId") String toolCallId,
            @Param("createdAt") Instant createdAt);
}
