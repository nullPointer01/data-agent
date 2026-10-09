package com.ai.integration.mcp;

import com.ai.agent.runtime.AgentRunScope;
import com.ai.agent.tool.governance.AgentToolDescriptor;
import com.ai.agent.tool.governance.AgentToolRegistry.RegisteredTool;
import com.ai.agent.tool.governance.AgentToolRiskLevel;
import com.ai.security.OutboundUrlGuard;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.mcp.McpToolExecutor;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP 连接和工具规格适配：官方客户端处理协议，本目录只管理本地映射、生命周期与合同固定。
 * 远端工具不得直接作为 AiServices ToolProvider 使用，必须经过现有 Tool Pipeline。
 */
@Component
public class McpToolCatalog implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(McpToolCatalog.class);
    private static final String TIMEOUT_RESULT = "MCP_TOOL_TIMEOUT";
    private final Map<String, Binding> bindings;
    private final Map<String, ServerState> servers;
    private final OutboundUrlGuard urlGuard;
    // 目录发现与 HTTP 回调分池，防止等待网络的发现任务占满执行 HTTP 回调的线程。
    private final ThreadPoolExecutor discovery = pool("mcp-discovery-", 2, 16);
    private final ThreadPoolExecutor httpIo = pool("mcp-http-", 4, 64);
    private volatile boolean closed;

    public McpToolCatalog(McpIntegrationProperties properties, OutboundUrlGuard urlGuard) {
        this.urlGuard = urlGuard;
        Map<String, Binding> configured = new LinkedHashMap<>();
        Map<String, ServerState> states = new LinkedHashMap<>();
        properties.servers().forEach((id, configuration) -> {
            if (!configuration.enabled()) return;
            ServerState state = new ServerState(id, configuration);
            states.put(id, state);
            configuration.tools().forEach((alias, tool) -> {
                String name = McpIntegrationProperties.toolName(id, alias);
                if (configured.putIfAbsent(name, new Binding(name, state, tool)) != null) {
                    throw new IllegalArgumentException("MCP 工具名称重复");
                }
            });
        });
        bindings = Map.copyOf(configured);
        servers = Map.copyOf(states);
    }

    /** 固定配置名称不触网；Run 授权快照不应因为建立 MCP 连接而阻塞。 */
    public Set<String> configuredToolNames() { return bindings.keySet(); }

    /** 只有显式列入该服务租户范围的主体才能看到或执行工具。 */
    public boolean allowsTenant(String name, String tenantId) {
        Binding binding = bindings.get(name);
        return binding != null && tenantId != null && binding.server.config.allowedTenants().contains(tenantId);
    }

    /** 首次访问有界发现 Schema，失败冷却后可重试；已固化规格不会随远端热变更悄悄变化。 */
    public Optional<RegisteredTool> find(String name) {
        Binding binding = bindings.get(name);
        if (closed || binding == null) {
            return Optional.empty();
        }
        ServerState state = binding.server;
        if (state.pinned == null) {
            try { state.connection(); }
            catch (RuntimeException failure) { return Optional.empty(); }
        }
        Map<String, ToolSpecification> pinned = state.pinned;
        ToolSpecification remote = pinned == null ? null : pinned.get(binding.tool.remoteName());
        if (remote == null) {
            return Optional.empty();
        }
        var specification = ToolSpecification.builder().name(name)
                // 用部署者维护的描述，不注入远端 instructions、annotation、隐藏元数据或提示词。
                .description(binding.tool.description()).parameters(remote.parameters()).build();
        var descriptor = new AgentToolDescriptor(name, AgentToolRiskLevel.MEDIUM, true, true,
                false, state.config.toolTimeout(), 1, binding.tool.requiredPermission(), false, "", 20_000);
        return Optional.of(new RegisteredTool(specification, (request, memoryId) -> execute(binding, request), descriptor));
    }

    /** 安全的运维状态，不返回 endpoint、Authorization 或远端异常正文；不主动触发连接。 */
    public Map<String, String> statuses() {
        Map<String, String> result = new LinkedHashMap<>();
        servers.forEach((id, state) -> result.put(id, state.status));
        return Map.copyOf(result);
    }

    private String execute(Binding binding, ToolExecutionRequest request) {
        var run = AgentRunScope.requireCurrent();
        run.control().ensureActive();
        if (!allowsTenant(binding.name, run.tenantId())) throw new SecurityException("MCP 服务未向当前租户授权");
        ServerState state = binding.server;
        McpClient client = state.connection();
        CompletableFuture<dev.langchain4j.service.tool.ToolExecutionResult> inFlight = null;
        try {
            urlGuard.assertSafe(state.config.url());
            Map<String, ToolSpecification> live;
            try {
                live = bounded(() -> list(client), minimum(state.config.discoveryTimeout(), run.control().remainingTime()));
            } catch (RuntimeException failure) {
                state.disconnect("UNAVAILABLE");
                throw failure;
            }
            ToolSpecification expected = state.pinned.get(binding.tool.remoteName());
            if (!expected.equals(live.get(binding.tool.remoteName()))) {
                state.status = "SCHEMA_CHANGED";
                throw new IllegalStateException("MCP 工具合同已变更，请核对服务并重启后重新绑定");
            }
            state.status = "CONNECTED";
            run.control().ensureActive();
            // 官方固定名称执行器负责映射；模型只能填本地工具参数，不能选择远端服务和工具名。
            var executor = new McpToolExecutor(client, binding.tool.remoteName());
            inFlight = executor.executeAsync(request, InvocationContext.builder().build());
            var result = inFlight.get(Math.max(1L, minimum(state.config.toolTimeout(),
                    run.control().remainingTime()).toMillis()), TimeUnit.MILLISECONDS);
            run.control().ensureActive();
            // 该 SDK 版本把超时包装为普通文本结果（isError=false），必须先识别保留标记。
            if (TIMEOUT_RESULT.equals(result.resultText())) throw new TimeoutException("MCP 工具调用超时");
            if (result.isError()) {
                // isError 不能被降成成功字符串；远端错误正文不进入模型、日志和 Trace。
                throw new IllegalStateException("MCP 服务返回工具执行失败");
            }
            return result.resultText();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MCP 调用已中断", failure);
        } catch (TimeoutException failure) {
            throw new IllegalStateException("MCP 工具调用超时", failure);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            // 官方 SDK 的业务错误和网络错误都可能是 ToolExecutionException，不能只按外层类型断线。
            // 不向上携带异常原文，避免远端业务数据进入日志、模型或 Trace。
            if (cause instanceof ToolArgumentsException) throw new IllegalArgumentException("MCP 服务拒绝工具参数");
            while (cause instanceof ToolExecutionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof TimeoutException || cause instanceof java.net.http.HttpTimeoutException) {
                throw new IllegalStateException("MCP 工具调用超时", new TimeoutException("MCP 工具调用超时"));
            }
            if (cause instanceof java.io.IOException) state.disconnect("UNAVAILABLE");
            // 其余协议/业务错误保持连接；下次 tools/list 若确实失败再断连并冷却。
            throw new IllegalStateException("MCP 远端请求失败");
        } finally {
            if (inFlight != null && !inFlight.isDone()) inFlight.cancel(true);
        }
    }

    private final class ServerState {
        private final String id;
        private final McpIntegrationProperties.Server config;
        private volatile McpClient client;
        private volatile Map<String, ToolSpecification> pinned;
        private volatile String status = "NOT_CONNECTED";
        private long retryAfterNanos;

        private ServerState(String id, McpIntegrationProperties.Server config) { this.id = id; this.config = config; }

        private synchronized McpClient connection() {
            if (closed) throw new IllegalStateException("MCP 客户端已关闭");
            if (client != null) return client;
            if (System.nanoTime() < retryAfterNanos) throw new IllegalStateException("MCP 连接暂不可用，正在冷却");
            try {
                var loaded = connectBounded(config, id);
                if (pinned != null && !pinned.equals(loaded.tools)) {
                    closeQuietly(loaded.client);
                    throw new IllegalStateException("MCP 工具合同变化");
                }
                pinned = loaded.tools;
                client = loaded.client;
                status = "CONNECTED";
                LOG.info("MCP 服务已连接: server={}, mappedTools={}", id, config.tools().size());
                return client;
            } catch (RuntimeException failure) {
                retryAfterNanos = System.nanoTime() + config.reconnectCooldown().toNanos();
                status = "UNAVAILABLE";
                LOG.warn("MCP 服务暂不可用: server={}，本地工具继续可用", id);
                throw new IllegalStateException("MCP 服务暂不可用");
            }
        }

        private synchronized void disconnect(String reason) {
            closeQuietly(client);
            client = null;
            status = reason;
            retryAfterNanos = System.nanoTime() + config.reconnectCooldown().toNanos();
        }
    }

    private Loaded connectBounded(McpIntegrationProperties.Server config, String id) {
        var abandoned = new java.util.concurrent.atomic.AtomicBoolean();
        var created = new java.util.concurrent.atomic.AtomicReference<Loaded>();
        try {
            return bounded(() -> {
                Loaded loaded = connect(config, id);
                created.set(loaded);
                // 初始化恰好在调用方超时后完成时，仍由创建者关闭资源，不能遗留孤立客户端。
                if (abandoned.get()) closeQuietly(loaded.client);
                return loaded;
            }, config.discoveryTimeout());
        } catch (RuntimeException failure) {
            abandoned.set(true);
            Loaded loaded = created.get();
            if (loaded != null) closeQuietly(loaded.client);
            throw failure;
        }
    }

    private Loaded connect(McpIntegrationProperties.Server config, String id) {
        urlGuard.assertSafe(config.url());
        var transport = StreamableHttpMcpTransport.builder().url(config.url()).timeout(config.discoveryTimeout())
                .executor(httpIo).followRedirects(false).subsidiaryChannel(false)
                .customHeaders(config.authorization().isBlank() ? Map.of() : Map.of("Authorization", config.authorization()))
                .logRequests(false).logResponses(false).build();
        McpClient client = null;
        boolean completed = false;
        try {
            client = new DefaultMcpClient.Builder().key(id).transport(transport)
                    .initializationTimeout(config.discoveryTimeout()).protocolDetectionTimeout(Duration.ofMillis(500))
                    .toolExecutionTimeout(config.toolTimeout()).toolExecutionTimeoutErrorMessage(TIMEOUT_RESULT)
                    .autoHealthCheck(false).cacheToolList(false).logHandler(message -> { })
                    .subscribeToToolListChanges(false).subscribeToPromptListChanges(false)
                    .subscribeToResourceListChanges(false).multiRoundTripMaxRetries(0).build();
            var listed = list(client);
            Map<String, ToolSpecification> selected = new LinkedHashMap<>();
            for (var tool : config.tools().values()) {
                var specification = listed.get(tool.remoteName());
                if (specification == null) throw new IllegalStateException("MCP 配置工具未被服务发现");
                selected.put(tool.remoteName(), specification);
            }
            if (closed || Thread.currentThread().isInterrupted()) throw new IllegalStateException("MCP 发现已取消");
            completed = true;
            return new Loaded(client, Map.copyOf(selected));
        } finally {
            if (!completed) {
                closeQuietly(client);
                closeQuietly(transport);
            }
        }
    }

    private Map<String, ToolSpecification> list(McpClient client) {
        Map<String, ToolSpecification> result = new LinkedHashMap<>();
        for (var tool : client.listTools()) {
            if (result.putIfAbsent(tool.name(), tool) != null) throw new IllegalStateException("MCP 服务返回重复工具名");
            if (result.size() > 512) throw new IllegalStateException("MCP 工具目录超过本地上限");
        }
        return Map.copyOf(result);
    }

    private <T> T bounded(Callable<T> action, Duration timeout) {
        var future = discovery.submit(action);
        try {
            return future.get(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MCP 发现已中断", failure);
        } catch (TimeoutException failure) {
            throw new IllegalStateException("MCP 发现超时", failure);
        } catch (ExecutionException failure) {
            throw new IllegalStateException("MCP 发现失败");
        } finally {
            if (!future.isDone()) future.cancel(true);
        }
    }

    private static Duration minimum(Duration a, Duration b) { return a.compareTo(b) <= 0 ? a : b; }

    private static ThreadPoolExecutor pool(String prefix, int size, int capacity) {
        AtomicInteger ids = new AtomicInteger();
        return new ThreadPoolExecutor(size, size, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(capacity), task -> {
            Thread thread = new Thread(task, prefix + ids.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    private static void closeQuietly(AutoCloseable resource) {
        if (resource == null) return;
        try { resource.close(); }
        catch (Exception ignored) { /* 释放失败不能打印包含地址、凭据或远端原文的异常。 */ }
    }

    /** 应用关闭时释放全部 SDK 客户端和本组件拥有的有界线程池。 */
    @Override
    @PreDestroy
    public void close() {
        closed = true;
        discovery.shutdownNow();
        servers.values().forEach(state -> state.disconnect("CLOSED"));
        httpIo.shutdownNow();
    }

    private record Binding(String name, ServerState server, McpIntegrationProperties.Tool tool) { }
    private record Loaded(McpClient client, Map<String, ToolSpecification> tools) { }
}
