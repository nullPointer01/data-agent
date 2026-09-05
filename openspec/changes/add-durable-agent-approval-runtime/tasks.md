## 1. Durable Contracts And Persistence

- [x] 1.1 定义持久化 Run 配置、等待/恢复状态和稳定终止原因。（实现任务）
  - Acceptance: durable feature flag、审批 TTL、租约、扫描周期和保留期均为正数服务端配置；Run 状态图包含 WAITING_APPROVAL/RESUMING/REJECTED/EXPIRED 且终态判断一致。
  - Verify: 静态核对配置绑定、默认值、状态转换声明和客户端不可覆盖边界。
  - Files: `src/main/java/com/ai/agent/durable/AgentDurableRuntimeProperties.java`, `src/main/java/com/ai/agent/runtime/AgentRunStatus.java`, `src/main/java/com/ai/agent/runtime/AgentRunTerminationReason.java`, `src/main/resources/application.yml`
  - Covers: SC-1, SC-2, SC-8

- [x] 1.2 增加持久化 Run、审批和沙箱动作数据库结构与基础 Repository。（实现任务）
  - Acceptance: `agent_run_state`、`agent_tool_approval` 和 `hotel_rate_sandbox` 具有租户索引、版本、状态、租约、过期时间及 `runId/toolCallId/approvalId` 唯一约束；实体字段与 SQL 一致。
  - Verify: 对照实体注解、Repository 标识类型和 `sql/init.sql` 逐字段静态核验。
  - Files: `sql/init.sql`, `src/main/java/com/ai/agent/durable/AgentRunStateEntity.java`, `src/main/java/com/ai/agent/approval/AgentToolApprovalEntity.java`, `src/main/java/com/ai/agent/durable/AgentRunStateRepository.java`, `src/main/java/com/ai/agent/approval/AgentToolApprovalRepository.java`
  - Covers: SC-2, SC-4, SC-6, SC-8

- [x] 1.3 定义版本化可移植 Checkpoint 和加密 Codec。（实现任务）
  - Acceptance: Checkpoint 覆盖执行位置、可移植消息、待处理动作、执行者白名单、预算计数和剩余 active timeout；Codec 使用现有 CryptoUtil 整体加解密并拒绝未知版本。
  - Verify: 静态扫描确保不序列化 Spring Bean/LangChain4j 客户端，不存在明文持久化出口。
  - Files: `src/main/java/com/ai/agent/durable/AgentRunCheckpoint.java`, `src/main/java/com/ai/agent/durable/AgentCheckpointMessage.java`, `src/main/java/com/ai/agent/durable/AgentCheckpointCodec.java`, `src/main/java/com/ai/agent/durable/AgentCheckpointVersion.java`
  - Covers: SC-2, SC-3

### Checkpoint A

- 数据库结构能够表达一个持久化 Run、一个不可变审批决定和一个幂等沙箱动作。
- Checkpoint 不依赖 JVM 对象身份，敏感载荷只有加密持久化路径。
- durable feature 默认关闭时不改变当前在线执行。

## 2. Persistent Run Lifecycle

- [x] 2.1 建立唯一 Run Store 和条件状态转换边界。（实现任务）
  - Acceptance: 创建、转换、暂停、终止和版本冲突统一经过 Store；业务代码不能无条件 save 覆盖状态；每次转换核对期望状态/version 和受影响行数。
  - Verify: 静态追踪所有 Run 状态写入点，人工推演 completion/cancel/timeout 竞争只有一个赢家。
  - Files: `src/main/java/com/ai/agent/durable/AgentDurableRunStore.java`, `src/main/java/com/ai/agent/durable/AgentRunTransition.java`, `src/main/java/com/ai/agent/durable/AgentRunStateRepository.java`, `src/main/java/com/ai/agent/durable/AgentRunStateEntity.java`
  - Covers: SC-1, SC-2, SC-4

