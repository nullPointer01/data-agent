package com.ai.agent.sandbox;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * [演示功能][隔离数据] 酒店改价审批的演示流水实体。
 *
 * <p>该表仅用于验收 Agent 的高风险动作流程，不连接任何真实酒店业务表，
 * {@code hotelId}、房型、价格和日期都是演示数据。</p>
 *
 * @author data-agent
 */
public class HotelRateSandboxEntity {

    private Long id;

    private String tenantId;

    private String hotelId;

    private String roomType;

    private LocalDate stayDate;

    private String currency = "CNY";

    private BigDecimal beforePrice;

    private BigDecimal afterPrice;

    private String actionKey;

    private String approvalId;

    private String toolCallId;

    private boolean demo = true;

    private Instant createdAt;

    public void onCreate() {
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
