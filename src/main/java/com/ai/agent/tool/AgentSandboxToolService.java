package com.ai.agent.tool;

import com.ai.agent.approval.AgentApprovalDecisionStatus;
import com.ai.agent.approval.AgentApprovalExecutionStatus;
import com.ai.agent.approval.AgentToolApprovalEntity;
import com.ai.agent.approval.AgentToolApprovalRepository;
import com.ai.agent.durable.AgentDurableRunStore;
import com.ai.agent.runtime.AgentRunContext;
import com.ai.agent.runtime.AgentRunScope;
import com.ai.agent.sandbox.HotelRateSandboxService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 审批型演示工具适配器，只允许当前恢复 Run 的已批准动作进入沙箱。
 *
 * @author data-agent
 */
@Service
public class AgentSandboxToolService {

    public static final String TOOL_NAME = "updateHotelPrice";

    private final AgentDurableRunStore runStore;
    private final AgentToolApprovalRepository approvalRepository;
    private final HotelRateSandboxService sandboxService;
    private final ObjectMapper objectMapper;

    public AgentSandboxToolService(AgentDurableRunStore runStore,
            AgentToolApprovalRepository approvalRepository,
            HotelRateSandboxService sandboxService,
            ObjectMapper objectMapper) {
        this.runStore = runStore;
        this.approvalRepository = approvalRepository;
        this.sandboxService = sandboxService;
        this.objectMapper = objectMapper;
    }

    public String updateHotelPrice(String hotelId, String roomType, String stayDate, double newPrice) {
        AgentRunContext context = AgentRunScope.current()
                .orElseThrow(() -> new SecurityException("酒店价格沙箱缺少 Agent Run Context"));
        String approvalId = runStore.find(context.runId())
                .map(run -> run.getApprovalId())
                .filter(value -> value != null && !value.isBlank())
                .orElseThrow(() -> new SecurityException("酒店价格沙箱缺少审批引用"));
        AgentToolApprovalEntity approval = approvalRepository.findById(approvalId)
                .filter(item -> Objects.equals(item.getTenantId(), context.tenantId()))
                .filter(item -> Objects.equals(item.getRunId(), context.runId()))
                .filter(item -> TOOL_NAME.equals(item.getToolName()))
                .filter(item -> item.getDecisionStatus() == AgentApprovalDecisionStatus.APPROVED)
                .filter(item -> item.getExecutionStatus() == AgentApprovalExecutionStatus.RUNNING)
                .orElseThrow(() -> new SecurityException("酒店价格沙箱审批状态无效"));
        HotelRateSandboxService.HotelRateSandboxResult result = sandboxService.updatePrice(
                context.tenantId(),
                context.runId(),
                approval.getApprovalId(),
                approval.getToolCallId(),
                hotelId,
                roomType,
                LocalDate.parse(stayDate),
                BigDecimal.valueOf(newPrice));
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("酒店价格沙箱结果序列化失败", e);
        }
    }
}