- [x] 2.2 让 RunControl 能冻结和恢复预算而不扩容。（实现任务）
  - Acceptance: 暂停快照保存四类计数和剩余 active timeout；恢复构造器延续已用预算并拒绝负数、超上限或零剩余时间；人工等待不计入 active duration。
  - Verify: 人工计算边界快照，核对恢复后的下一次 iteration/model/tool/token 准入结果。
  - Files: `src/main/java/com/ai/agent/runtime/AgentRunControl.java`, `src/main/java/com/ai/agent/runtime/AgentRunSnapshot.java`, `src/main/java/com/ai/agent/durable/AgentRunBudgetCheckpoint.java`, `src/main/java/com/ai/agent/durable/AgentRunControlFactory.java`
  - Covers: SC-2, SC-3

- [x] 2.3 将 Coordinator 生命周期同步到持久化 Run。（实现任务）
  - Acceptance: durable 请求创建同 runId 记录；运行、暂停和所有终态均持久化；WAITING 不被 Coordinator 自动 complete/fail；内存 Registry 仍只保存当前执行节点。
  - Verify: 静态追踪同步/流式/异常三条路径的持久化转换，确认每条至多一个终态。
  - Files: `src/main/java/com/ai/agent/runtime/AgentRunCoordinator.java`, `src/main/java/com/ai/agent/runtime/AgentRunContext.java`, `src/main/java/com/ai/agent/durable/AgentDurableRunStore.java`, `src/main/java/com/ai/agent/runtime/AgentExecutionStrategy.java`
  - Covers: SC-1, SC-2, SC-8

- [x] 2.4 增加所有者可见的持久化 Run 查询和等待态取消。（实现任务）
  - Acceptance: detail 只返回当前租户所有者的安全状态/预算/审批引用/结果；取消同时支持内存活动 Run 和持久化 WAITING Run，并使关联审批不可执行。
  - Verify: 人工推演 owner、同租户他人、跨租户和已终态取消场景。
  - Files: `src/main/java/com/ai/agent/durable/AgentDurableRunService.java`, `src/main/java/com/ai/agent/durable/AgentDurableRunController.java`, `src/main/java/com/ai/agent/durable/dto/AgentDurableRunResponse.java`, `src/main/java/com/ai/agent/runtime/AgentRunCancellationService.java`
  - Covers: SC-2, SC-7

### Checkpoint B

- 在线运行与持久化运行共用 runId 和单向状态机。
- WAITING 可跨连接查询和取消，预算在暂停期间冻结。
- 尚未接入工具审批时，所有既有执行路径保持原行为。

## 3. Approval-Aware Tool Boundary

- [x] 3.1 扩展 Tool Policy、Descriptor 和状态码的显式审批合同。（实现任务）
  - Acceptance: `approvalRequired` 与 `approvalPermission` 独立于 risk；缺失权限或审批工具声明自动重试时 Registry 启动失败；结果状态包含 APPROVAL_REQUIRED。
  - Verify: 扫描全部 @Tool 元数据，核对普通工具默认行为不变和非法组合 fail-fast。
  - Files: `src/main/java/com/ai/agent/tool/governance/AgentToolPolicy.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolDescriptor.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolRegistry.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolExecutionStatus.java`
  - Covers: SC-1, SC-5, SC-8

- [x] 3.2 将工具治理拆为可复用准入与受控执行两阶段。（实现任务）
  - Acceptance: Registry/Schema/权限/allowlist/risk 只解析一次形成内部 AdmittedToolCall；批准前不进入 attempt/预算；普通工具仍由同一 Pipeline 执行。
  - Verify: 静态调用链确认底层 ToolExecutor 仍只有一个执行点，拒绝与待审批路径 attempt=0。
  - Files: `src/main/java/com/ai/agent/tool/governance/AgentToolAdmissionService.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolAdmission.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolExecutionPipeline.java`, `src/main/java/com/ai/agent/tool/AgentToolInvoker.java`
  - Covers: SC-1, SC-5, SC-6

