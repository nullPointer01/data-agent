# Agent 工具治理

## 为什么模型不能直接拥有工具权限

Tool Calling 中，模型只负责返回“建议调用哪个工具、参数是什么”。工具名和参数都属于不可信输入。把某个 `ToolSpecification` 隐藏起来只能降低模型主动选择它的概率，不能阻止模型或兼容网关返回一个未展示的工具名。因此真实执行必须由服务端再次决定。

当前准入集合是四个条件的交集：

```text
Registry 中已注册且环境启用
  ∩ Run 创建时固化的用户/租户/RBAC 权限
  ∩ 当前 Agent 或 ReAct 规划预检步骤的精确白名单
  ∩ 环境允许的最大风险等级
```

任意条件不满足都在工具实现和预算之前拒绝。`AgentProfile.capability_bindings` 必须是 JSON 数组，`[]` 表示零能力；缺失、损坏、未知或重复身份都会失败关闭。默认个人 Agent 只在首次创建时固化当时允许的 Tool，后续新增 Tool 不会自动扩权。

## Profile 能力配置安全边界

`AgentProfile` 只保存 `capability_bindings` 原始字段，不负责 JSON 解析。保存、响应、能力目录、风险计算和运行时统一经过 `AgentCapabilityConfigurationCodec`：

```text
capability_bindings
  -> []：零能力
  -> 合法数组：精确 Tool / Skill / sub Agent 白名单
  -> NULL 或非法值：失败关闭
```

Codec 解析异常使用稳定领域错误码，API 只返回字段、Agent 编号和原因码，不回显原始 JSON。旧库先用 `sql/audit-agent-capability-bindings.sql` 识别历史状态，再用默认不更新数据的 `sql/migrate-agent-capability-bindings.sql` 审查候选，最后执行 `sql/finalize-agent-capability-bindings.sql` 设置非空约束并删除旧列。历史 `tools=NULL` 必须人工确认具体 Tool 快照，迁移不接入启动流程。

## Descriptor 与 Registry

每个 `@Tool` 方法必须同时声明 `@AgentToolPolicy`，包括风险、只读、幂等、可重试、timeout、最大尝试数、权限码和结果长度。`AgentToolRegistry` 同时保存同一个工具的 LangChain4j Schema、底层执行器和治理 Descriptor。

启动时会拒绝以下配置：

- 工具重名或缺少治理注解
- timeout、尝试数或结果长度不是正数
- 可重试工具没有同时声明只读和幂等
- 环境启用列表包含不存在的工具

新增工具不能只写描述文本，也不能在配置文件里复制一份容易漂移的安全属性。

## 固定执行管道

```text
解析 Registry 条目
  -> 以同一个 ToolSpecification Schema 校验 JSON 参数
  -> 校验 Run 权限、调用者白名单和风险策略
  -> 每次真实尝试前检查 Run 并消费一次工具预算
  -> 专用有界线程池执行，应用工具 timeout
  -> 分类异常并按安全条件决定是否退避重试
  -> 统一脱敏、截断
  -> 结构化结果 + Journal + Event + Log + Metrics
```

未知工具、畸形或超长 JSON、缺少必填字段、未知字段、类型错误以及未授权调用的实现尝试数都是 0。参数日志只包含字段名、长度和短哈希，不包含字段值。

审批工具在预算前增加显式分支：

```text
admit: Registry -> Schema -> owner RBAC -> Agent allowlist -> risk
  普通工具 -> consume budget -> execute
  审批工具无 grant -> persist approval/checkpoint -> suspend
  审批工具有 grant -> 重新准入 -> consume budget -> execute
```

`approvalRequired` 和 `approvalPermission` 是 Descriptor 的显式合同，不由 `HIGH` 风险自动推断。审批只同意一个参数不可修改的动作，不能补齐发起人缺失的执行权限；恢复时发起人、审批人、工具和策略任一撤权都会安全失败。并行工具响应先整体预检，包含审批动作时不会提前执行同组普通工具。

## 沙箱审批工具

`updateHotelPrice` 声明为非只读、幂等、不可自动重试、需要审批。它受 `app.agent.durable.enabled`、`sandbox-tool-enabled` 和 `enabled-approval-tools` 三重配置收窄，且只有显式绑定后才可能进入 Agent 能力快照。

工具只写隔离的 `hotel_rate_sandbox` 追加流水，不连接真实酒店表。稳定 `approvalId + toolCallId` 形成唯一动作键，重复恢复读取第一次结果且不重复改变沙箱价格。这是本地幂等证据，不是跨系统 exactly-once 承诺。

## 逻辑调用、尝试与重试

模型的一次工具请求是一项逻辑调用，只有一个稳定 `toolCallId`。内部每执行一次真实工具实现才算一次 attempt，并各自消费 Run 的 tool-call budget。

自动重试必须同时满足：

```text
readOnly && idempotent && retryable && transientFailure && Run active
```

明确的连接失败、timeout、HTTP 429 和 5xx 可以归类为瞬时故障。参数、权限、策略、HTTP 4xx、业务空结果和未知异常默认不重试。工具 Descriptor 上限与服务端上限取较小值，退避也有上限。

`toolCallId` 只提供事件和 Trace 相关性，不提供跨进程去重。未来接入支付、发布、写库等工具时，需要持久化幂等键、下游唯一约束、审批与补偿，而不是放宽本管道的重试条件。

## timeout 与取消边界

单次等待时间取工具 timeout、服务端 timeout 和 Run 剩余 deadline 的最小值。工具在专用有界线程池运行，Security、MDC 和 AgentRunScope 会传播并在复用线程前恢复。超时会执行 `Future.cancel(true)`，调用链及时得到 `TIMEOUT`，晚返回不会再进入模型或 Trace。

线程中断是协作式机制。若底层 JDBC 或 HTTP 客户端忽略中断，任务可能继续占用线程，因此连接器仍需配置自身 timeout。当前治理不能被描述为强制杀进程或 exactly-once。

## 安全结果与可观测性

原始结果只在执行管道当前栈中短暂存在。进入模型、SSE、结构化日志和 Trace 前统一屏蔽 Bearer Token、API Key、password/secret/token 字段和连接串凭据，再按 Descriptor 与服务端上限的较小值截断。

每项逻辑调用只发布一个兼容前端的 `tool_call` 事件。Micrometer 仅使用已注册工具名、状态、风险和尝试结果等低基数标签，不使用 runId、userId 或异常文本。Run Tool Journal 有固定容量并汇总到现有 Trace JSON。

## 配置

配置前缀是 `app.agent.tool-governance`。模板默认值是保护性起点：参数 16384 字符、结果 20000 字符、全局 timeout 20 秒、最多 2 次尝试、100ms 至 1s 退避、Journal 64 条、2 至 8 个执行线程、队列 100。应根据真实 P95/P99、拒绝率、timeout 率和线程池饱和度调优，不能把模板值宣称为最优值。

`enabled-tools` 为空表示全部 Registry 工具启用；`max-risk` 可按环境限制最高风险。第一版现有工具继续复用 `app:use` 与管理员 `*:*`，以后增加细粒度权限码不需要修改执行管道。

## 当前边界

持久化审批只覆盖 ReAct 单动作暂停/恢复，没有多级审批、转交、撤回和任意图节点回放。新增真实写工具时，不能因为已有沙箱就直接开放：仍需单独评估下游幂等合同、补偿、业务权限、数据保留和密钥轮换。
