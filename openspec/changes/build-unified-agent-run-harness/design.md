## Context

Data Agent 当前已经具有 Harness 的主要零件，但没有统一的 Run 控制平面：

- `AgentRuntimeService` 在同步路径中默认优先调用 `OrchestratorAgent`，流式路径中默认直接调用 `ReActAgent`，传输方式意外参与了业务路由。
- `ConfigurableAgentExecutor` 自己区分 Chat/ReAct，`MultiAgentRuntimeService` 再做一次 Profile/Specialist 分发，默认 Agent 又由 `AgentRuntimeService` 单独分发。
- `ReActLoopRunner` 把最大轮数硬编码为 8；Orchestrator 只有 `maxPlanTasks`，没有整次运行共享的 deadline、取消信号和模型/工具调用预算。
- `ReActStreamEventWriter` 直接把内部行为序列化为 JSON，`AnalysisStreamService`、`AgentRuntimeService`、`ConfigurableAgentExecutor` 和 ReAct 路径都可能发送结束或错误事件。
- `AgentExecutionTraceService` 分别记录 Orchestrator 和 ReAct，Chat、命令、显式 Skill 与配置 Agent 没有统一轨迹结构。
- Orchestrator 可并行执行专家任务。运行计数与取消状态必须线程安全，并随现有 `TaskDecorator` 传播。

约束：Spring Boot 3.2、LangChain4j 1.19、JDK 17；保留现有分析 API、AgentProfile、Tools、Skills、RAG、Memory 与数据库表结构；当前阶段不新增或运行自动化测试，不执行 Maven 编译。

## Goals / Non-Goals

**Goals:**

- 为每次 Agent 执行建立唯一身份、确定模式、统一状态机和单一终态。
- 让 Chat、ReAct、Orchestrated 共享一个生命周期，传输层只决定事件如何交付。
- 在整个 Run 内统一执行迭代、模型调用、工具调用、Token 和总耗时预算。
- 支持 SSE 断开、deadline 和显式请求触发的协作式取消。
- 让内部事件类型化并统一关联 `runId`，同时保持现有 SSE 消费字段兼容。
- 用现有 Trace 表记录所有运行模式的终态与消耗，不增加数据库迁移。

**Non-Goals:**

- 不替换 LangChain4j，不引入其他 Agent 框架。
- 不增加新的 Agent 模式或重写现有专家、工具和 RAG 业务逻辑。
- 不实现跨节点取消、持久化 Run、Checkpoint/Resume 或任务队列。
- 不实现工具权限、风险分级、人工审批、重试和幂等策略。
- 不持久化或展示模型隐藏推理过程。

## Decisions

### 1. AgentProfile 与 AgentRun 分离

`AgentProfile` 继续描述长期配置：模型、system prompt、Tools、Skills、数据源和执行模式。新增的 `AgentRunContext` 只描述一次执行：

- `runId`、tenantId、userId、sessionId、agentId
- `AgentExecutionMode`：`CHAT`、`REACT`、`ORCHESTRATED`
- 原始 `AgentExecutionContext`
- `AgentRunLimits` 和线程安全的 `AgentRunControl`
- startedAt、deadline、当前状态和终止原因
- 迭代、模型调用、工具调用和 Token 消耗快照

`AgentRunStatus` 使用 `CREATED`、`RUNNING`、`COMPLETED`、`FAILED`、`CANCELLED`、`TIMED_OUT`、`BUDGET_EXHAUSTED`。只有 `CREATED -> RUNNING` 和 `RUNNING -> 任一终态` 合法；终态通过 CAS 竞争，保证并发完成、超时和断开只成功一次。

替代方案是把运行字段写入 `AgentProfile` 或 `AnalysisRequest`，但这会混淆持久配置与瞬时状态，并允许客户端控制服务端治理字段。

### 2. AgentRuntimeService 保留为门面，AgentRunCoordinator 成为生命周期唯一所有者

现有 Controller 和 `DataAnalysisAgent` 入口保持不变。`AgentRuntimeService` 将请求交给 `AgentRunCoordinator`，由 Coordinator 依次完成：

1. 生成 `runId`，解析服务端限制并注册活跃 Run；
2. 使用同一个 `AgentRunRouteResolver` 解析目标和执行模式；
3. 进入 Run Scope、标记 `RUNNING` 并发送 `RUN_STARTED`；
4. 调用对应执行策略或命令/Skill 快捷处理器；
5. 根据结果竞争终态，补充同步响应，发送唯一终态事件；
6. 记录统一 Trace，并在 `finally` 中清理 Scope 和活跃注册。