- [x] 3.3 在 ReAct 工具步骤原子保存 Checkpoint 与审批并暂停循环。（实现任务）
  - Acceptance: APPROVAL_REQUIRED 产生完整 ReAct Checkpoint、唯一审批和暂停 Outcome；同步/流式 Processor 与 Loop 不继续模型调用、不生成失败或最终答案。
  - Verify: 静态追踪多工具请求、首个审批工具、持久化失败和重复 suspension 场景。
  - Files: `src/main/java/com/ai/agent/react/ReActStepHandler.java`, `src/main/java/com/ai/agent/react/ReActStepOutcome.java`, `src/main/java/com/ai/agent/react/ReActSynchronousStepProcessor.java`, `src/main/java/com/ai/agent/react/ReActStreamingStepProcessor.java`, `src/main/java/com/ai/agent/react/ReActLoopRunner.java`
  - Covers: SC-1, SC-2, SC-3

- [x] 3.4 增加传输无关的审批中断事件并兼容现有响应。（实现任务）
  - Acceptance: 每次暂停只产生一个 APPROVAL_REQUIRED 事件；SSE transport done 携带 WAITING 状态但不宣称业务完成；同步响应通过 additive metadata 返回 runId/approvalId。
  - Verify: 核对 EventType 到 SSE JSON 的单一映射以及旧 token/error/done 字段未删除。
  - Files: `src/main/java/com/ai/agent/runtime/event/AgentEventType.java`, `src/main/java/com/ai/agent/runtime/event/AgentSseEventWriter.java`, `src/main/java/com/ai/agent/runtime/event/AgentRunEventBridge.java`, `src/main/java/com/ai/model/AnalysisResponse.java`
  - Covers: SC-1, SC-2, SC-7, SC-8

### Checkpoint C

- 审批动作在实现和预算之前可靠中断。
- ReAct 暂停是正式控制流，不依赖错误字符串或旧 SSE 连接。
- 普通工具和固定只读 Orchestrator 预检不经过审批等待。

## 4. Approval Decisions And Security

- [x] 4.1 增加审批 RBAC 权限并默认只赋给管理员。（实现任务）
  - Acceptance: `agent:approval:review` 在新旧数据库初始化路径幂等创建并只属于 ADMIN；Security 常量与初始化 SQL 一致。
  - Verify: 静态核对 local 既有管理员升级、普通 USER 权限和重复初始化场景。
  - Files: `src/main/java/com/ai/security/SecurityConstants.java`, `src/main/java/com/ai/config/DataInitializer.java`, `src/main/java/com/ai/security/rbac/RolePermissionService.java`, `sql/init.sql`
  - Covers: SC-4, SC-5, SC-8

- [x] 4.2 实现同租户四眼原则和不可变审批决定服务。（实现任务）
  - Acceptance: approve/reject 仅接受 bounded comment；校验 reviewer、tenant、权限、非本人和 TTL；PENDING 条件更新保证并发只有一个决定成功。
  - Verify: 人工推演批准/拒绝竞争、自批、跨租户、撤权、重复请求和过期场景。
  - Files: `src/main/java/com/ai/agent/approval/AgentApprovalService.java`, `src/main/java/com/ai/agent/approval/AgentApprovalDecision.java`, `src/main/java/com/ai/agent/approval/AgentToolApprovalRepository.java`, `src/main/java/com/ai/agent/approval/AgentToolApprovalEntity.java`
  - Covers: SC-4, SC-5, SC-7

