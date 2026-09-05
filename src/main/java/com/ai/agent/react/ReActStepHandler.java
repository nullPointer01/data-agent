package com.ai.agent.react;

import com.ai.agent.approval.AgentApprovalSuspension;
import com.ai.agent.approval.AgentApprovalSuspensionService;
import com.ai.agent.runtime.AgentRunContext;
import com.ai.agent.runtime.AgentRunScope;
import com.ai.agent.runtime.event.AgentEvent;
import com.ai.agent.runtime.event.AgentEventType;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.input.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import com.ai.agent.tool.AgentToolInvoker;
import com.ai.agent.tool.governance.AgentToolAdmission;
import com.ai.agent.tool.governance.AgentToolExecutionResult;
import com.ai.agent.tool.governance.AgentToolExecutionStatus;
import com.ai.agent.tool.governance.AgentToolInvocationContext;

/**
 * 处理单次 ReAct 模型响应：消费原生 Function Calling 工具请求并回填观察结果。
 *
 * @author data-agent
 */
@Component
public class ReActStepHandler {

    private static final String MEMORY_INDEXED_MARKER = "__MEMORY_INDEXED__";
    private static final String MEMORY_INDEXED_OBSERVATION = "记忆已保存。请继续回答用户的问题。";
    private static final PromptTemplate TOOL_RESULT_TEMPLATE = PromptTemplate.from(
            "工具执行结果:\n{{result}}\n\n请根据结果继续分析或给出最终回答。如果已有足够信息，请直接给出最终回答（使用Markdown格式），不要再调用工具。");

    private final ReActResponseParser responseParser;
    private final AgentToolInvoker toolInvoker;
    private final ErrorRecoveryAdvisor recoveryAdvisor;
    private final AgentApprovalSuspensionService approvalSuspensionService;

    @Autowired
    public ReActStepHandler(ReActResponseParser responseParser, AgentToolInvoker toolInvoker,
            @Nullable ErrorRecoveryAdvisor recoveryAdvisor,
            AgentApprovalSuspensionService approvalSuspensionService) {
        this.responseParser = responseParser;
        this.toolInvoker = toolInvoker;
        this.recoveryAdvisor = recoveryAdvisor;
        this.approvalSuspensionService = approvalSuspensionService;
    }