`AgentRunRouteResolver` 不接收“是否流式”参数。路由优先级继续兼容现状：斜杠命令、显式 Agent、显式 Skill、默认 Agent。显式 Agent 继续使用现有 `chat` 或 `react`；默认路径在 Orchestrator 可用时解析为 `ORCHESTRATED`，否则解析为 `REACT`。让持久化 AgentProfile 直接选择 `orchestrated` 涉及根 Agent 配置如何约束专家模型、Prompt、Tools 和 Skills，本阶段不扩展该语义。

Chat、ReAct、Orchestrated 通过薄的 `AgentExecutionStrategy` 适配现有执行器。策略可根据 Event Sink 是否支持 token 流选择同步或流式模型方法，但不得重新决定执行模式。

替代方案是在 `execute()` 和 `executeStreaming()` 内分别修复分支。它能修当前 bug，但后续新增预算、取消或模式时仍会再次漂移。

### 3. 显式上下文与受控 AgentRunScope 组合传播

Coordinator 和策略边界显式传递 `AgentRunContext`，便于阅读、编排和人工验证。深层公共网关 `McpModelService` 与 `AgentToolInvoker` 从 `AgentRunScope` 获取可选上下文，以统一拦截所有现有 Agent 调用而不改写几十个业务方法签名。

`AgentRunScope` 基于 JDK 17 `ThreadLocal`，但只允许 `runWith(context, supplier)` 风格的结构化绑定：进入前保存旧值，`finally` 恢复或清理。`ContextPropagatingTaskDecorator` 同时捕获安全上下文和 Agent Run，上交线程池前恢复，执行后清理。非 Agent 的模型调用没有 Run Scope，保持现有配额和记账行为。

替代方案一是只使用显式参数，类型最严格但会把运行时参数穿透 Memory、RAG、Specialist 和 Tool 内部；替代方案二是只用 ThreadLocal，改动少但依赖隐式且容易在线程池泄漏。组合方案把 Scope 限制在跨层网关和异步传播处。

### 4. AgentRunControl 统一预算与终止检查

`AgentRuntimeProperties` 使用 `app.agent.runtime.*` 绑定以下正数默认限制：

- `timeout`
- `max-iterations`
- `max-model-calls`
- `max-tool-calls`
- `max-tokens`

这些默认值是安全起点，不宣称经过效果调优。当前阶段不接收客户端覆盖，避免用户绕过服务端上限。

`AgentRunControl` 提供原子操作，而不是暴露可写计数器：

- `beforeIteration()`：检查终态、线程中断、取消、deadline，再竞争一个迭代名额；
- `beforeModelCall()`：执行相同检查，再竞争一个模型调用名额；
- `afterModelCall(actualOrEstimatedTokens)`：累计厂商真实 TokenUsage，缺失时复用 `TokenMonitor` 估算；
- `beforeToolCall()`：执行检查并竞争一个工具调用名额；
- `cancel(reason)`、`complete()`、`fail(error)`：竞争唯一终态；
- `snapshot()`：生成只读消耗快照供响应、事件和 Trace 使用。

Token 是调用完成后才能准确获得的计量项，因此本阶段是“调用边界硬停止”：调用前若已无剩余 Token 则拒绝，调用后真实或估算用量可能让总量超过上限，但不会再发起下一次外部调用。若未来要求单次调用也绝不越界，需要模型客户端支持按 Run 动态压低最大输出 Token，单独设计。

Orchestrator 并行任务共享同一个 `AgentRunControl`，名额通过原子递增竞争；没有为每个子任务静态切片，避免未使用配额浪费。顺序可能随并发竞争变化，但总调用上限稳定。

### 5. 取消采用节点内注册表和协作式中止

`AgentRunRegistry` 只保存当前 JVM 的活跃 Run，提供按 `runId`、tenantId、userId 校验后的取消操作。流式运行在 `RUN_STARTED` 事件中立即返回 `runId`；新增 `POST /api/v1/analysis/runs/{runId}/cancel` 供已知 runId 的客户端取消。SSE timeout、客户端断开和后台 Future 中断也调用同一个 `cancel()`。

取消或 deadline 不承诺强制杀死已经发送给模型厂商或外部工具的阻塞请求。请求返回后，下一边界检查必须停止后续执行并丢弃不应继续发布的业务事件。注册表不持久化，服务重启或请求打到其他节点时无法取消，这符合第一阶段单节点边界。

替代方案是只调用 `Future.cancel(true)`；许多 HTTP 客户端不会立即响应线程中断，且 ReAct/Orchestrator 无法得到可观察的取消终态。

### 6. 类型化 AgentEvent 是唯一内部事件合同

新增不可变 `AgentEvent`、`AgentEventType` 和 `AgentEventSink`。事件至少覆盖：

