## Context

项目已经有统一 `AgentRunCoordinator`、内存 `AgentRunControl/Registry`、三种执行策略、类型化事件、Trace，以及统一 `AgentToolExecutionPipeline`。这些组件可以约束一次在线执行，但 `AgentRunRegistry` 只保存当前 JVM 的对象，`AgentRunStatus` 没有等待/恢复状态，Coordinator 会在策略返回后立即结束 Run。工具策略也只有允许或拒绝，没有可持久化的中断结果。

项目中的 `graphdemo` 展示了 `interruptBefore + resume` 概念，但它保存的是可变内存对象，没有租户、权限、持久化、并发领取、加密或失败恢复，不能直接用于生产链路。现有 MySQL、JPA、RBAC、`AuditLogService`、`APP_ENCRYPTION_KEY` 和 Trace 可以作为第三阶段基础，无需引入新框架。

本设计服务于三类人：发起 Agent 任务的普通用户、处理高风险动作的管理员/审批人、负责排障和恢复的运维开发者。用户之前明确要求不创建或运行自动化测试，也不执行 Maven/前端构建，因此设计将运行期验证风险显式保留。

## Goals / Non-Goals

**Goals:**

- 将等待审批的 Run 持久化，使连接断开和进程重启不丢失执行现场。
- 在现有 Tool Governance 内建立“准入成功但执行前暂停”的正式语义。
- 保证审批、恢复领取和沙箱写操作具有明确的状态转换、租户边界和幂等证据。
- 恢复时延续既有消息、预算和 `runId/toolCallId`，同时重新应用最新权限与风险策略。
- 用一个受控沙箱写工具展示完整 Human-in-the-loop 闭环。
- 保持无审批工具和现有 Chat/ReAct/Orchestrated 请求行为兼容。

**Non-Goals:**

- 不构建通用图引擎、BPMN 系统或任意节点编排 UI。
- 不支持多级会签、转交、加签、撤回或修改待执行参数。
- 不接入真实外部高风险写系统，不承诺跨系统 exactly-once。
- 不实现任意历史步骤回放或从任意节点修改状态后重跑。
- 不改变 RAG、Embedding、Memory、模型客户端或普通工具业务逻辑。

## Decisions

### 1. 扩展现有 Harness，而不是引入 LangGraph 或提升 graphdemo

生产入口继续由 `AgentRunCoordinator` 和执行策略管理，ReAct 原生工具调用仍经过 `AgentToolInvoker`。新增持久化 Store、Checkpoint Codec、Approval Service 和 Resume Worker，通过稳定接口接入现有边界。

`graphdemo` 保留为教学代码，不参与 Bean 装配。替代方案是引入 LangGraph4j 或自建通用 `StateGraph`，但当前真正缺少的是持久化与安全合同，不是另一套路由 DSL；新框架会同时扩大序列化、事件和迁移面。

### 2. 使用两张运行控制表和一张隔离演示表

新增 `agent_run_state` 保存一个 Run 的当前权威状态和 Checkpoint，新增 `agent_tool_approval` 保存每个待审批工具动作。演示工具使用独立 `hotel_rate_sandbox`，不得修改真实业务数据。

`agent_run_state` 核心字段：

- `run_id` 唯一、tenant/user/session/agent/mode
- `status`、`termination_reason`、`version`
- `checkpoint_ciphertext`、`checkpoint_schema_version`
- 迭代/模型/工具/Token 已用预算和剩余 active timeout
- `lease_owner`、`lease_until`
- `approval_expires_at`、created/updated/completed 时间
- 最终结果或安全错误摘要

`agent_tool_approval` 核心字段：

- `approval_id` 唯一，`tenant_id + run_id + tool_call_id` 唯一
- 工具名、风险、执行者标识、安全参数摘要
- 加密的原始工具请求，只供恢复执行读取
- `decision_status`、`execution_status`、版本
- requester/reviewer、决定备注和各阶段时间

不把可恢复状态塞进 `agent_execution_trace.shared_context_json`。Trace 面向完成后的观测，缺少可变状态、乐观锁和领取索引；复用它会混淆审计记录与工作队列。

### 3. Run 状态机增加持久化暂停与恢复状态

权威状态转换为：

```text
CREATED -> RUNNING
RUNNING -> WAITING_APPROVAL | COMPLETED | FAILED | CANCELLED | TIMED_OUT | BUDGET_EXHAUSTED
WAITING_APPROVAL -> RESUMING | REJECTED | EXPIRED | CANCELLED
RESUMING -> WAITING_APPROVAL | COMPLETED | FAILED | CANCELLED | TIMED_OUT | BUDGET_EXHAUSTED
```

每次转换都使用期望状态和 `version` 条件更新。`WAITING_APPROVAL` 是持久化非终态，但会结束当前 HTTP/SSE 执行占用；`REJECTED` 和 `EXPIRED` 是终态。内存 Registry 只作为当前节点正在执行的快速取消索引，不再作为 Run 是否存在的事实来源。

