## 1. Run Domain And Configuration

- [x] 1.1 定义 Agent Run 的模式、状态、终止原因和不可变限制。
  - Acceptance: `CHAT/REACT/ORCHESTRATED` 与 7 个状态定义唯一；终止原因覆盖失败、取消、超时和三类计数/Token 预算；限制对象只接受正数。
  - Verify: 静态核对枚举、终态判断与不可变限制构造校验。
  - Files: `src/main/java/com/ai/agent/runtime/AgentExecutionMode.java`, `src/main/java/com/ai/agent/runtime/AgentRunStatus.java`, `src/main/java/com/ai/agent/runtime/AgentRunTerminationReason.java`, `src/main/java/com/ai/agent/runtime/AgentRunLimits.java`
  - Covers: SC-1, SC-3, SC-4

- [x] 1.2 绑定 Agent Runtime 的五类服务端预算配置。
  - Acceptance: `app.agent.runtime.*` 提供 timeout、iteration/model/tool/token 五类正数默认限制；配置非法时启动绑定明确失败；不接受客户端扩大上限。
  - Verify: 静态核对 `@ConfigurationProperties` 校验、默认值和 `application.yml` 占位符回退值，不运行 Maven。
  - Files: `src/main/java/com/ai/agent/runtime/AgentRuntimeProperties.java`, `src/main/resources/application.yml`, `src/main/java/com/ai/DataAgentApplication.java`
  - Covers: SC-4

- [x] 1.3 实现线程安全的 AgentRunControl 和只读 AgentRunSnapshot。
  - Acceptance: 状态只允许 `CREATED -> RUNNING -> 单一终态`；iteration/model/tool 使用原子准入；Token 真实/估算记账、deadline、线程中断和取消都收敛为稳定终止原因。
  - Verify: 逐项人工推演成功、异常、并发预算竞争、Token 越界、超时与取消状态转换。
  - Files: `src/main/java/com/ai/agent/runtime/AgentRunControl.java`, `src/main/java/com/ai/agent/runtime/AgentRunSnapshot.java`, `src/main/java/com/ai/agent/runtime/AgentRunTerminatedException.java`
  - Covers: SC-3, SC-4, SC-5

- [x] 1.4 建立 AgentRunContext、结构化 Scope 和节点内活跃 Run Registry。
  - Acceptance: Run Context 固定 run/tenant/user/session/agent/mode；Scope 嵌套后恢复父上下文并在异常时清理；Registry 只能由同租户同用户查看或取消活跃 Run。
  - Verify: 静态检查所有 Scope 绑定都有 `finally` 清理，Registry 在终态后移除且不泄漏其他用户运行信息。
  - Files: `src/main/java/com/ai/agent/runtime/AgentRunContext.java`, `src/main/java/com/ai/agent/runtime/AgentRunScope.java`, `src/main/java/com/ai/agent/runtime/AgentRunRegistry.java`, `src/main/java/com/ai/security/SecurityContextHelper.java`
  - Covers: SC-1, SC-5, SC-6

## 2. Unified Events

- [x] 2.1 定义传输无关的 AgentEvent、AgentEventType 与单终态 AgentEventSink。
  - Acceptance: 每个事件携带 runId/mode/time/type；Coordinator 专属 start/terminal；Sink 接受首个终态后拒绝所有后续事件。
  - Verify: 静态检查过程事件不包含 API Key、完整 system prompt 或隐藏推理字段，人工推演重复终态竞争。
  - Files: `src/main/java/com/ai/agent/runtime/event/AgentEvent.java`, `src/main/java/com/ai/agent/runtime/event/AgentEventType.java`, `src/main/java/com/ai/agent/runtime/event/AgentEventSink.java`, `src/main/java/com/ai/agent/runtime/event/GuardedAgentEventSink.java`
  - Covers: SC-1, SC-7, SC-8