    /**
     * 处理一次原生模型响应：有工具请求则执行并回填观察，否则视为最终回答。
     *
     * @param aiMessage 模型返回的 AI 消息
     * @param messages 可变的 ReAct 消息历史
     * @return 步骤处理结果
     */
    public ReActStepOutcome handleNative(AiMessage aiMessage, List<ChatMessage> messages,
            AgentToolInvocationContext invocationContext) {
        if (aiMessage == null) {
            return ReActStepOutcome.finalAnswer("");
        }
        if (!aiMessage.hasToolExecutionRequests()) {
            String text = aiMessage.text() == null ? "" : aiMessage.text();
            return ReActStepOutcome.finalAnswer(responseParser.cleanAssistantAnswer(text));
        }

        // 先完成整组纯准入检查。若本轮包含审批动作，不能先执行同组其他工具，否则暂停时会留下
        // 部分副作用和无法成对恢复的 tool_call。审批 Checkpoint 只保留一个待执行动作，其余动作
        // 由恢复后的模型重新规划。
        List<ToolExecutionRequest> requests = aiMessage.toolExecutionRequests();
        List<AgentToolAdmission> admissions = new ArrayList<>(requests.size());
        AgentToolAdmission pendingApproval = null;
        ToolExecutionRequest pendingRequest = null;
        for (ToolExecutionRequest request : requests) {
            AgentToolAdmission admission = toolInvoker.admit(request, invocationContext);
            admissions.add(admission);
            if (pendingApproval == null && admission.approvalRequired()) {
                pendingApproval = admission;
                pendingRequest = request;
            }
        }
        messages.add(aiMessage);
        if (pendingApproval != null) {
            AgentToolExecutionResult executionResult = toolInvoker.invokeStructured(
                    pendingApproval, invocationContext);
            AgentApprovalSuspension suspension = approvalSuspensionService.suspend(
                    pendingRequest, executionResult, invocationContext, messages);
            emitApprovalRequired(suspension, executionResult);
            return ReActStepOutcome.approvalRequired(executionResult, suspension.approvalId());
        }

        String firstToolName = null;
        AgentToolExecutionResult firstToolResult = null;
        ErrorRecoveryAdvice firstAdvice = null;
        boolean allMemoryIndexed = true;

        for (int index = 0; index < requests.size(); index++) {
            ToolExecutionRequest request = requests.get(index);
            AgentToolExecutionResult executionResult = toolInvoker.invokeStructured(
                    admissions.get(index), invocationContext);
            String toolCallResult = executionResult.toModelObservation();
            if (executionResult.successful() && MEMORY_INDEXED_MARKER.equals(executionResult.payload().content())) {
                // Memory indexing 是一种内部副作用；通知模型继续而不计入新的迭代。
                messages.add(ToolExecutionResultMessage.from(request, MEMORY_INDEXED_OBSERVATION));
                continue;
            }
            allMemoryIndexed = false;

            // 如果配置了错误恢复建议器，则先尝试追加恢复建议并获取建议对象
            ErrorRecoveryAdvice advice = recoveryAdvisor != null
                    ? recoveryAdvisor.advise(request.name(), executionResult)
                    : ErrorRecoveryAdvice.none();
            String toolResultWithAdvice = recoveryAdvisor != null
                    ? recoveryAdvisor.appendAdvice(request.name(), executionResult)
                    : toolCallResult;
            messages.add(ToolExecutionResultMessage.from(request,
                    TOOL_RESULT_TEMPLATE.apply(Map.of("result", toolResultWithAdvice)).text()));
            if (advice != null && advice.recoveryRequired() && advice.hasInstruction()) {
                messages.add(UserMessage.from("错误恢复建议: " + advice.instruction()));
            }
            if (firstToolName == null) {
                firstToolName = request.name();
                firstToolResult = executionResult;
                firstAdvice = advice;
            }
        }

        if (allMemoryIndexed) {
            return ReActStepOutcome.memoryIndexed();
        }
        // 步骤语义（思考步骤展示、是否需要终答）沿用第一个工具，与单工具行为一致
        return ReActStepOutcome.toolObservation(firstToolResult,
                isFinalAnswerRequired(firstToolName, firstToolResult), firstAdvice);
    }

    private void emitApprovalRequired(AgentApprovalSuspension suspension,
            AgentToolExecutionResult executionResult) {
        AgentRunContext context = AgentRunScope.current()
                .orElseThrow(() -> new IllegalStateException("审批事件缺少 Agent Run Context"));
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("approvalId", suspension.approvalId());
        payload.put("toolCallId", suspension.toolCallId());
        payload.put("toolName", suspension.toolName());
        payload.put("risk", executionResult.approvalContext().risk().name());
        payload.put("argumentSummary", suspension.safeArgumentSummary());
        payload.put("expiresAt", suspension.expiresAt().toString());
        payload.put("status", "WAITING_APPROVAL");
        context.eventSink().emit(AgentEvent.of(context, AgentEventType.APPROVAL_REQUIRED, payload));
    }

    private boolean isFinalAnswerRequired(String toolName, AgentToolExecutionResult result) {
        if (result == null) {
            return true;
        }
        if (result.status() == AgentToolExecutionStatus.UNAUTHORIZED
                || result.status() == AgentToolExecutionStatus.POLICY_DENIED
                || result.status() == AgentToolExecutionStatus.RUN_TERMINATED) {
            return true;
        }
        return result.successful() && "askUserForInfo".equals(toolName);
    }
}
