## Why

统一 Agent Run 和 Tool Governance 已经能够限制循环、校验权限并安全执行工具，但 Run 仍只存在当前 JVM：服务重启后无法恢复，长时间等待不能脱离 SSE/HTTP 连接，高风险动作也只能立即执行或直接拒绝。企业 Agent 需要第三种结果——在动作前持久化现场、等待有权限的人审批，并在批准后安全地从原位置继续。

本变更把现有 Harness 从“受控的一次性执行”升级为“可暂停、可审批、可恢复的持久化执行”，让模型保持推理自主权，同时把写操作和高风险动作交给服务端状态机、权限、幂等和审计控制。

## What Changes

- 为 Agent Run 墕加持久化记录和不可逆状态转换，支持等待审批、恢复中、审批拒绝和审批过期等状态。
- 保存可恢复 Checkpoint，包括执行位置、可移植消息历史、待处理工具调用、预算用量、服务端执行者白名单和安全版本信息；敏感载荷加密存储。
- 为明确声明需要审批的工具建立持久化审批单；工具在批准前不得进入实现调用或消耗真实 attempt。
- 新增按租户和 RBAC 隔离的审批列表、详情、批准、拒绝接口，以及面向管理员的审批中心页面。
- 批准后重新解析当前用户、审批人、工具启用状态、风险策略和权限，不直接信任长时间保存的旧授权快照。
- 使用数据库条件更新、版本号、唯一动作键和有时限执行租约控制重复审批、多实例竞争、进程崩溃与恢复。
- 批准后的动作沿用现有 `AgentToolExecutionPipeline`，并把审批、恢复、执行结果统一关联到 `runId + toolCallId + approvalId`。
- 增加一个只作用于项目内沙箱数据的酒店价格调整演示工具，用于跑通“模型提议 -> 暂停 -> 审批 -> 恢复 -> 幂等执行 -> Trace/Audit”闭环。
- 保持现有 Chat、ReAct、Orchestrated、AgentProfile、RAG、Memory、模型接入和分析 API 可继续使用；无审批工具仍按当前同步/流式语义执行。

## Capabilities

### New Capabilities

- `durable-agent-run`: 定义持久化 Run 状态机、Checkpoint 契约、执行租约、进程重启恢复、预算延续和终态一致性。
- `agent-action-approval`: 定义工具审批声明、待审批动作、审批权限与时效、恢复前重校验、幂等执行和完整审计。

### Modified Capabilities

无。统一 Run 与 Tool Governance 的规格尚未归档到 `openspec/specs`；本变更以新 capability 依赖其当前实现，不在此伪造已发布规格。

## Impact

- 后端：`com.ai.agent.runtime`、`com.ai.agent.tool.governance`、ReAct 工具观察流程、新增持久化 Run/审批模块、恢复调度和管理接口。
- 前端：新增 Agent 审批中心，显示待审批动作、安全参数摘要、风险、申请人、过期时间和处理结果。
- 数据库：新增持久化 Agent Run/Checkpoint 与工具审批表；通过唯一约束、版本字段和状态条件更新实现节点间竞争控制。
- 安全：复用现有 Spring Security、租户、RBAC、`APP_ENCRYPTION_KEY` 和审计能力；审批人不能审批自己无权执行或跨租户的动作。
- API：保留现有分析接口，新增 Run 查询和审批接口；流式连接可在 `WAITING_APPROVAL` 后结束，批准后由新的连接查询或订阅恢复结果。
- 依赖：继续使用 JDK 17、Spring Boot 3.2、LangChain4j 1.19 和 MySQL，不引入 LangGraph 或新的 Agent 框架。

## Objective And Scope

目标是在不重写现有三种 Agent 执行模式的前提下，让需要审批的工具调用可以跨请求、跨连接和进程重启安全暂停与恢复，并以服务端状态机证明“未经批准绝不执行、同一批准动作不会被并发重复领取”。

### Anti-Scope

- 不自研通用 BPMN/工作流编排平台，不把教学用 `graphdemo` 直接提升为生产框架。
- 不接入真实支付、发消息、生产改价、删除业务数据等外部副作用；演示写工具只操作明确隔离的项目沙箱数据。
- 不承诺对不支持幂等键的外部系统实现全局 exactly-once；数据库状态机只能保证领取和本地提交边界。
- 不在本阶段实现任意历史节点回放、时间旅行调试、人工修改模型上下文或多级会签。
- 不改变普通只读工具、RAG、Embedding、Memory 和模型供应商行为。

### Quantified Success Criteria

- SC-1：100% 声明需要审批的工具在批准前实现调用次数为 0，且 Run 必须持久化为 `WAITING_APPROVAL`。
- SC-2：一个等待审批的 Run 在服务重启或原 SSE/HTTP 连接断开后，仍能通过 `runId` 读取状态并从同一 `toolCallId` 恢复。
- SC-3：每个 Checkpoint 至少保存执行位置、可恢复消息、预算快照、待处理动作、版本和过期时间；API Key、Bearer Token、数据库密码及未加密工具参数不得明文落库。
- SC-4：同一审批单只允许一次 `PENDING -> APPROVED | REJECTED | EXPIRED` 决策；并发审批只有 1 个请求成功改变状态。
- SC-5：批准后的恢复必须重新校验租户、Run 所有者、审批人权限、Tool 启用状态、当前 Agent 白名单和风险策略；任一失败时真实工具 attempt 为 0。
- SC-6：同一 `approvalId + toolCallId` 在并发恢复、重复请求和过期租约回收中最多只有 1 个活动执行持有者；沙箱写工具最终只产生 1 条业务变更结果。
- SC-7：审批、拒绝、过期、恢复领取、工具结果和终态都能按 `runId + toolCallId + approvalId` 在 Trace/Audit 中关联，且用户可见事件不重复宣称同一动作成功。
- SC-8：现有无需审批的 Agent 请求和工具继续使用当前接口与执行链；数据库升级后旧 AgentProfile、会话、Trace 和知识数据无需迁移内容即可使用。

## Assumptions And Boundaries

详细假设记录在 `sdd-assumptions.md`。

- Always：Checkpoint 是服务端事实，客户端只能提交审批决定和备注，不能修改恢复节点、工具名、参数、权限或预算。
- Always：恢复时重新授权；长期暂停期间的撤权、禁用工具和风险策略收紧必须立即生效。
- Always：所有状态转换使用数据库条件更新或乐观锁，所有恢复执行必须先持有未过期租约。
- Ask first：接入真实外部写系统、增加多级审批、修改审批角色模型或承诺跨系统 exactly-once。
- Ask first：引入消息队列、Redis 分布式协调或第三方工作流引擎。
- Never：把审批按钮当作唯一防重手段、明文保存敏感工具参数、批准后绕过 Tool Governance、允许用户审批跨租户动作。

## Verification Strategy

继续遵守用户当前项目约束：不创建或运行自动化测试，不执行 Maven 编译、打包或前端构建。实现验收采用 OpenSpec strict validation、状态转换和调用链静态检查、SQL 唯一约束/条件更新审查、权限与重复请求人工场景推演、敏感字段扫描和 diff integrity；真实重启恢复、并发争抢、租约过期和沙箱幂等结果必须在验证报告中标为待人工运行，不能用静态检查冒充实测。
