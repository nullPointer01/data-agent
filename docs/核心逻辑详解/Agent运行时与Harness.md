# Agent 运行时与 Harness

## 四个核心概念

- `AgentProfile`：一份可复用的 Agent 配置，绑定模型、System Prompt、Tools、Skills、数据源和执行模式。
- `Agent`：完成业务任务的执行能力，例如内置 ReAct、配置化 Agent 或专家 Agent。
- `AgentRun`：Agent 接受一次请求后产生的一次运行实例，拥有唯一 `runId`、状态、预算、deadline、事件和 Trace。
- `Harness`：包围 Agent 的工程化运行框架，负责路由、生命周期、资源限制、取消、上下文传播、事件和可观测性，不替代 Agent 的业务能力。

## 统一链路

```text
AnalysisController
  -> DataAnalysisAgent（参数、会话、文件准备）
  -> AgentRuntimeService（薄门面）
  -> AgentRunCoordinator
       1. AgentRunRouteResolver 只解析一次路线
       2. 创建 runId、RunControl、RBAC 工具权限快照、RunContext
       3. 注册并绑定 AgentRunScope
       4. Chat / ReAct / Orchestrated Strategy
       5. 选择唯一终态
       6. 统一记录会话和 Trace
       7. 发布 RUN_TERMINATED 并清理 Scope/Registry
```

同步和 SSE 只使用不同的 `AgentEventSink`，不会改变路由结果。路线优先级固定为：

```text
可识别斜杠命令 -> 显式 agentId -> 显式 skillId -> 默认 Orchestrator -> ReAct 回退
```

## 生命周期与预算

Run 状态只能按以下方向变化：

```text
CREATED -> RUNNING -> COMPLETED
                   -> FAILED
                   -> CANCELLED
                   -> TIMED_OUT
                   -> BUDGET_EXHAUSTED
                   -> WAITING_APPROVAL
WAITING_APPROVAL -> RESUMING | REJECTED | EXPIRED | CANCELLED
RESUMING -> WAITING_APPROVAL | COMPLETED | FAILED | CANCELLED | TIMED_OUT | BUDGET_EXHAUSTED
```

`AgentRunControl` 用原子操作竞争唯一终态，并管理五类服务端预算：总超时、最大 ReAct 迭代数、最大模型调用数、最大工具调用数和最大 Token 数。客户端不能覆盖这些上限。

模型和工具网关通过 `AgentRunScope` 获取当前 Run。模型调用在业务调用边界消耗一次名额，SDK 内部重试不会重复扣业务额度；返回后优先结算厂商 TokenUsage，缺失时使用 `TokenMonitor` 估算。Token 在响应返回后才能准确结算，因此单次在途请求可能产生 overshoot，但越界后不会再准入新模型或工具调用。

Orchestrator 工作线程通过 `ContextPropagatingTaskDecorator` 继承同一个 Run Context，各任务竞争共享原子预算；线程任务结束后恢复原上下文，避免线程池复用造成串 Run。

工具预算统计的是实际进入工具实现的尝试次数，而不是模型提出工具调用的次数。未知工具、参数非法、无权限和风险策略拒绝都发生在预算前；一次允许重试的逻辑调用可能消耗多个工具预算，但始终共享同一个 `toolCallId`。

## 事件与 SSE

内部统一使用类型化 `AgentEvent`，每个事件都带 `runId`、mode、事件时间和安全载荷。只有 `AgentRunCoordinator` 可以发布 `RUN_STARTED` 和 `RUN_TERMINATED`。`GuardedAgentEventSink` 接受第一个终态事件后拒绝后续事件。

`AgentSseEventWriter` 负责兼容旧前端协议：模型输出仍是 `token/content`，工具仍是 `tool_call/toolName/result`，编排仍是 `orchestration`，终态仍是 `done`；同时新增 `runId`、mode、status、terminationReason 和 usage。流式请求的第一个 Agent 事件是 `run_started`。