- [x] 4.3 提供审批列表、详情、批准和拒绝 API。（实现任务）
  - Acceptance: API 只暴露本租户安全摘要；无 review 权限、跨租户或不存在资源均不泄露审批信息；请求不能携带工具或恢复状态字段。
  - Verify: 静态核对 Controller 参数、DTO 字段、Service 权限入口和分页上限。
  - Files: `src/main/java/com/ai/agent/approval/AgentApprovalController.java`, `src/main/java/com/ai/agent/approval/dto/AgentApprovalResponse.java`, `src/main/java/com/ai/agent/approval/dto/AgentApprovalListResponse.java`, `src/main/java/com/ai/agent/approval/dto/AgentApprovalDecisionRequest.java`
  - Covers: SC-4, SC-5, SC-7

- [x] 4.4 实现审批过期的幂等扫描与 Run 联动。（实现任务）
  - Acceptance: 调度器只领取到期 PENDING 审批，原子更新 approval/Run 为 EXPIRED；重复扫描不重复事件或执行工具；扫描批量有界。
  - Verify: 静态推演 approve-vs-expire 竞争、Run 已取消和扫描重入。
  - Files: `src/main/java/com/ai/agent/approval/AgentApprovalExpirationScheduler.java`, `src/main/java/com/ai/agent/approval/AgentApprovalService.java`, `src/main/java/com/ai/agent/approval/AgentToolApprovalRepository.java`, `src/main/java/com/ai/agent/durable/AgentDurableRuntimeProperties.java`
  - Covers: SC-4, SC-7

### Checkpoint D

- 审批单不能跨租户、不能自批、不能修改动作，且只能决策一次。
- 过期、拒绝和取消均使 pending action 永久不可执行。
- 批准事务只提交决定，不在 HTTP 事务内执行模型或工具。

## 5. Lease, Resume And Reauthorization

- [x] 5.1 实现 MySQL 条件领取、续租和过期租约回收。（实现任务）
  - Acceptance: approved Run 只有一个 lease holder；续租要求 owner/version 匹配；失去租约的 worker 不能写回；过期租约可被其他节点领取。
  - Verify: 静态核对每条 update 的 WHERE 条件与受影响行检查，人工交错推演双 worker。
  - Files: `src/main/java/com/ai/agent/durable/AgentRunLeaseService.java`, `src/main/java/com/ai/agent/durable/AgentResumeWorker.java`, `src/main/java/com/ai/agent/durable/AgentRunStateRepository.java`, `src/main/java/com/ai/agent/durable/AgentDurableRunStore.java`, `src/main/java/com/ai/config/AsyncConfig.java`
  - Covers: SC-2, SC-6

- [x] 5.2 从 Checkpoint 重建 RunContext 和 ReAct 可移植消息。（实现任务）
  - Acceptance: Restorer 只从持久化 server state 和当前 Registry 构造对象；保留 run/session/agent/mode/toolCallId 与预算；解密/版本错误 fail closed。
  - Verify: 静态逐字段核对 capture/restore 对称性和不受客户端字段影响。
  - Files: `src/main/java/com/ai/agent/durable/AgentCheckpointRestorer.java`, `src/main/java/com/ai/agent/durable/AgentCheckpointCodec.java`, `src/main/java/com/ai/agent/runtime/AgentRunContext.java`, `src/main/java/com/ai/agent/react/ReActLoopRunner.java`
  - Covers: SC-2, SC-3

- [x] 5.3 恢复前重新校验 owner、reviewer、Tool、allowlist 与风险策略。（实现任务）
  - Acceptance: 使用当前持久化 RBAC 和 Registry，不沿用旧授权结论；任一撤权/禁用/租户/过期失败均 attempt=0 并安全终止。
  - Verify: 人工推演暂停后删除角色、禁用工具、修改风险上限和禁用用户。
  - Files: `src/main/java/com/ai/agent/approval/AgentApprovalReauthorizationService.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolAuthorizationService.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolAdmissionService.java`, `src/main/java/com/ai/agent/approval/AgentApprovalService.java`
  - Covers: SC-5, SC-6