- [x] 2.2 实现 SSE 兼容 Adapter，将类型化事件映射到现有事件协议。
  - Acceptance: 保留 `token/tool_call/orchestration/error/done` 等既有 type 与字段；新增 runId/mode/status/usage；每个 started run 恰好一个 done。
  - Verify: 对照当前前端事件消费代码逐项检查旧字段兼容和终态后的事件拒绝。
  - Files: `src/main/java/com/ai/agent/runtime/event/AgentSseEventWriter.java`, `src/main/java/com/ai/agent/react/ReActStreamEventWriter.java`, `frontend/src/pages/ChatPage.jsx`
  - Covers: SC-6, SC-7

## 3. Routing And Coordination

- [x] 3.1 实现与传输方式无关的 AgentRunRouteResolver 和执行策略合同。
  - Acceptance: 保持 command -> explicit agent -> explicit skill -> default 优先级；Profile 的 chat/react 与默认 Orchestrated/ReAct 解析稳定；Resolver 不接收 streaming 标记。
  - Verify: 用同步与流式两组相同请求人工走查解析结果，确认传输方式不进入路由条件。
  - Files: `src/main/java/com/ai/agent/runtime/AgentRunRoute.java`, `src/main/java/com/ai/agent/runtime/AgentRunRouteResolver.java`, `src/main/java/com/ai/agent/runtime/AgentExecutionStrategy.java`, `src/main/java/com/ai/service/AgentProfileService.java`
  - Covers: SC-2, SC-7

- [x] 3.2 为 Chat、ReAct、Orchestrated 增加薄策略适配器，复用现有执行器。
  - Acceptance: 三种策略共享 AgentRunContext 与 Event Sink；策略只决定执行机制，不重新路由；现有 Profile 模型/Prompt/Tools/Skills/Memory 语义保持。
  - Verify: 静态追踪每种模式到现有 `ConfigurableAgentExecutor`、`ReActAgent` 或 `OrchestratorAgent` 的委托链。
  - Files: `src/main/java/com/ai/agent/runtime/ChatExecutionStrategy.java`, `src/main/java/com/ai/agent/runtime/ReActExecutionStrategy.java`, `src/main/java/com/ai/agent/runtime/OrchestratedExecutionStrategy.java`, `src/main/java/com/ai/agent/ConfigurableAgentExecutor.java`, `src/main/java/com/ai/agent/orchestrator/OrchestratorAgent.java`
  - Covers: SC-2, SC-7

- [x] 3.3 实现 AgentRunCoordinator，并把同步与流式 AgentRuntimeService 收敛到同一生命周期。
  - Acceptance: Coordinator 唯一负责创建、注册、启动、路由、终止和清理 Run；同步/流式共享同一次模式决策；命令与显式 Skill 也进入共同生命周期。
  - Verify: 静态检查 `AgentRuntimeService.execute*` 不再各自包含默认模式分支，所有退出路径都经过 Coordinator `finally`。
  - Files: `src/main/java/com/ai/agent/runtime/AgentRunCoordinator.java`, `src/main/java/com/ai/agent/AgentRuntimeService.java`, `src/main/java/com/ai/agent/SkillExecutionService.java`, `src/main/java/com/ai/service/MultiAgentRuntimeService.java`
  - Covers: SC-1, SC-2, SC-3, SC-7

## 4. Budget Enforcement

- [x] 4.1 在 McpModelService 的所有 Agent 模型调用入口接入 Run Scope 准入与 Token 结算。
  - Acceptance: 有 Scope 时调用前原子消耗 model 名额并检查终态/deadline/token；调用后优先使用真实 TokenUsage、缺失时估算并标记；无 Scope 调用保持原行为。
  - Verify: 搜索 `McpModelService` 的同步、JSON、消息和流式入口，确认它们共享一个 guard/settlement 路径且重试不会被误算成多个业务调用。
  - Files: `src/main/java/com/ai/mcp/McpModelService.java`, `src/main/java/com/ai/mcp/TokenMonitor.java`, `src/main/java/com/ai/agent/runtime/AgentRunControl.java`, `src/main/java/com/ai/agent/runtime/AgentRunScope.java`
  - Covers: SC-4, SC-5

