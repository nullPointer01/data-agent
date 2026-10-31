package com.ai.agent.tool.ai;

import com.ai.agent.approval.AgentApprovalSuspensionService;
import com.ai.agent.react.ErrorRecoveryAdvisor;
import com.ai.agent.react.ErrorRecoveryType;
import com.ai.agent.react.ReActStreamEventWriter;
import com.ai.agent.react.ReActWorkingMemoryService;
import com.ai.agent.runtime.AgentRunContext;
import com.ai.agent.runtime.AgentRunScope;
import com.ai.agent.runtime.event.AgentEvent;
import com.ai.agent.runtime.event.AgentEventType;
import com.ai.agent.runtime.planning.AgentToolChoice;
import com.ai.agent.tool.AgentToolInvoker;
import com.ai.agent.tool.governance.AgentToolAdmission;
import com.ai.agent.tool.governance.AgentToolExecutionResult;
import com.ai.agent.tool.governance.AgentToolInvocationContext;
import com.ai.config.TaskContextSnapshot;
import com.ai.mcp.ModelInvocationException;
import com.ai.model.AnalysisResponse;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.reactivestreams.FlowAdapters;
import reactor.core.publisher.Flux;

/**
 * 单次工具对话的治理边界：模型完整响应先整批准入，框架随后逐个执行受治理工具。
 * 实例只属于一个调用，不跨用户共享；正常 AiServices 和审批续跑复用同一实现。
 */
public final class GovernedToolSession {
    private final AgentToolInvoker invoker;
    private final AgentApprovalSuspensionService approvals;
    private final ErrorRecoveryAdvisor recovery;
    private final ReActStreamEventWriter events;
    private final ReActWorkingMemoryService memory;
    private final AgentToolInvocationContext invocation;
    private final List<ToolSpecification> specifications;
    private final List<AnalysisResponse.ThinkingStep> steps;
    private final Consumer<String> emitter;
    private final AgentToolChoice initialChoice;
    private final String modelId;
    private final com.ai.skill.SkillCatalog skills;
    private java.util.Set<String> visibleNames = java.util.Set.of();
    private final TaskContextSnapshot context = TaskContextSnapshot.capture();
    private final AgentRunContext run = AgentRunScope.requireCurrent();
    private final Map<ToolExecutionRequest, AgentToolAdmission> admitted = new LinkedHashMap<>();
    private final Map<ErrorRecoveryType, Integer> failures = new EnumMap<>(ErrorRecoveryType.class);
    private boolean firstTurn = true;
    private boolean answerOnly;
    private RuntimeException toolFailure;

    public GovernedToolSession(AgentToolInvoker invoker, AgentApprovalSuspensionService approvals,
            ErrorRecoveryAdvisor recovery, ReActStreamEventWriter events, ReActWorkingMemoryService memory,
            AgentToolInvocationContext invocation, List<ToolSpecification> specifications,
            List<AnalysisResponse.ThinkingStep> steps, Consumer<String> emitter, AgentToolChoice initialChoice, String modelId,
            com.ai.skill.SkillCatalog skills) {
        this.invoker = invoker;
        this.approvals = approvals;
        this.recovery = recovery;
        this.events = events;
        this.memory = memory;
        this.invocation = invocation;
        this.specifications = List.copyOf(specifications);
        this.steps = steps;
        this.emitter = emitter;
        this.initialChoice = initialChoice;
        this.modelId = modelId;
        this.skills = skills;
        if (initialChoice == AgentToolChoice.REQUIRED && specifications.isEmpty()) {
            throw new IllegalArgumentException("强制工具调用缺少可用工具规格");
        }
        if (specifications.stream().anyMatch(spec -> !invocation.allows(spec.name()))) {
            throw new SecurityException("工具描述超出执行者白名单");
        }
    }

