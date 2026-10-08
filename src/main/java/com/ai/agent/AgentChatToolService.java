package com.ai.agent;

import com.ai.agent.approval.AgentApprovalSuspensionService;
import com.ai.agent.react.ErrorRecoveryAdvisor;
import com.ai.agent.react.ReActExecutionResult;
import com.ai.agent.react.ReActStreamEventWriter;
import com.ai.agent.react.ReActWorkingMemoryService;
import com.ai.agent.runtime.AgentRunScope;
import com.ai.agent.runtime.planning.AgentToolChoice;
import com.ai.agent.tool.AgentToolInvoker;
import com.ai.agent.tool.ai.AgentToolSuspendedException;
import com.ai.agent.tool.ai.GovernedToolSession;
import com.ai.agent.tool.governance.AgentToolInvocationContext;
import com.ai.config.TaskContextSnapshot;
import com.ai.model.AnalysisResponse;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/** 将框架工具对话接入 Run 的结果、工作记忆和 SSE 生命周期；不自行实现推理循环。 */
@Component
public class AgentChatToolService {
    private final AgentChatService chat;
    private final AgentToolInvoker invoker;
    private final AgentApprovalSuspensionService approvals;
    private final ErrorRecoveryAdvisor recovery;
    private final ReActStreamEventWriter events;
    private final ReActWorkingMemoryService memory;

    public AgentChatToolService(AgentChatService chat, AgentToolInvoker invoker,
            AgentApprovalSuspensionService approvals, ErrorRecoveryAdvisor recovery,
            ReActStreamEventWriter events, ReActWorkingMemoryService memory) {
        this.chat = chat;
        this.invoker = invoker;
        this.approvals = approvals;
        this.recovery = recovery;
        this.events = events;
        this.memory = memory;
    }

    /** 同步助手调用，审批只转成暂停结果，其他异常继续交给 Coordinator。 */
    public ReActExecutionResult run(List<ChatMessage> messages, List<ToolSpecification> specs,
            String modelId, String sessionId, String question, List<AnalysisResponse.ThinkingStep> steps,
            AgentToolInvocationContext invocation, AgentToolChoice choice) {
        var session = open(specs, steps, null, invocation, choice);
        memory.recordStart(sessionId, question);
        try {
            return finish(chat.chat(messages, modelId, session), null, sessionId, question);
        } catch (RuntimeException error) {
            return finish(null, error, sessionId, question);
        }
    }

    /** 返回贯通 SSE 的 Future，避免同步等待框架工具流。 */
    public CompletableFuture<ReActExecutionResult> stream(List<ChatMessage> messages, List<ToolSpecification> specs,
            String modelId, String sessionId, String question, List<AnalysisResponse.ThinkingStep> steps,
            Consumer<String> emitter, AgentToolInvocationContext invocation, AgentToolChoice choice) {
        var context = TaskContextSnapshot.capture();
        var session = open(specs, steps, emitter, invocation, choice);
        memory.recordStart(sessionId, question);
        return chat.stream(messages, modelId, token -> events.emitToken(emitter, token), session)
                .handle((answer, error) -> context.call(() -> finish(answer, error, sessionId, question)));
    }

    /** 每次初始执行或审批续跑创建隔离的治理状态，预算继续使用当前根 Run。 */
    public GovernedToolSession open(List<ToolSpecification> specs, List<AnalysisResponse.ThinkingStep> steps,
            Consumer<String> emitter, AgentToolInvocationContext invocation, AgentToolChoice choice) {
        return new GovernedToolSession(invoker, approvals, recovery, events, memory, invocation, specs, steps, emitter, choice);
    }

    /** 普通助手与持久化续跑共用终态转换，不把等待审批误写成成功或失败。 */
    public ReActExecutionResult finish(String answer, Throwable error, String sessionId, String question) {
        var snapshot = AgentRunScope.requireCurrent().snapshot();
        while (error instanceof CompletionException && error.getCause() != null) {
            error = error.getCause();
        }
        if (error instanceof AgentToolSuspendedException suspended) {
            return new ReActExecutionResult("等待工具动作审批", snapshot.iterations(), snapshot.toolCalls(),
                    true, true, suspended.approvalId());
        }
        if (error != null) {
            memory.recordFailure(sessionId, question, snapshot.iterations());
            throw error instanceof RuntimeException runtime ? runtime : new CompletionException(error);
        }
        memory.recordCompletion(sessionId, question, snapshot.iterations(), answer);
        return new ReActExecutionResult(answer, snapshot.iterations(), snapshot.toolCalls(), true);
    }
}
