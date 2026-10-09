package com.ai.agent;

import com.ai.mcp.McpModelService;
import com.ai.mcp.ModelInvocationException;
import com.ai.agent.tool.ai.GovernedToolSession;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.AiServiceStreamingEvent;
import com.ai.config.TaskContextSnapshot;
import reactor.core.publisher.Flux;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 用框架创建聊天和工具助手，每个 Run 使用独立消息窗口。
 * 已鉴权的 MySQL/Redis 会话快照是历史来源，窗口不另行落库，成功回答仍由统一会话记录器提交。
 */
@Component
public class AgentChatService {

    private final McpModelService modelService;

    public AgentChatService(McpModelService modelService) {
        this.modelService = modelService;
    }

    /**
     * 用 AiServices 执行同步对话，模型请求经过统一治理适配器。
     */
    public String chat(List<ChatMessage> messages, String modelId) {
        return chat(messages, modelId, null);
    }

    /**
     * AiServices 负责工具选择、原生结果回填和多轮推理，Session 负责治理。
     */
    public String chat(List<ChatMessage> messages, String modelId, GovernedToolSession tools) {
        var input = input(messages);
        return requireAnswer(assistant(input, modelId, tools).chat(input.systemPrompt(), input.question()).content());
    }

    /**
     * 消费框架 Flux，将原生文本增量交给 SSE 事件层。
     */
    public CompletableFuture<String> stream(List<ChatMessage> messages, String modelId, Consumer<String> onToken) {
        return stream(messages, modelId, onToken, null);
    }

    /**
     * 订阅 AiServices 的 Flux；只有 FinalResponseEvent 是要保存的回答，工具轮旁白不能拼进终答。
     * toFuture 只衔接 Run 的完成事务，doOnNext 已在过程中实时发送片段。
     */
    public CompletableFuture<String> stream(List<ChatMessage> messages, String modelId,
                                            Consumer<String> onToken, GovernedToolSession tools) {
        var context = TaskContextSnapshot.capture();
        return Flux.defer(() -> context.call(() -> {
                    var input = input(messages);
                    return assistant(input, modelId, tools).stream(input.systemPrompt(), input.question());
                }))
                .doOnNext(event -> context.wrap(() -> {
                    if (event instanceof AiServiceStreamingEvent.PartialResponseEvent partial) {
                        onToken.accept(partial.partialResponse().text());
                    }
                }).run())
                .ofType(AiServiceStreamingEvent.FinalResponseEvent.class)
                .single()
                .map(event -> requireAnswer(event.chatResponse().aiMessage().text()))
                .toFuture();
    }

    private AgentChatAssistant assistant(AssistantInput input, String modelId, GovernedToolSession tools) {
        // 窗口保留本次角色快照与新增回答，Token 裁剪统一交给 AgentContextGovernor，避免双重淘汰。
        // 工具轮会追加多条消息，不按初始历史长度裁剪，避免当前用户问题被框架窗口淘汰。
        // 本地窗口只活一个 Run；真正送模前仍由 Token 上下文治理器整轮裁剪。
        var memory = MessageWindowChatMemory.builder()
                .maxMessages(Integer.MAX_VALUE)
                // 历史先预填、注解后生成指令；让框架把 SystemMessage 固定在首位。
                .alwaysKeepSystemMessageFirst(true)
                .build();
        // 系统消息统一由 @SystemMessage 创建；这里仅预填历史，防止两个来源相互覆盖。
        input.history().forEach(memory::add);
        var builder = AiServices.builder(AgentChatAssistant.class)
                .chatModel(tools == null ? modelService.chatModel(modelId)
                        : tools.chatModel(modelService.chatModel(modelId)))
                .streamingChatModel(tools == null ? modelService.streamingChatModel(modelId)
                        : tools.streamingModel(modelService.streamingChatModel(modelId)))
                .chatMemory(memory);
        if (tools != null) {
            builder.toolProvider(tools.toolProvider())
                    // 响应式工具循环默认使用框架公共执行池；指定直接执行器使本批入口串行。
                    // 真正的工具方法仍在 Pipeline 的有界池运行，不引入额外无界线程池。
                    .executeToolsConcurrently(Runnable::run)
                    // 可恢复的业务错误已由 Pipeline 生成脱敏观察；意外异常不能让框架转成原文送模。
                    .toolExecutionErrorHandler((error, context) -> {
                        throw propagate(error);
                    })
                    .toolArgumentsErrorHandler((error, context) -> {
                        throw propagate(error);
                    })
                    // 由根 Run 的 beforeIteration 产生统一终止原因；框架默认次数不能提前截断。
                    .maxToolCallingRoundTrips(Integer.MAX_VALUE)
                    .hallucinatedToolNameStrategy(request ->
                            ToolExecutionResultMessage.from(request, tools.execute(request)));
        }
        return builder.build();
    }

    private RuntimeException propagate(Throwable error) {
        return error instanceof RuntimeException runtime ? runtime
                : new ModelInvocationException("工具执行异常", error);
    }

    private AssistantInput input(List<ChatMessage> messages) {
        if (messages == null || messages.size() < 2
                || !(messages.get(0) instanceof SystemMessage system)
                || !(messages.get(messages.size() - 1) instanceof UserMessage user)) {
            throw new IllegalArgumentException("助手输入必须以系统指令开始、以当前用户问题结尾");
        }
        var history = messages.subList(1, messages.size() - 1);
        if (history.stream().anyMatch(SystemMessage.class::isInstance)) {
            throw new IllegalArgumentException("历史中不能再包含第二份系统指令");
        }
        return new AssistantInput(system.text(), user.singleText(), List.copyOf(history));
    }

    private record AssistantInput(String systemPrompt, String question, List<ChatMessage> history) {
    }

    private String requireAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            throw new ModelInvocationException("模型未返回有效回答");
        }
        return answer;
    }
}