替代方案是把等待审批表达为一次失败，批准后创建全新 Run。这样实现简单，但会丢失原始 `runId`、预算、模型消息和工具调用相关性，也无法证明是从同一动作恢复。

### 4. Checkpoint 使用版本化可移植 DTO，并整体加密

不直接序列化 LangChain4j 实现类或 Spring Bean。定义版本化 `AgentRunCheckpoint` DTO，至少包含：

- 当前策略与 ReAct 执行位置
- 可移植 message 列表及 tool request/result 关系
- final answer、迭代和恢复计数
- 待处理工具请求与原执行者 allowlist
- Run limits、已用预算和剩余 active timeout
- 与 Registry/Policy 兼容性有关的 schema/catalog 版本

Checkpoint JSON 和待执行工具请求使用现有 AES-GCM 能力加密后写库；列表接口、Audit、Trace 和日志只使用已脱敏参数摘要。`APP_ENCRYPTION_KEY` 变化会导致旧 Checkpoint 无法恢复，因此生产密钥轮换必须采用多版本密钥方案，当前阶段只记录这一运维约束。

### 5. 人工等待与 active execution deadline 分离

当前 Run timeout 约束模型和工具实际执行时间，不应把审批人等待数小时算作 Agent 计算超时。进入 `WAITING_APPROVAL` 时保存剩余 active timeout；恢复后以 `now + remainingActiveTimeout` 重建 deadline，预算计数继续累计而不重置。

审批单使用独立 `approvalTtl`，默认保护性起点为 24 小时。定时清理器将过期待审批单和 Run 原子转为 `EXPIRED`，不会执行工具。该默认值必须后续根据业务 SLA 调整，不能宣称最优。

### 6. Tool Governance 增加显式审批合同和两阶段执行

`@AgentToolPolicy`/Descriptor 增加 `approvalRequired` 和 `approvalPermission`。审批不是由 `HIGH` 风险名称隐式推断；风险用于分类，是否审批是明确合同，避免某些 HIGH 只读查询和某些 MEDIUM 写动作被错误处理。

执行拆为：

```text
admit: registry -> schema -> principal/tenant/agent/risk -> approval policy
  approval not required -> consume budget -> execute
  approval required and no grant -> persist checkpoint + approval -> suspend
  valid grant -> re-admit with current policy -> consume budget -> execute
```

准入结果携带内部 `AdmittedToolCall`，包含已解析 Descriptor、结构化参数和安全摘要。原始参数不能通过事件或 DTO 暴露。批准前不调用反射执行器、不消耗 attempt 和工具预算。

ReAct Step Handler 在收到 `APPROVAL_REQUIRED` 后返回明确暂停结果，Loop 和 Coordinator 不把它当失败或最终回答；Coordinator 持久化成功后结束当前传输。普通 Tool 和 Orchestrator 三个只读预检工具不改变路径。

### 7. 审批不能授予调用者原本没有的权限

新增 `agent:approval:review` 权限并默认只赋给 ADMIN。审批必须同时满足：

- 审批单、Run、审批人属于同一 tenant
- 审批单仍是 PENDING 且未过期
- 审批人拥有 `agent:approval:review`、工具所需权限和 Descriptor 的审批权限
- 审批人不是 Run 发起人，保持四眼原则

批准只表达对这一个不可修改动作的同意，不扩大 Run 主体权限。恢复时重新解析 Run 发起人和审批人的当前 RBAC；任何撤权、禁用用户、禁用 Tool、allowlist 变化或风险上限收紧都会阻止执行并形成安全终态。

拒绝和过期都销毁可继续执行资格，但保留加密 Checkpoint 到保留期结束用于审计；API 永远不返回密文。

### 8. 使用数据库 CAS + 租约实现恢复领取

批准事务只做 `PENDING -> APPROVED` 决策并提交审计，不在 HTTP 事务里直接执行模型或工具。Resume Worker 使用条件更新领取 Run：期望状态、版本匹配，且租约为空或已过期；成功后设置 `RESUMING + leaseOwner + leaseUntil`。

节点在恢复期间续租。扫描调度只负责发现候选并提交到独立有界恢复线程池，不能在调度线程中执行长模型或工具调用，否则会阻塞同一调度器上的续租。续租 CAS 失败时取消当前节点 Registry 中的 Run，使后续模型与工具准入停止；崩溃后其他节点只能在租约过期后重新领取。领取不等于外部副作用 exactly-once，因此每个恢复动作还携带稳定 `approvalId/toolCallId` 幂等键；沙箱表以该键建立唯一约束。未来真实连接器必须把键传给支持幂等的下游，或实现业务唯一约束和补偿。

