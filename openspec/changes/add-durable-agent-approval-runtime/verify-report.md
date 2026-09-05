# Verification Report

## Decision

`PASS_WITH_NOTES`

本 change 已完成约定的无编译、无自动化测试静态验收。规格结构、代码调用边界、SQL 条件写入、前端语法和依赖 API 均有静态证据；真实进程重启、数据库竞争、租约接管、模型恢复和浏览器渲染仍未运行验证，因此不能宣称生产闭环已经实测。

## Scope And Constraints

- Change: `add-durable-agent-approval-runtime`
- Java: JDK 17 source baseline
- Framework: Spring Boot 3.2, LangChain4j 1.19, MySQL, React/Vite
- Test override: 未创建或运行测试，未执行 Maven compile/package/validate/verify，未执行前端或 Docker build
- Anti-scope: 未引入 LangGraph、BPMN、Redis 协调、MQ、真实酒店写系统、多级审批或事件 replay

## Success Criteria

### SC-1 Approval Stops Before Tool Implementation

静态通过。`AgentToolAdmissionService` 在预算和执行前返回 `APPROVAL_REQUIRED`；`ReActStepHandler` 先预检整组请求并调用 `AgentApprovalSuspensionService`，批准前不会进入 `DefaultToolExecutor`。Run 与审批在同一暂停事务中写为 `WAITING_APPROVAL/PENDING`。

### SC-2 Durable Run And Same Logical Identity

静态通过，运行未验证。`agent_run_state` 保存稳定 `runId`，Checkpoint 保存同一 `toolCallId/providerRequestId`；查询接口按 tenant + owner 读取。恢复仍使用原 runId、会话、模型消息和冻结预算。真实服务重启后的恢复尚未执行。

### SC-3 Versioned Encrypted Checkpoint

静态通过。Checkpoint DTO 包含执行位置、消息、待处理动作、allowlist、预算、版本和捕获时间；Codec 拒绝未知版本。`checkpoint_ciphertext` 与 `request_ciphertext` 只接受 `ENC:` 载荷，API DTO、Trace、Audit、日志和前端均未暴露密文字段或原始参数。

### SC-4 Immutable Decision

静态通过，竞争未验证。批准、拒绝和过期均以 `PENDING + expiresAt + version` 条件更新；影响行数只有 1 才表示决定成功。重复 API 请求返回数据库最终状态。双请求并发结果未在真实 MySQL 中执行。

### SC-5 Reauthorization Before Resume

静态通过。恢复重新读取 owner/reviewer 用户启用状态、tenant、双方 RBAC、Tool Registry 启用状态、原 Agent allowlist、required permission、approval permission 和当前风险策略；失败在 approved tool attempt 前收敛为安全终态。

### SC-6 Lease And Idempotent Sandbox

静态通过，竞争未验证。领取和续租使用状态、版本、lease owner、lease expiry 的条件更新；恢复扫描只向独立有界线程池提交任务，避免长模型调用阻塞同一调度器上的续租；续租 CAS 失败会取消节点内 Run，阻止后续模型/工具准入。达到最大恢复次数时 `WAITING_APPROVAL/RESUMING` 都能终止。`hotel_rate_sandbox` 以 tenant + `approvalId:toolCallId` 唯一，`INSERT IGNORE` 后总是读取第一次结果。双 worker、进程崩溃和过期租约接管未实测。

### SC-7 Correlated Observability

静态通过。审批 request/approve/reject/expire/resume/sandbox audit 共享 `runId/toolCallId/approvalId`；恢复成功、再次暂停、Checkpoint 解码失败、重授权失败、失租和重试耗尽都能追加到原 runId Trace，即使尚未重建运行时 Context；工具 Journal 继续关联 runId/toolCallId。Micrometer 仅使用 event、outcome、tool、status、risk 等低基数标签。

### SC-8 Compatibility And Default-Off

静态通过，回归未运行。durable 和 sandbox flag 默认 false；审批工具还受审批工具集合过滤，普通 Profile 空工具列表不会默认暴露 `updateHotelPrice`。既有 API 与普通工具路径保持不变，数据库只增加新表和 RBAC 数据。

## Static Checks

- `openspec validate add-durable-agent-approval-runtime --strict --json`: passed, 0 issues
- `git diff --check`: passed
- LangChain4j 1.19 Maven Central JAR `javap`: confirmed `AiMessage.Builder.toolExecutionRequests`, `ToolExecutionResultMessage.toolName/from` and `ToolExecutionRequest.Builder.id/name/arguments`
- TypeScript parser over modified JS/JSX: all files syntax OK
- Run state writes scan: initial create uses `saveAndFlush`; later state changes use Repository condition updates through Store/approval services
- Tool bypass scan: production reflection execution remains inside `AgentToolExecutionPipeline`; approved resume uses `invokeApproved`
- Sensitive field scan: ciphertext and `argumentsJson` have no API/frontend/log DTO path
- SQL/entity review: three new tables, indexes, enum lengths, precision, IDs and unique constraints align statically
- API/client review: list/detail/approve/reject paths and response field names align
- Scheduler/lease review: resume work uses a dedicated bounded executor, leaving the scheduler free to renew leases; renewal CAS failure cancels the node-local Run
- Context-free Trace review: decode/reauthorize/exhaustion failures append by persisted runId + tenantId + approvalId + toolCallId

## Manual Walkthroughs

1. Approval request: pre-admit -> no attempt -> encrypted checkpoint + approval -> WAITING -> approval event -> transport done.
2. Approve: four-eyes and tenant checks -> immutable APPROVED/READY -> worker claim -> RUNNING -> reauthorize -> original tool pipeline -> ReAct continuation.
3. Reject/expire/cancel: approval becomes non-executable and Run enters a matching terminal state.
4. Worker crash: Run remains RESUMING/RUNNING until lease expiry; another node can reclaim with the same IDs; sandbox unique action returns the first result.
5. Multi-tool response: all requests are preflighted; when approval is present, no peer tool executes and Checkpoint retains one protocol-paired tool call.

## Residual Risks

- Spring Data JPQL/native query behavior, transaction isolation and affected-row counts require a real MySQL run.
- Scheduler timing, lease heartbeat races, GC pause and two-node recovery require integration testing.
- Tool success followed by process death before local completion is not generally exactly-once; only the sandbox has a local unique action key.
- Model-provider acceptance of restored message history and continuation requires calls against each supported provider.
- Approval UI layout and interaction were syntax-checked but not built or screenshot-tested by explicit constraint.
- Current single `APP_ENCRYPTION_KEY` has no key version; rotation can invalidate pending Checkpoints.
- Current UI authorizes navigation by ADMIN role because login state does not expose fine-grained permission codes; backend remains the authority for every approval API.

## Follow-Up Verification When Execution Is Allowed

Run one local MySQL/Milvus/ES application instance, then two application nodes sharing MySQL. Exercise approve/reject/expire/cancel, restart between WAITING and approval, kill a worker after sandbox insert, wait for lease expiry, replay the same action, revoke owner/reviewer permissions before resume, and inspect Trace/Audit/Prometheus outputs. Build the frontend and capture 375px, 768px and desktop approval-center screenshots.
