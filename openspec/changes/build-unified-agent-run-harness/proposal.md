## Why

当前系统已经具备 Chat、ReAct、Orchestrator、自定义 Agent、Tools、Skills、Memory、RAG 和执行追踪，但每条执行路径分别管理循环、流式事件、失败处理和轨迹记录：默认同步请求优先进入 Orchestrator，默认流式请求却直接进入 ReAct，ReAct 轮数固定为 8，也没有贯穿整次运行的 deadline、取消信号和模型/工具调用预算。这使系统具备了 Agent 功能，却还缺少企业级 Harness 应有的统一生命周期和治理边界。

本变更引入统一 Agent Run 控制层，让 Agent 继续描述“谁来执行”，让 Harness 统一负责“如何可靠地执行”，为后续工具审批、断点恢复和轨迹评测建立稳定基础。

## What Changes

- 建立统一 `AgentRunContext`，为每次执行分配 `runId`，携带 Agent、会话、执行模式、deadline、取消状态、预算快照和运行状态。
- 建立明确且单向的 Agent Run 状态机，统一表达创建、运行、成功、失败、取消、超时和预算耗尽等结果。
- 将 Chat、ReAct、Orchestrated 收敛为同一 Harness 下的执行策略；斜杠命令和显式 Skill 仍保留现有快捷路由，但纳入同一 Run 生命周期。
- 统一同步和流式入口的模式选择规则，禁止相同请求仅因传输方式不同而从 Orchestrator 切换到 ReAct。
- 将 ReAct 固定 8 轮和 Orchestrator 独立任务上限收敛为可配置的运行预算，并增加模型调用、工具调用、Token 和总耗时预算。
- 在模型调用、工具调用和循环边界增加预算与取消检查；达到限制后停止发起新的外部调用，并返回可辨识的终态和原因。
- 建立与传输层解耦的统一 Agent Event，覆盖运行状态、模型输出、工具调用、编排任务、预算变化、错误和终态；SSE 只负责序列化这些事件。
- 将统一 `runId`、执行模式、终态、耗时和预算消耗接入现有执行 Trace，不在第一阶段引入新的数据库工作流。
- 保持现有分析 API、Agent 配置、Tools、Skills、RAG、Memory 和模型接入协议兼容。

## Capabilities

### New Capabilities

- `agent-run-harness`: 定义统一 Agent Run 生命周期、执行策略选择、运行预算、deadline/取消、事件流、终态语义和 Trace 关联。

### Modified Capabilities

无。当前仓库没有已发布的 Agent Runtime capability，本次以新能力规格描述现有执行链路的收敛行为。

## Impact

- 后端：`com.ai.agent` 运行入口与上下文、`com.ai.agent.react` 循环控制、`com.ai.agent.orchestrator` 执行接入、模型/工具调用边界、现有 Trace 服务。
- 配置：新增 `app.agent.runtime.*` 运行预算与 deadline 配置；现有 ReAct 和 Orchestrator 参数需要明确兼容与迁移规则。
- API：保留 `/api/v1/analysis/analyze` 与 `/api/v1/analysis/analyze/stream`，新增节点内运行取消端点；响应和 SSE 事件可增加 `runId`、终态和预算相关字段，但不移除现有字段。
- 数据：第一阶段复用现有 `agent_execution_trace` 能力，不引入 Checkpoint、任务队列表或新的恢复表。
- 依赖：继续使用 Spring Boot 3.2、LangChain4j 1.19 和 JDK 17，不引入新的 Agent 框架。

## Objective And Scope

目标是把现有 Agent 功能收敛为一个可治理、可观测、可停止的执行运行时，使相同 Agent 请求在同步和流式入口拥有一致的执行语义，并且任何循环或外部调用都受统一预算控制。

### Anti-Scope

- 不新增第四种 Agent 模式，也不重写现有 ReAct、Orchestrator、Tool、Skill、RAG 或 Memory 的业务能力。
- 不在第一阶段实现数据库 Checkpoint、跨进程 Resume、分布式任务队列或跨节点取消。
- 不在第一阶段实现高风险工具人工审批、工具权限模型、自动重试和幂等治理；这些建立在统一 Run 之上，后续单独提案。
- 不要求底层厂商暴露真实推理过程，不把内部 Chain-of-Thought 作为事件或 Trace 对外输出。
- 不修改模型供应商和 Embedding 接入方案。

### Quantified Success Criteria

- SC-1：Chat、ReAct、Orchestrated 三种执行模式全部通过同一个 Agent Run 入口创建上下文，且每次请求只有 1 个稳定 `runId`。
- SC-2：相同请求在同步和流式入口选择相同执行模式；传输方式不得改变 Agent 路由结果。
- SC-3：运行状态只能按 `CREATED -> RUNNING -> COMPLETED | FAILED | CANCELLED | TIMED_OUT | BUDGET_EXHAUSTED` 转移，每次运行最多产生 1 个终态。
- SC-4：每次运行至少支持 5 类限制：最大迭代数、最大模型调用数、最大工具调用数、最大 Token 数和总 deadline；所有限制必须可通过配置设置正数默认值。
- SC-5：每次模型或工具调用前都执行 deadline、取消和对应预算检查；触发终止条件后新增外部调用数为 0。
- SC-6：同步响应与流式 `done` 事件都能关联同一个 `runId`、执行模式和终态；流式链路的终态事件恰好发送 1 次。
- SC-7：现有两个分析 API、已有 AgentProfile 数据及 Chat/ReAct/Orchestrated 核心能力无需数据库迁移即可继续使用。
- SC-8：现有执行 Trace 能按 `runId` 关联运行模式、终态、总耗时、实际迭代数、模型调用数和工具调用数；不记录 API Key、完整 system prompt 或隐藏推理文本。

## Assumptions And Boundaries

详细假设记录在 `sdd-assumptions.md`。

- Always：保持租户和会话边界；所有运行限制使用服务端上限，不能由请求绕过。
- Always：内部执行事件与 SSE 传输解耦；同步和流式只允许表现形式不同，不允许路由语义不同。
- Always：终止采用协作式取消；无法中断已经发出的阻塞式厂商请求时，返回后必须丢弃后续执行并进入正确终态。
- Ask first：新增数据库表、持久化 Run、跨节点取消、Checkpoint/Resume 或外部任务队列。
- Ask first：改变已有 API 字段语义、删除旧 SSE 事件或修改前端消费协议。
- Never：无限循环、无限工具调用、将客户端预算直接当作可信服务端上限，或在 Trace 中暴露密钥和隐藏推理过程。

## Verification Strategy

遵循用户当前项目约束，本变更不创建或运行自动化测试，不执行 Maven 编译。实现阶段采用 OpenSpec 严格校验、静态调用链检查和人工场景验收：分别核对三种模式的同步/流式路由、五类预算终止、主动取消、deadline、单一终态、Trace 字段以及现有 API 兼容性。若用户后续改变测试约束，再单独补充适配 JDK 17 的单元或集成测试。