    /** 用框架 ToolProvider 统一提供本轮工具；Skills/MCP/本地工具实际执行均由 Pipeline 发起。 */
    public ToolProvider toolProvider() {
        Map<ToolSpecification, ToolExecutor> tools = new LinkedHashMap<>();
        specifications.forEach(spec -> tools.put(spec, new ToolExecutor() {
            @Override
            public String execute(ToolExecutionRequest request, Object memoryId) {
                return GovernedToolSession.this.execute(request);
            }

            @Override
            public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext invocationContext) {
                return GovernedToolSession.this.executeResult(request);
            }

            @Override
            public CompletableFuture<ToolExecutionResult> executeAsync(
                    ToolExecutionRequest request, InvocationContext invocationContext) {
                // Pipeline 已在独立有界工具池执行并等待超时；当前适配保留这条治理路径。
                // 不使用公共线程池另起一条失去身份/预算的工具任务，也不宣称工具已经完全非阻塞。
                try {
                    return CompletableFuture.completedFuture(executeWithContext(request, invocationContext));
                } catch (Exception error) {
                    return CompletableFuture.failedFuture(error);
                }
            }
        }));
        return new ToolProvider() {
            @Override public boolean isDynamic() { return invocation.initialToolNames().contains(com.ai.skill.SkillCatalog.ACTIVATE_TOOL); }
            @Override public ToolProviderResult provideTools(dev.langchain4j.service.tool.ToolProviderRequest request) {
                return context.call(() -> {
                    Map<ToolSpecification, ToolExecutor> visible = new LinkedHashMap<>();
                    tools.forEach((spec, executor) -> {
                        if (invocation.initialToolNames().contains(spec.name())) visible.put(spec, executor);
                    });
                    if (isDynamic()) {
                        var scoped = skills.scopedTools(request, names -> {
                            Map<ToolSpecification, ToolExecutor> dependencies = new LinkedHashMap<>();
                            tools.forEach((spec, executor) -> { if (names.contains(spec.name())) dependencies.put(spec, executor); });
                            return ToolProviderResult.builder().addAll(dependencies).build();
                        });
                        // 官方只决定哪些技能已经激活；管理工具执行器仍使用统一 Pipeline 的包装。
                        var names = scoped.aiServiceTools().stream().map(tool -> tool.name()).collect(java.util.stream.Collectors.toSet());
                        tools.forEach((spec, executor) -> { if (names.contains(spec.name())) visible.put(spec, executor); });
                    }
                    return ToolProviderResult.builder().addAll(visible).build();
                });
            }
        };
    }

    /** 审批原生续跑和普通 AiServices 使用同一个官方动态技能目录。 */
    public List<ToolSpecification> visibleSpecifications(List<ChatMessage> messages) {
        var request = dev.langchain4j.service.tool.ToolProviderRequest.builder()
                .messages(messages).invocationContext(InvocationContext.builder().build())
                .userMessage(dev.langchain4j.data.message.UserMessage.from("skill-runtime")).build();
        return toolProvider().provideTools(request).aiServiceTools().stream()
                .map(tool -> tool.toolSpecification()).toList();
    }

    /** 包装已具备模型预算/重试治理的模型，每个框架推理轮再申请一次迭代预算。 */
    public ChatModel chatModel(ChatModel delegate) {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                return context.call(() -> {
                    ChatRequest governed = beforeModel(request);
                    ChatResponse response = delegate.chat(governed);
                    preflight(governed, response);
                    return response;
                });
            }
        };
    }

    /** 在完整流式响应交给 AiServices 之前预检；不开放增量工具回调或并发执行。 */
    public StreamingChatModel streamingModel(StreamingChatModel delegate) {
        return new StreamingChatModel() {
            @Override
            public Flow.Publisher<ChatModelStreamingEvent> doChat(ChatRequest request) {
                var events = Flux.defer(() -> context.call(() -> {
                    ChatRequest governed = beforeModel(request);
                    return Flux.from(FlowAdapters.toPublisher(delegate.chat(governed)))
                            // 框架收到 CompleteToolCall 会提前执行；只交付文本和整轮响应，保证整批准入。
                            .filter(event -> event instanceof PartialResponse || event instanceof CompleteResponse)
                            .doOnNext(event -> context.wrap(() -> {
                                run.control().ensureActive();
                                if (event instanceof CompleteResponse complete) {
                                    preflight(governed, complete.chatResponse());
                                }
                            }).run());
                }));
                return FlowAdapters.toFlowPublisher(events);
            }

            @Override
            public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                try {
                    context.call(() -> {
                        ChatRequest governed = beforeModel(request);
                        delegate.chat(governed, new StreamingChatResponseHandler() {
                            @Override
                            public void onPartialResponse(String token) {
                                context.wrap(() -> {
                                    run.control().ensureActive();
                                    handler.onPartialResponse(token);
                                }).run();
                            }

                            @Override
                            public void onCompleteResponse(ChatResponse response) {
                                try {
                                    context.wrap(() -> {
                                        preflight(governed, response);
                                        handler.onCompleteResponse(response);
                                    }).run();
                                } catch (Exception failure) {
                                    onError(failure);
                                }
                            }

                            @Override
                            public void onError(Throwable error) {
                                context.wrap(() -> handler.onError(error)).run();
                            }
                        });
                        return null;
                    });
                } catch (Exception failure) {
                    context.wrap(() -> handler.onError(failure)).run();
                }
            }
        };
    }

    private ChatRequest beforeModel(ChatRequest request) {
        run.control().beforeIteration();
        if (emitter != null) {
            events.emitThinkingStart(emitter, run.snapshot().iterations());
        }
        boolean required = firstTurn && initialChoice == AgentToolChoice.REQUIRED;
        firstTurn = false;
        var current = answerOnly ? List.<ToolSpecification>of() : visibleSpecifications(request.messages());
        visibleNames = current.stream().map(ToolSpecification::name).collect(java.util.stream.Collectors.toUnmodifiableSet());
        // 不让首轮 REQUIRED 延续到终答轮；恢复与子 Agent 继续消耗根 Run 原有预算。
        // ChatRequest.toBuilder 的空集合表示“不覆盖”，不能用它清空工具；替换参数对象才会生效。
        var parameters = ChatRequestParameters.builder().overrideWith(request.parameters())
                .toolSpecifications(current)
                .toolChoice(answerOnly || current.isEmpty() ? null
                        : required ? ToolChoice.REQUIRED : ToolChoice.AUTO).build();
        return request.toBuilder().parameters(parameters).build();
    }

    private void preflight(ChatRequest request, ChatResponse response) {
        run.control().ensureActive();
        if (response == null || response.aiMessage() == null) {
            throw new ModelInvocationException("模型未返回有效响应");
        }
        var message = response.aiMessage();
        memory.recordIteration(run.sessionId(), run.snapshot().iterations(), message.hasToolExecutionRequests()
                ? "请求工具: " + message.toolExecutionRequests().stream().map(ToolExecutionRequest::name).toList()
                : "生成最终回答");
        admitted.clear();
        if (!message.hasToolExecutionRequests()) {
            if (request.parameters().toolChoice() == ToolChoice.REQUIRED) {
                throw new ModelInvocationException("模型未遵守本次必需的工具调用约束");
            }
            return;
        }
        if (answerOnly) {
            throw new ModelInvocationException("模型在工具已关闭的终答轮继续请求工具");
        }
        // 模型调用 ID 必须唯一，否则 Map 覆盖会破坏请求/结果配对与幂等键。
        var ids = new java.util.HashSet<String>();
        for (var tool : message.toolExecutionRequests()) {
            if (!visibleNames.contains(tool.name())) throw new SecurityException("工具尚未通过本轮技能激活开放");
            if (tool.id() == null || tool.id().isBlank() || !ids.add(tool.id())) {
                throw new ModelInvocationException("模型工具调用 ID 缺失或重复");
            }
            admitted.put(tool, invoker.admit(tool, invocation));
        }
        for (var entry : admitted.entrySet()) {
            if (!entry.getValue().approvalRequired()) {
                continue;
            }
            var result = invoker.invokeStructured(entry.getValue(), invocation);
            List<ChatMessage> transcript = new ArrayList<>(request.messages());
            transcript.add(message);
            var suspension = approvals.suspend(entry.getKey(), result, invocation, transcript, modelId);
            run.eventSink().emit(AgentEvent.of(run, AgentEventType.APPROVAL_REQUIRED, Map.of(
                    "approvalId", suspension.approvalId(), "toolCallId", suspension.toolCallId(),
                    "toolName", suspension.toolName(), "risk", result.approvalContext().risk().name(),
                    "argumentSummary", suspension.safeArgumentSummary(),
                    "expiresAt", suspension.expiresAt().toString(), "status", "WAITING_APPROVAL")));
            throw new AgentToolSuspendedException(suspension.approvalId());
        }
    }

    /** 消费预检结果一次；包括幻觉工具在内的请求也不能绕过服务端准入。 */
    public String execute(ToolExecutionRequest request) {
        return executeResult(request).resultText();
    }

    /** 将官方激活属性与安全观察文本一起交还框架，失败结果不携带激活属性。 */
    public ToolExecutionResult executeResult(ToolExecutionRequest request) {
        return context.call(() -> {
            run.control().ensureActive();
            if (toolFailure != null) {
                throw new IllegalStateException("本批工具已因异常终止", toolFailure);
            }
            var admission = admitted.remove(request);
            if (admission == null) {
                throw new IllegalStateException("工具未通过本轮整组预检或已执行");
            }
            AgentToolExecutionResult result;
            try {
                result = invoker.invokeStructured(admission, invocation);
            } catch (RuntimeException error) {
                // 响应式框架会收集同批异常；首个意外异常后其余动作不能继续产生副作用。
                toolFailure = error;
                throw error;
            }
            var advice = recovery.advise(request.name(), result);
            if (advice.hasInstruction()) {
                int count = failures.merge(advice.type(), 1, Integer::sum);
                answerOnly |= count > advice.maxAttempts();
            }
            answerOnly |= result.successful() && "askUserForInfo".equals(request.name());
            var step = new AnalysisResponse.ThinkingStep(run.snapshot().iterations(), "tool_call", "调用工具");
            step.setToolName(request.name());
            String observation = recovery.appendAdvice(request.name(), result);
            if (result.successful() && result.attributes().get(com.ai.skill.SkillCatalog.ACTIVATED_ATTRIBUTE) instanceof String id) {
                var unavailable = skills.dependenciesFor(id).stream().filter(name -> !invocation.allows(name)).sorted().toList();
                if (!unavailable.isEmpty()) observation += "\n本次未获授权的技能依赖：" + unavailable
                        + "。不得假装执行，需用户补齐能力绑定或说明本次上下文限制。";
            }
            step.setToolResult(observation.substring(0, Math.min(observation.length(), 300)));
            steps.add(step);
            String text = "__MEMORY_INDEXED__".equals(observation) ? "记忆已保存，请继续回答用户的问题。" : observation;
            return ToolExecutionResult.builder().resultText(text)
                    .attributes(result.successful() ? result.attributes() : Map.of()).build();
        });
    }
}
