package com.ai.agent.sandbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 隔离酒店价格演示流水，不连接任何真实酒店业务表。
 *
 * @author data-agent
 */
@Entity
@Table(name = "hotel_rate_sandbox", indexes = {
        @Index(name = "idx_hotel_rate_sandbox_current",
                columnList = "tenant_id,hotel_id,room_type,stay_date,created_at"),
        @Index(name = "idx_hotel_rate_sandbox_approval",
                columnList = "tenant_id,approval_id,tool_call_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_hotel_rate_sandbox_action", columnNames = {"tenant_id", "action_key"})
})
public class HotelRateSandboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "hotel_id", nullable = false, length = 64)
    private String hotelId;

    @Column(name = "room_type", nullable = false, length = 64)
    private String roomType;

    @Column(name = "stay_date", nullable = false)
    private LocalDate stayDate;

    @Column(nullable = false, length = 8)
    private String currency = "CNY";

    @Column(name = "before_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal beforePrice;

    @Column(name = "after_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal afterPrice;

    @Column(name = "action_key", nullable = false, length = 255)
    private String actionKey;

    @Column(name = "approval_id", length = 64)
    private String approvalId;

    @Column(name = "tool_call_id", length = 128)
    private String toolCallId;

    @Column(nullable = false)
    private boolean demo = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = createdAt == null ? Instant.now() : createdAt;
        demo = true;
    }

    public Long getId() { return id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getHotelId() { return hotelId; }
    public void setHotelId(String hotelId) { this.hotelId = hotelId; }
    public String getRoomType() { return roomType; }
    public void setRoomType(String roomType) { this.roomType = roomType; }
    public LocalDate getStayDate() { return stayDate; }
    public void setStayDate(LocalDate stayDate) { this.stayDate = stayDate; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getBeforePrice() { return beforePrice; }
    public void setBeforePrice(BigDecimal beforePrice) { this.beforePrice = beforePrice; }
    public BigDecimal getAfterPrice() { return afterPrice; }
    public void setAfterPrice(BigDecimal afterPrice) { this.afterPrice = afterPrice; }
    public String getActionKey() { return actionKey; }
    public void setActionKey(String actionKey) { this.actionKey = actionKey; }
    public String getApprovalId() { return approvalId; }
    public void setApprovalId(String approvalId) { this.approvalId = approvalId; }
    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }
    public boolean isDemo() { return demo; }
    public Instant getCreatedAt() { return createdAt; }
}