- [x] 4.2 在 AgentToolInvoker 与 ReAct 循环接入工具和迭代预算，删除硬编码 8 轮。
  - Acceptance: 工具实现只在 tool 准入成功后调用；同步/流式 ReAct 每轮使用相同 iteration 准入；预算耗尽返回统一终态，不再追加“正常完成”结果。
  - Verify: 搜索 `MAX_ITERATIONS` 和所有 `toolInvoker.invoke` 调用，确认不存在绕过 RunControl 的 Agent 工具入口。
  - Files: `src/main/java/com/ai/agent/tool/AgentToolInvoker.java`, `src/main/java/com/ai/agent/react/ReActLoopRunner.java`, `src/main/java/com/ai/agent/react/ReActStepHandler.java`, `src/main/java/com/ai/agent/orchestrator/ParallelPlanExecutor.java`
  - Covers: SC-3, SC-4, SC-5

- [x] 4.3 将 Agent Run Scope 传播到 Orchestrator 并行任务并处理未启动任务取消。
  - Acceptance: 每个专家子任务读取父 runId 并竞争同一预算；线程复用后 Scope 清空；Run 终止后排队任务不执行专家、模型或工具逻辑。
  - Verify: 静态检查 TaskDecorator 捕获/恢复/清理顺序，人工推演两个并发任务争夺最后一个模型名额。
  - Files: `src/main/java/com/ai/config/ContextPropagatingTaskDecorator.java`, `src/main/java/com/ai/agent/orchestrator/ParallelTaskExecutor.java`, `src/main/java/com/ai/agent/orchestrator/ParallelPlanExecutor.java`, `src/main/java/com/ai/agent/AgentExecutionRequest.java`
  - Covers: SC-4, SC-6

## 5. Cancellation And Transport Lifecycle

- [x] 5.1 提供节点内 Run 取消服务和受租户/用户约束的取消 API。
  - Acceptance: 已知 runId 的所有者可取消活跃 Run；其他租户/用户不能取消或探测其存在；已终止或未知 Run 返回稳定安全结果；不新增数据库表。
  - Verify: 静态检查身份读取、Registry 条件匹配、幂等取消和响应不泄露运行载荷。
  - Files: `src/main/java/com/ai/controller/AnalysisController.java`, `src/main/java/com/ai/agent/runtime/AgentRunCancellationService.java`, `src/main/java/com/ai/agent/runtime/dto/AgentRunCancellationResponse.java`, `src/main/java/com/ai/agent/runtime/AgentRunRegistry.java`
  - Covers: SC-5

- [x] 5.2 让 SSE 启动、断开、超时和 Future 中断驱动统一 Run 生命周期。
  - Acceptance: 首个 Agent 事件是 RUN_STARTED；断开/超时同时中断任务并取消对应 Run；所有失败和取消路径最终只发送一次 done；下层不再独立发送 done。
  - Verify: 静态枚举 `SseEmitter` completion/timeout/error、异步异常和正常完成路径，确认每条路径关联相同 runId。
  - Files: `src/main/java/com/ai/service/AnalysisStreamService.java`, `src/main/java/com/ai/agent/DataAnalysisAgent.java`, `src/main/java/com/ai/agent/DataAnalysisAgentImpl.java`, `src/main/java/com/ai/agent/runtime/AgentRunCoordinator.java`, `src/main/java/com/ai/agent/runtime/event/AgentSseEventWriter.java`
  - Covers: SC-1, SC-3, SC-5, SC-7

## 6. Response, Trace And Compatibility

