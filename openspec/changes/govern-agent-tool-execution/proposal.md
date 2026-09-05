## Why

统一 Agent Run 已经能够限制工具调用次数并终止失控运行，但当前 `AgentToolInvoker` 只按全局工具名查找并直接执行：AgentProfile 的工具列表主要用于告诉模型“有哪些工具”，没有在真实执行边界再次证明该 Run、Agent、租户和用户有权调用该工具。工具异常也主要退化成字符串，缺少风险、超时、重试、幂等和安全输出的统一语义。

本变更建立 Tool Governance 执行层，让模型只能提出工具调用建议，由服务端完成最终准入和可靠执行，为后续写操作、人工审批与 Checkpoint/Resume 提供可信基础。

## What Changes

- 建立不可变工具目录，为每个工具声明名称、风险等级、只读性、超时、可重试性、幂等性、所需权限和最大结果长度。
- 在 Agent Run 创建时固化服务端允许的工具集合；实际执行前重新校验 Run、Agent/Profile 白名单、用户权限和工具策略，默认拒绝未授权调用。
- 将工具执行收敛为固定管道：解析工具 -> 参数结构校验 -> 策略准入 -> 预算准入 -> 超时/有限重试 -> 执行 -> 脱敏/截断 -> 结构化结果。
- 为每次逻辑工具调用生成稳定 `toolCallId`，区分逻辑调用和底层尝试次数；只允许满足策略的只读、幂等工具进行有限重试。
- 用结构化错误码表达未知工具、未授权、参数非法、超时、执行失败和结果受限，使 ReAct 能可靠决定继续、纠正或停止。
- 将工具准入、拒绝、尝试、耗时、结果状态、重试和安全处理信息接入现有 Agent Event、Trace、日志和 Micrometer 指标。
- 保持现有 `@Tool` 方法、AgentProfile `tools` 数据、分析 API、ReAct/Orchestrator、RAG、Memory 和模型协议兼容。

## Capabilities

### New Capabilities

- `agent-tool-governance`: 定义工具目录、执行期授权、参数校验、风险策略、超时与有限重试、幂等标识、结构化结果、安全输出和可观测性。

### Modified Capabilities

无。当前 `openspec/specs` 尚无已发布的工具治理 capability；本变更依赖已完成的统一 Agent Run 实现，但不修改其三种执行模式和状态机合同。

## Impact

- 后端：`com.ai.agent.tool` 工具注册与执行、`AgentRunContext` 的执行期工具授权快照、ReAct 工具结果处理、现有 Trace/Event 接入。
- 安全：复用 Spring Security、租户上下文和现有 RBAC；新增工具权限判断，但第一阶段不新增权限管理页面。
- 配置：新增 `app.agent.tool-governance.*` 的默认超时、最大尝试数、结果长度与脱敏规则；客户端不能扩大上限。
- 数据：不修改 `agent_profile` 表，不新增审批、Checkpoint 或工具调用表；幂等只提供运行内逻辑调用标识和执行策略约束，不承诺跨进程持久化去重。
- 依赖：继续使用 Spring Boot 3.2、LangChain4j 1.19 和 JDK 17，不引入新的 Agent 框架。

## Objective And Scope

目标是让所有 Agent 工具调用都经过同一个服务端执行控制面，保证“模型知道某工具”与“服务端允许执行某工具”严格分离，并让失败、重试和审计拥有稳定、可解释的工程语义。

### Anti-Scope

- 不在本阶段实现人工审批页面、持久化审批单、Checkpoint/Resume 或等待审批的新 Run 状态。
- 不实现跨节点工具任务调度、分布式锁或持久化幂等结果缓存。
- 不新增发送消息、修改数据库、支付或发布等真实高风险写操作工具。
- 不改变 Agent 路由、三种执行模式、模型接入、RAG、Memory 或现有业务工具内部逻辑。
- 不把“最多尝试次数”误报成一定能消除外部系统的重复副作用。

### Quantified Success Criteria

- SC-1：项目中 100% 的 Agent 工具实现调用通过统一 Tool Governance 管道；不存在从 ReAct、Orchestrator 或配置 Agent 绕过该管道的直接执行路径。
- SC-2：一次 Run 的实际可执行工具集合等于服务端目录、Agent/Profile 白名单、用户权限与租户边界的交集；任何不在交集内的调用执行次数为 0。
- SC-3：100% 的已注册工具具有风险等级、只读性、幂等性、超时、最大尝试数、所需权限和最大结果长度；缺失或非法元数据时工具不得注册。
- SC-4：每次逻辑工具调用只有 1 个稳定 `toolCallId`，底层尝试次数不超过服务端配置且继续受 Run 工具预算和 deadline 约束。
- SC-5：只有 `readOnly=true && idempotent=true && retryable=true` 且错误分类可重试的工具允许重试；权限、参数、未知工具和策略拒绝的尝试次数恒为 0。
- SC-6：工具结果统一返回结构化状态与安全错误码；对外结果不超过配置上限，并对 API Key、Bearer Token、密码和连接串凭据执行脱敏。
- SC-7：同步与流式执行都能按 `runId + toolCallId` 关联工具名、准入结果、尝试次数、耗时、终态和安全处理标记；不得记录完整敏感参数或未截断结果。
- SC-8：现有 AgentProfile `tools`、分析 API 和已有工具无需数据库迁移即可继续使用；工具名称及模型可见 JSON Schema 保持兼容。

## Assumptions And Boundaries

详细假设记录在 `sdd-assumptions.md`。

- Always：模型输出的工具名和参数一律视为不可信输入；真实执行前必须服务端校验。
- Always：执行期授权使用 Run 创建时固化的服务端快照，不能接受模型或客户端临时扩大工具权限。
- Always：预算准入发生在每个真实工具实现尝试前；策略拒绝和参数拒绝不得消耗实现调用次数。
- Ask first：新增数据库表、修改 AgentProfile 表结构、接入持久化审批或分布式幂等存储。
- Ask first：为现有工具改变业务结果或新增有副作用的写操作。
- Never：按工具描述文本推断权限、对非幂等调用自动重试、在事件/Trace/日志中保存凭据或完整敏感参数。

## Verification Strategy

继续遵循用户明确约束：本 change 不创建或运行自动化测试，不执行 Maven 编译或前端构建。实现验收使用 OpenSpec strict validation、全调用点静态扫描、授权矩阵人工推演、失败分类与重试边界推演、敏感信息扫描和 diff integrity 检查，并在验证报告中明确未覆盖真实超时、并发和外部副作用场景。