- [x] 5.4 在持有租约和审批 grant 时执行同一逻辑工具调用并继续 ReAct。（实现任务）
  - Acceptance: approved call 复用 toolCallId，通过原 Pipeline 消耗预算和执行；工具结果回填对应 request 后继续循环；再次遇审批可重新暂停。
  - Verify: 静态追踪 resume -> reauthorize -> pipeline -> result message -> loop，确认没有 raw executor 旁路。
  - Files: `src/main/java/com/ai/agent/durable/AgentResumeWorker.java`, `src/main/java/com/ai/agent/approval/AgentApprovalGrant.java`, `src/main/java/com/ai/agent/tool/AgentToolInvoker.java`, `src/main/java/com/ai/agent/react/ReActStepHandler.java`, `src/main/java/com/ai/agent/react/ReActLoopRunner.java`
  - Covers: SC-2, SC-5, SC-6

- [x] 5.5 收敛恢复失败、失租和崩溃后的安全终态。（实现任务）
  - Acceptance: decode/reauthorize/lease/tool/模型失败各有稳定结果；未知提交结果不无限重试；失租 worker 丢弃晚结果；恢复重试受次数和 active deadline 限制。
  - Verify: 人工推演每个异常分支，扫描不存在无限 while/无界恢复或异常吞噬。
  - Files: `src/main/java/com/ai/agent/durable/AgentResumeWorker.java`, `src/main/java/com/ai/agent/durable/AgentResumeStateService.java`, `src/main/java/com/ai/agent/durable/AgentDurableRunStore.java`, `src/main/java/com/ai/agent/runtime/AgentRunTerminationReason.java`, `src/main/java/com/ai/service/AgentExecutionTraceService.java`
  - Covers: SC-2, SC-6, SC-7

### Checkpoint E

- 服务重启后可由新 worker 从同一 Checkpoint 继续。
- 多节点并发只有一个活动 lease holder，恢复前应用最新权限。
- 数据库租约不被描述为外部系统 exactly-once。

## 6. Idempotent Sandbox Action

- [x] 6.1 实现隔离酒店价格沙箱及唯一动作结果。（实现任务）
  - Acceptance: 沙箱记录明确 tenant/demo 标记；更新使用 approvalId/toolCallId 唯一动作键；重复执行返回第一次结果且价格只变化一次。
  - Verify: 静态核对事务、条件更新、唯一约束冲突读取和跨租户过滤。
  - Files: `src/main/java/com/ai/agent/sandbox/HotelRateSandboxEntity.java`, `src/main/java/com/ai/agent/sandbox/HotelRateSandboxRepository.java`, `src/main/java/com/ai/agent/sandbox/HotelRateSandboxService.java`, `sql/init.sql`
  - Covers: SC-6, SC-7

- [x] 6.2 注册默认关闭的审批型 updateHotelPrice 演示工具。（实现任务）
  - Acceptance: 工具非只读、幂等、不可自动重试且 approvalRequired；只调用沙箱 Service；feature flag 默认 false，空 Profile 工具列表不会意外获得它。
  - Verify: 核对 Tool Descriptor、server-enabled 过滤、参数 Schema、沙箱依赖和默认配置。
  - Files: `src/main/java/com/ai/agent/tool/AgentTools.java`, `src/main/java/com/ai/agent/tool/AgentSandboxToolService.java`, `src/main/java/com/ai/agent/durable/AgentDurableRuntimeProperties.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolRegistry.java`, `src/main/resources/application.yml`
  - Covers: SC-1, SC-6, SC-8

### Checkpoint F

- 演示可完整说明“提议、暂停、审批、恢复、幂等执行”。
- 任何默认生产配置都不会自动暴露沙箱写工具。
- 项目不宣称已经接入真实酒店改价能力。

## 7. Approval Center

