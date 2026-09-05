package com.ai.agent.sandbox;

import com.ai.agent.approval.AgentApprovalTelemetry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 只写隔离演示流水的酒店价格沙箱服务。
 *
 * @author data-agent
 */
@Service
public class HotelRateSandboxService {

    private static final BigDecimal DEFAULT_PRICE = new BigDecimal("399.00");
    private static final String CURRENCY = "CNY";

    private final HotelRateSandboxRepository repository;
    private final AgentApprovalTelemetry telemetry;

    public HotelRateSandboxService(HotelRateSandboxRepository repository,
            AgentApprovalTelemetry telemetry) {
        this.repository = repository;
        this.telemetry = telemetry;
    }

    /**
     * 按审批动作键最多写入一次价格变化，并始终返回第一次动作结果。
     */
    @Transactional
    public HotelRateSandboxResult updatePrice(String tenantId,
            String runId,
            String approvalId,
            String toolCallId,
            String hotelId,
            String roomType,
            LocalDate stayDate,
            BigDecimal requestedPrice) {
        String normalizedTenant = requireText(tenantId, "tenantId");
        String normalizedApproval = requireText(approvalId, "approvalId");
        String normalizedToolCall = requireText(toolCallId, "toolCallId");
        String normalizedHotel = requireText(hotelId, "hotelId");
        String normalizedRoom = requireText(roomType, "roomType");
        if (stayDate == null) {
            throw new IllegalArgumentException("入住日期不能为空");
        }
        BigDecimal afterPrice = normalizePrice(requestedPrice);
        String actionKey = normalizedApproval + ":" + normalizedToolCall;
        HotelRateSandboxEntity existing = repository.findByTenantIdAndActionKey(
                normalizedTenant, actionKey).orElse(null);
        if (existing != null) {
            telemetry.record(
                    "SANDBOX_ACTION",
                    "REPLAYED",
                    normalizedTenant,
                    null,
                    "agent-runtime",
                    normalizedApproval,
                    runId,
                    normalizedToolCall,
                    "updateHotelPrice");
            return toResult(existing, true);
        }

        BigDecimal beforePrice = repository
                .findFirstByTenantIdAndHotelIdAndRoomTypeAndStayDateOrderByCreatedAtDesc(
                        normalizedTenant, normalizedHotel, normalizedRoom, stayDate)
                .map(HotelRateSandboxEntity::getAfterPrice)
                .orElse(DEFAULT_PRICE);
        int inserted = repository.insertOnce(
                normalizedTenant,
                normalizedHotel,
                normalizedRoom,
                stayDate,
                CURRENCY,
                beforePrice,
                afterPrice,
                actionKey,
                normalizedApproval,
                normalizedToolCall,
                Instant.now());
        HotelRateSandboxEntity stored = repository.findByTenantIdAndActionKey(normalizedTenant, actionKey)
                .orElseThrow(() -> new IllegalStateException("酒店价格沙箱动作未能持久化"));
        telemetry.record(
                "SANDBOX_ACTION",
                inserted == 0 ? "REPLAYED" : "SUCCEEDED",
                normalizedTenant,
                null,
                "agent-runtime",
                normalizedApproval,
                runId,
                normalizedToolCall,
                "updateHotelPrice");
        return toResult(stored, inserted == 0);
    }

    private BigDecimal normalizePrice(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.compareTo(new BigDecimal("999999.99")) > 0) {
            throw new IllegalArgumentException("演示价格必须在 0.01 到 999999.99 之间");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }

    private HotelRateSandboxResult toResult(HotelRateSandboxEntity entity, boolean replayed) {
        return new HotelRateSandboxResult(
                entity.getTenantId(),
                entity.getHotelId(),
                entity.getRoomType(),
                entity.getStayDate(),
                entity.getCurrency(),
                entity.getBeforePrice(),
                entity.getAfterPrice(),
                entity.getApprovalId(),
                entity.getToolCallId(),
                true,
                replayed,
                entity.getCreatedAt());
    }

    /**
     * 可返回模型的隔离沙箱动作结果。
     */
    public record HotelRateSandboxResult(
            String tenantId,
            String hotelId,
            String roomType,
            LocalDate stayDate,
            String currency,
            BigDecimal beforePrice,
            BigDecimal afterPrice,
            String approvalId,
            String toolCallId,
            boolean demo,
            boolean replayed,
            Instant createdAt) {
    }
}