- `RUN_STARTED`
- `MODEL_TOKEN`
- `TOOL_CALL`
- `ORCHESTRATION`
- `BUDGET_UPDATED`
- `ERROR`
- `RUN_TERMINATED`

所有事件包含 `runId`、模式、时间和安全载荷。Coordinator 独占 `RUN_STARTED` 与 `RUN_TERMINATED`；下层只发送过程事件，不能发送 `done`。

同步调用使用收集/空 Sink 并返回最终 `AnalysisResponse`；SSE 使用序列化 Sink，把类型化事件转换成现有 `token`、`tool_call`、`orchestration`、`error`、`done` 等 `type`，并附加 `runId`、mode、status 和 usage。这样前端旧逻辑继续工作，新逻辑可读取统一字段。发送终态后 Sink 拒绝后续事件。

替代方案是继续让各执行器拼 Map 和 JSON，但无法静态约束事件字段，也无法防止重复 `done`。

### 7. runId 与现有 traceId 使用同一个关联标识

Run 创建时生成 UUID，统一 Trace 持久化时复用该值作为现有 `agent_execution_trace.trace_id`。这避免在无数据库迁移约束下维护两套关联 ID。同步响应新增 `runId`、`runStatus`、`executionMode` 和预算快照；已有 `traceId` 在 Trace 保存成功时与 `runId` 相同。

`AgentExecutionTraceService` 增加统一 `recordRun` 路径，所有模式写入共同字段。模式、终态、迭代/模型/工具/Token 消耗与 deadline 原因放入现有 JSON 元数据列；原有 Orchestrator 计划与 ReAct thinking step 摘要继续保留。Trace 失败不得改变已经确定的业务终态，但必须记录结构化错误。

Trace 和事件不得保存 API Key、完整 system prompt 或隐藏推理文本。可见工具结果继续遵循现有截断规则。

## Risks / Trade-offs

- [Scope 在线程池中泄漏或串 Run] → 只提供结构化绑定 API，所有入口和 TaskDecorator 使用 `try/finally` 恢复，禁止直接暴露 ThreadLocal 的 set/remove。
- [同步与流式虽同模式但底层调用方法不同] → 路由只解析一次；差异限制在策略内部的输出方式，并共享同一个 RunControl 与终态处理。
- [已发出的模型请求不能硬中断] → 明确采用协作式取消，在请求前后检查，并把终止原因暴露到事件和 Trace。
- [Token 用量可能单次越过上限] → 优先使用真实 TokenUsage、缺失时估算；越界后禁止后续调用，并在 Trace 标记 overshoot。
- [并行任务竞争预算导致完成顺序非确定] → 接受调度差异，保证原子总上限；后续如需任务级公平配额再扩展。
- [旧前端依赖既有 SSE type] → 序列化 Adapter 保留旧 type/字段，仅增加关联和终态字段；删除旧字段必须另行确认。
- [Trace 保存失败导致 traceId 无记录] → runId 仍用于日志和响应，持久化失败独立记录且不反转 Run 结果。
- [变更横跨多个执行器] → 分阶段迁移，每个检查点先完成结构与一个模式，再扩展到另外两种模式。

## Migration Plan

1. 新增 Run 模型、状态机、配置、Scope 和节点内 Registry，不接入现有执行路径。
2. 新增类型化事件与 SSE 兼容 Adapter，先保留现有事件输出。
3. 引入 Coordinator 和统一路由解析，把同步/流式入口切换到同一模式决定；先接 Chat，再接 ReAct 和 Orchestrated。
4. 在 ReAct 循环、`McpModelService` 和 `AgentToolInvoker` 接入共享 RunControl；将 Orchestrator 并行任务纳入 Scope 传播。
5. 移除下层重复终态事件，由 Coordinator 独占终态；接入 SSE 断开和显式取消。
6. 将 Trace 收敛到 `recordRun`，补充响应和事件元数据，然后删除不再使用的分支代码。

每一步都保持现有 API 可调用。若迁移阶段出现问题，可以让 `AgentRuntimeService` 临时回到旧执行分支；没有数据库迁移需要回滚。新配置均提供服务端默认值。

## Open Questions

- 第一阶段的预算默认值是保护性起点，后续需要依据 Trace 中的 P95 轮数、调用次数、Token 和耗时分布调优。
- 多实例部署后的跨节点取消与持久化 Run Registry 需要 Redis、数据库或任务系统支持，不在本变更决定。
- 单次模型调用的严格 Token 预留与动态 maxTokens 需要验证 LangChain4j 1.19 对各兼容厂商的请求级参数支持，再单独提案。
- 工具风险分级、人工确认和幂等键应建立在本次 `runId + AgentRunControl` 之上，作为下一阶段能力。