- [x] 7.1 接入审批 API 客户端与导航入口。（实现任务）
  - Acceptance: 只有具备审批入口权限的用户看到导航；API client 复用现有鉴权和错误处理；不缓存敏感详情。
  - Verify: 静态核对路由、导航权限、API 路径和现有页面兼容性。
  - Files: `frontend/src/api/client.js`, `frontend/src/config/navigation.jsx`, `frontend/src/pages/AdminPages.jsx`
  - Covers: SC-7, SC-8

- [x] 7.2 实现审批列表、详情和批准/拒绝交互。（实现任务）
  - Acceptance: 页面展示安全摘要、风险、requester、TTL 和状态；批准/拒绝有明确确认和处理中状态；重复决定显示服务端最终状态；移动端不重叠。
  - Verify: 静态检查 loading/empty/error/expired/decided 状态、按钮防重复和文本容器约束；不执行前端构建。
  - Files: `frontend/src/pages/AgentApprovalsPage.jsx`, `frontend/src/components/admin/AgentApprovalDetail.jsx`, `frontend/src/styles.css`
  - Covers: SC-4, SC-7

## 8. Observability, Documentation And Static Acceptance

- [x] 8.1 统一审批、租约、恢复和沙箱动作的 Trace/Audit/指标。（实现任务）
  - Acceptance: runId/toolCallId/approvalId 全链路关联；Audit 区分 request/approve/reject/expire/resume；Trace 包含安全恢复摘要；指标标签低基数且不含 ID/用户/备注。
  - Verify: 扫描所有事件/日志/Trace payload 和 metric tags，确认无密文、原始参数或重复成功事件。
  - Files: `src/main/java/com/ai/service/AgentExecutionTraceService.java`, `src/main/java/com/ai/service/AuditLogService.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolTelemetry.java`, `src/main/java/com/ai/agent/approval/AgentApprovalTelemetry.java`
  - Covers: SC-3, SC-7

- [x] 8.2 更新 Harness、工具治理、部署和演化边界文档。（文档任务）
  - Acceptance: 文档解释暂停/恢复状态机、active timeout、审批 TTL、租约、四眼原则、密钥约束和 exactly-once 边界；明确 demo 非生产工具。
  - Verify: 对照 proposal/design/spec 检查术语、配置默认值和 Anti-Scope 一致性。
  - Files: `CLAUDE.md`, `README.md`, `docs/核心逻辑详解/Agent运行时与Harness.md`, `docs/核心逻辑详解/Agent工具治理.md`, `docs/演化/常见陷阱.md`
  - Covers: SC-1, SC-2, SC-3, SC-5, SC-6, SC-7, SC-8

- [x] 8.3 执行无编译、无自动化测试的静态验收并记录未验证项。（验证任务）
  - Acceptance: OpenSpec strict validation、状态写入点、SQL 条件更新、审批权限、调用旁路、明文敏感字段、事件唯一性、API/Schema 兼容和 diff integrity 全部检查；真实重启/并发/租约/沙箱结果明确未实测。
  - Verify: 生成 `verify-report.md`，逐项记录 SC-1..SC-8 的证据、人工场景和残余风险。
  - Files: `openspec/changes/add-durable-agent-approval-runtime/verify-report.md`, `openspec/changes/add-durable-agent-approval-runtime/tasks.md`, `openspec/changes/add-durable-agent-approval-runtime/sdd-state.md`, `openspec/changes/add-durable-agent-approval-runtime/sdd-assumptions.md`
  - Covers: SC-1, SC-2, SC-3, SC-4, SC-5, SC-6, SC-7, SC-8

## Test Strategy Override

用户和项目知识库明确要求自动化开发 Agent 不创建或运行测试，不执行 Maven 编译、打包、verify、前端构建或 Docker 构建。本 change 因此不创建 `src/test` 任务，也不声明覆盖率已经达到任何数值。静态验收只能证明代码结构与合同关系，真实重启恢复、并发审批、租约接管、事务崩溃窗口和 UI 渲染必须在用户以后允许运行验证时单独执行。