- [x] 6.1 扩展 AnalysisResponse 并将现有 ReAct/Orchestrator Trace 收敛为 recordRun。
  - Acceptance: 响应包含 runId/mode/status/usage；Trace 使用 runId 作为 traceId，覆盖 Chat/ReAct/Orchestrated/命令/Skill，并保留安全的现有计划或步骤摘要；持久化失败不反转业务终态。
  - Verify: 静态核对所有运行模式的 trace 调用和 JSON 元数据，扫描 API Key、完整 system prompt、chain-of-thought 泄露风险。
  - Files: `src/main/java/com/ai/model/AnalysisResponse.java`, `src/main/java/com/ai/service/AgentExecutionTraceService.java`, `src/main/java/com/ai/model/AgentExecutionTrace.java`, `src/main/java/com/ai/agent/dto/AgentExecutionTraceResponse.java`, `src/main/java/com/ai/agent/runtime/AgentRunSnapshot.java`
  - Covers: SC-1, SC-3, SC-7, SC-8

- [x] 6.2 清理各执行器重复终态和独立 trace 分支，保持会话记录与前端兼容。
  - Acceptance: Coordinator 是唯一终态事件发送方；会话只记录一次；旧 SSE type、sessionId、result、thinkingSteps 和 traceId 消费保持；所有模式终止后均清理 Registry/Scope。
  - Verify: 搜索 `emitDone`、`recordReAct`、`traceService.record` 和会话记录调用，逐个确认所有权唯一且无遗漏路径。
  - Files: `src/main/java/com/ai/agent/AgentRuntimeService.java`, `src/main/java/com/ai/agent/ConfigurableAgentExecutor.java`, `src/main/java/com/ai/agent/react/ReActAgent.java`, `src/main/java/com/ai/agent/react/ReActStreamEventWriter.java`, `src/main/java/com/ai/agent/tool/AgentConversationRecorder.java`
  - Covers: SC-3, SC-7, SC-8

## 7. Documentation And Manual Verification

- [x] 7.1 更新 Agent Harness 架构、配置和演化边界文档。
  - Acceptance: 文档区分 Agent/Profile/Run/Harness，描述统一路由、五类预算、协作式取消、事件、Trace 与单节点限制，并把工具治理和 Checkpoint 标为后续阶段。
  - Verify: 对照 proposal/design/spec 检查术语和边界一致，不宣称预算默认值已调优或支持跨节点恢复。
  - Files: `CLAUDE.md`, `README.md`, `docs/核心逻辑详解/Agent运行时与Harness.md`, `docs/核心逻辑详解/目录索引.md`, `docs/演化/常见陷阱.md`
  - Covers: SC-1, SC-2, SC-3, SC-4, SC-5, SC-6, SC-7, SC-8

- [x] 7.2 执行无编译、无自动化测试的静态验收并记录结果。
  - Acceptance: OpenSpec strict validation、状态机/路由/预算/Scope/事件/Trace 调用链检查、敏感信息扫描和 diff integrity 完成；明确记录未执行的 Maven 编译、自动化测试和真实长耗时模型场景。
  - Verify: 生成 `verify-report.md`，逐项记录 SC-1..SC-8 的代码证据、人工场景结果和剩余风险。
  - Files: `openspec/changes/build-unified-agent-run-harness/verify-report.md`, `openspec/changes/build-unified-agent-run-harness/tasks.md`, `openspec/changes/build-unified-agent-run-harness/sdd-state.md`, `openspec/changes/build-unified-agent-run-harness/sdd-assumptions.md`
  - Covers: SC-1, SC-2, SC-3, SC-4, SC-5, SC-6, SC-7, SC-8

## Test Strategy Override

用户已明确要求后续默认不创建或运行测试，也不执行 Maven 编译。该项目不是 goods-ds，且用户指令优先于通用 SDD 测试模板，因此本 change 不创建 `src/test` 测试任务、不设置增量覆盖率目标；验证采用 OpenSpec 严格校验、静态调用链检查、敏感信息扫描与人工场景推演。只有用户后续明确改变约束时，才补充 JDK 17 对应的自动化测试或编译任务。