替代方案是 JVM 锁、Redis 锁或消息队列。JVM 锁无法跨进程，Redis 锁不能单独证明数据库状态一致，消息队列会引入当前项目没有的基础设施。MySQL CAS 与现有部署最匹配，后续吞吐不足再演进 Outbox/MQ。

### 9. 恢复结果采用查询/轮询，不尝试复用旧 SSE 连接

新增：

- `GET /api/v1/agent-runs/{runId}`：所有者查看当前状态和安全结果
- `GET /api/v1/agent-approvals`：审批人按状态查看本租户审批单
- `GET /api/v1/agent-approvals/{approvalId}`：审批详情
- `POST /api/v1/agent-approvals/{approvalId}/approve`
- `POST /api/v1/agent-approvals/{approvalId}/reject`

原 SSE 在暂停时发送 additive `approval_required` 和 transport `done`，包含 IDs、状态和安全摘要，然后关闭。前端审批后轮询 Run 状态查看恢复结果。第一版不增加持久化事件日志和 SSE replay，避免把事件总线也纳入本阶段。

### 10. 沙箱工具用于证明动作闭环，不伪装生产能力

新增 `updateHotelPrice` 演示工具，标记为非只读、幂等、不可自动重试、需要审批。它只更新 `hotel_rate_sandbox`，使用 `approvalId/toolCallId` 唯一动作键防重，并返回变更前后价格和沙箱标记。

演示数据在初始化 SQL 中明确标注，不连接真实酒店、支付或消息系统。该工具默认只供显式配置的演示 Agent 使用，不因 Profile 空列表兼容语义自动暴露给所有 Agent；为此 server-enabled tools 和 demo feature flag 共同收窄目录。

### 11. Trace 与 Audit 分工

Run/工具 Trace 记录技术执行链和预算，Audit 记录谁在何时请求、批准、拒绝、过期或恢复。两者只保存安全摘要并共享 `runId/toolCallId/approvalId`。指标使用低基数状态、工具和结果标签，不以 ID、用户或备注作为 tag。

## Risks / Trade-offs

- [Checkpoint Schema 演进导致旧记录不可恢复] -> 每条记录保存 schema version，只支持明确迁移路径；不认识的版本进入安全失败并保留审计。
- [加密密钥变化导致密文不可解] -> 生产阶段禁止直接替换单密钥；后续设计 key version/rotation，本阶段启动文档明确约束。
- [JPA 乐观锁与自定义领取 SQL 行为不一致] -> 状态转换统一封装在 Store，领取使用明确的条件 update 并核对受影响行数，不允许业务 Service 直接 save 覆盖状态。
- [租约过短或恢复任务阻塞调度器造成重复领取] -> lease 必须大于扫描周期，恢复任务使用独立有界线程池，续租失败取消节点内 Run；真实停顿/GC 场景仍需人工运行验证。
- [工具已产生副作用但进程在记录成功前崩溃] -> 沙箱用业务唯一键恢复结果；真实系统仍必须提供下游幂等或对账补偿，不宣称通用 exactly-once。
- [审批积压导致密文和 Run 表增长] -> 审批 TTL、终态保留期和索引化查询；物理清理由后续运维策略执行。
- [Profile 空工具列表意味着全部工具] -> 审批演示工具额外受 feature flag/服务端启用集合约束，默认不扩大现有 Agent 能力。
- [无自动化测试和编译] -> 静态验收不能证明事务竞争、恢复序列化和框架 API；验证报告必须把这些列为未验证，等待用户允许人工运行。

## Migration Plan

1. 增加配置、状态枚举、持久化实体/Repository 和初始化 SQL；默认关闭 durable approval feature，旧请求行为不变。
2. 实现 Checkpoint Codec、Run Store 和状态 CAS，不接 Tool，先建立持久化生命周期边界。
3. 扩展 Tool Policy/Admission 和 ReAct 暂停协议；仍不启用演示写工具。
4. 增加审批服务、RBAC 权限、API、审计和 Resume Worker。
5. 增加隔离沙箱表与审批工具，显式开启 demo profile 后做人工闭环验证。
6. 增加审批前端与文档，完成静态安全/完整性验收。

回滚时先关闭 feature flag，停止产生新 WAITING Run；等待或人工终止存量 Run 后再回滚应用。新表是附加结构，不影响旧业务表，回滚代码时可暂时保留以便审计，不能直接删除未处理审批数据。

## Open Questions

- 24 小时审批 TTL、60 秒执行租约和 15 秒恢复扫描周期仅是保护性起点，需要真实运行数据后调优。
- Checkpoint 和审批终态记录保留多久，需要结合实际审计合规要求确定。
- 真实业务接入时，审批人是否必须拥有工具执行权限，还是单独的业务审批权限矩阵，需要按组织职责重新建模。
- 若未来需要实时恢复推送，应增加持久化事件序列和按 sequence replay，而不是尝试复用已经关闭的 SSE emitter。