## 取消与 deadline

前端收到 `run_started` 后保存 `runId`。点击停止时先调用：

```text
POST /api/v1/analysis/runs/{runId}/cancel
```

然后中止浏览器流。SSE 超时、异常断开和后台 Future 中断也会取消同一个 Run。取消是协作式的：已经发给模型厂商或外部工具的阻塞请求不保证立即停止，但返回后不会准入下一次迭代、模型调用或工具调用。

`AgentRunRegistry` 只保存当前 JVM 的活跃 Run，并使用 tenantId + userId 校验显式取消。启用 durable 后，`agent_run_state` 是跨连接和重启的权威状态；Registry 仍只是当前节点的取消索引。

## 持久化暂停与恢复

审批型工具在参数、权限、allowlist 和风险策略准入后，不立即消耗工具预算或进入反射执行器。ReAct 把当前可移植消息、单个待审批工具请求、剩余 active timeout 和已用预算写入版本化 Checkpoint，再将 Run 置为 `WAITING_APPROVAL`。同一模型响应包含多个工具请求时，先整体预检；只持久化第一个审批动作，其他动作由恢复后的模型重新规划，避免产生部分副作用或不成对的 tool message。

管理员批准只改变审批决定为 `APPROVED/READY`。后台 worker 通过 Run 状态、版本和过期租约做 CAS 领取，进入 `RESUMING` 后交给独立有界线程池执行，调度线程只负责扫描和续租，不能被模型调用占住。续租失败会取消当前节点 Registry 中的 Run，使后续模型和工具边界停止准入；执行前还会重新读取发起人和审批人的启用状态与 RBAC，并重查 Tool Registry、原 Agent allowlist、审批权限和当前风险策略。任一条件失效都会 fail closed，不会把审批当作权限提升。

暂停时冻结的 active timeout 不包含人工等待时间，恢复后以“当前时间 + 剩余 active timeout”重建 deadline；迭代、模型调用、工具调用和 Token 已用量全部延续。审批有效期单独由 `approvalTtl` 控制。旧 SSE 在 `approval_required` 后关闭，恢复结果通过 `GET /api/v1/agent-runs/{runId}` 查询，当前没有事件 replay。

Checkpoint 与原工具请求使用 `APP_ENCRYPTION_KEY` 做 AES-GCM 加密。当前没有 key version，直接更换生产密钥会让等待中的记录不可恢复；密钥轮换前必须另行实现多版本解密和迁移。

## Trace

`runId` 直接复用为现有 `agent_execution_trace.trace_id`，不新增表或字段。Chat、ReAct、Orchestrated、命令和 Skill 都走 `recordRun`。运行模式、终态、耗时、迭代/模型/工具调用次数、Token 和 deadline 写入现有 JSON 元数据列；Orchestrator 计划、任务结果和安全的可见步骤继续保留。

Trace 与事件不保存 API Key、完整 System Prompt 或隐藏 Chain-of-Thought。Trace 落库失败只影响可观测性，不反转已经确定的业务终态。

每个 Run 还持有固定容量的 Tool Journal。Journal 只保存 `runId + toolCallId`、工具名、准入结果、尝试次数、耗时、风险和脱敏/截断标记；达到容量后丢弃最旧记录并增加 overflow 计数。它汇总进现有 `shared_context_json`，不新增数据库表。

## 当前边界

当前已实现 MySQL Checkpoint/Resume、单级人工审批和数据库恢复租约，但没有通用图引擎、多级会签、事件 replay 或跨系统 exactly-once。内置 `updateHotelPrice` 只操作隔离沙箱，以 `approvalId + toolCallId` 数据库唯一键证明本地去重；真实支付、发布、发消息或业务写入仍需把幂等键传给下游，并提供对账与补偿。timeout 和租约都是协作式边界，不能强制终止忽略线程中断的 JDBC/HTTP 请求。
