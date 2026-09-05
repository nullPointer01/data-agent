## Context

统一 Agent Run 已经提供 `runId`、五类预算、deadline、协作式取消、类型化事件和统一 Trace，但工具边界仍是一个薄反射调用器：`AgentToolInvoker` 从 `AgentTools` 的 `@Tool` 方法构造全局执行器映射，收到模型返回的工具名后直接查表执行。配置 Agent 会先按 `AgentProfile.tools` 过滤发送给模型的 ToolSpecification，但执行器没有再次接收该白名单，因此“模型未被告知某工具”尚未升级为服务端不可绕过的授权规则。

当前工具还存在三类工程缺口：异常主要变成中文字符串，ReAct 依赖文本包含关系判断恢复；没有工具级 timeout 和明确的重试分类；日志、事件与 Trace 没有共同的 `toolCallId` 和安全载荷合同。已有工具大多为只读查询或本地计算，动态 Skill 可能访问外部 API，后续再增加写操作前必须先建立治理控制面。

约束：JDK 17、Spring Boot 3.2、LangChain4j 1.19；保留现有分析 API、AgentProfile 表和 `@Tool` Schema；不新增数据库表；不实现持久化审批、Checkpoint/Resume 或跨节点幂等；按用户要求不创建/运行测试且不执行 Maven 编译。

## Goals / Non-Goals

**Goals:**

- 让所有 Agent 工具实现调用经过唯一、默认拒绝的服务端执行管道。
- 把工具元数据从描述文本提升为可校验的类型化合同。
- 在执行边界同时验证 Run 主体权限和当前 Agent 实际允许的工具集合。
- 统一参数校验、预算、工具级 timeout、受限重试、结果安全处理和结构化错误。
- 让同步、SSE 和 Trace 使用相同 `runId + toolCallId` 关联一次逻辑调用。
- 保持已有工具名称、模型可见 Schema、Profile 数据和业务实现兼容。

**Non-Goals:**

- 不新增真实写操作工具，也不宣称已有工具已经适合执行支付、发布或数据修改。
- 不实现等待人工审批的持久状态、审批页面或恢复令牌。
- 不提供跨进程幂等结果缓存、分布式锁或 exactly-once 保证。
- 不重写具体工具 Service 的查询、RAG、Memory、SQL 和数据源业务逻辑。
- 不把模型的“重试思考轮”与执行管道内部的“同一次工具调用重试”混为一类。

## Decisions

### 1. 用类型化 Tool Descriptor 建立唯一工具目录

新增 `AgentToolDescriptor`、`AgentToolRiskLevel` 和 `@AgentToolPolicy`。每个 `AgentTools` 的 `@Tool` 方法必须同时声明：

- 风险等级：`LOW`、`MEDIUM`、`HIGH`
- `readOnly`、`idempotent`、`retryable`
- 工具 timeout 和最大尝试数
- 所需权限编码
- 最大安全结果长度

`AgentToolRegistry` 在启动构造时同时生成 LangChain4j `ToolSpecification`、`ToolExecutor` 和 Descriptor。存在重名、缺少治理注解、非法 timeout/尝试数/长度，或 `retryable=true` 但不是只读且幂等时，注册直接失败。前端现有工具列表继续读取名称和描述，可增量展示治理元数据但不改变已有字段。

替代方案是在配置文件中按工具名维护元数据。它便于环境覆盖，但工具重命名时容易漂移，并且无法在代码审查时保证新增 `@Tool` 同时声明策略。代码元数据负责不可变安全属性，配置只负责统一上限。

### 2. Run 权限快照与调用白名单分开表达

新增不可变 `AgentToolAuthorizationSnapshot`，由 `AgentToolAuthorizationService` 在 Coordinator 创建 Run 时根据认证用户、租户和现有 RBAC 角色权限生成。Snapshot 只保存授权判断所需的权限编码、管理员/通配权限和服务端启用工具集合，不保存密码、JWT 或完整用户实体。

当前 Spring Security JWT 主要携带角色，因此授权服务通过已有 `SysUserRepository` 读取当前用户的启用角色和权限，在 Run 创建时只解析一次，并校验用户仍属于当前租户。现有 `app:use` 和 `*:*` 语义继续兼容；未来可增加工具专属权限编码而不修改执行管道。

另新增显式 `AgentToolInvocationContext`，携带当前执行者标识和本次实际允许的工具名集合。配置 Agent 的集合来自 Profile 过滤后的 ToolSpecification；默认 ReAct 使用服务端内置集合；Orchestrator 的确定性预检只能携带那三个预检工具。`AgentToolInvoker` 不再接受缺少调用上下文的 Agent 请求。

执行准入取以下交集：

```text
Registered and server-enabled
        ∩ Run principal permission
        ∩ current Agent/invocation allowlist
        ∩ tool risk policy
```

替代方案是只把允许工具集合塞进 `AgentRunContext`。Orchestrator 可在同一 Run 内并行执行不同 Profile/专家，单一集合要么过宽，要么让并发子任务互相覆盖；显式调用上下文能保持各执行者边界。

### 3. 固定执行顺序，拒绝发生在实现调用之前

`AgentToolExecutionPipeline` 使用固定顺序：

```text
resolve descriptor
  -> parse and validate arguments
  -> authorize principal/tenant/agent/risk
  -> create logical toolCallId
  -> for each permitted attempt:
       ensure Run active
       consume one tool-call budget
       execute with tool timeout
       classify result/error
  -> sanitize and truncate model/event/trace output
  -> journal + metrics + typed event
```

未知工具、非法 JSON、缺失/多余字段、类型错误和授权拒绝都不会进入 `ToolExecutor`，也不消耗实现尝试或 Run 工具调用预算。每个真实尝试前重新检查 Run deadline/取消并消耗一次 tool-call 名额，因此重试不能绕过第一阶段预算。

替代方案是先调用 `DefaultToolExecutor` 再根据异常分类。这样参数错误已经越过真实执行边界，且无法证明拒绝场景的实现调用次数为零。

### 4. 参数校验复用 ToolSpecification Schema

`AgentToolArgumentValidator` 使用 Jackson 解析 `ToolExecutionRequest.arguments()`，并以 Registry 中同一个 LangChain4j ToolSpecification 的参数 Schema 校验顶层对象、必填字段、未知字段和当前已有标量类型。另设置参数 JSON 总长度上限，避免异常大参数进入反射和日志。

第一阶段工具参数均为简单字符串或无参数，不自建通用 JSON Schema 引擎。未来出现数组、嵌套对象或多模态参数时，再扩展验证器并增加对应 capability，而不是用字符串切割实现。

### 5. 结构化结果是内部事实，字符串只是模型观察格式

新增 `AgentToolExecutionResult` 和稳定错误枚举，至少覆盖：

- `SUCCESS`
- `UNKNOWN_TOOL`
- `INVALID_ARGUMENTS`
- `UNAUTHORIZED`
- `POLICY_DENIED`
- `TIMEOUT`
- `EXECUTION_FAILED`
- `RUN_TERMINATED`

结果包含 `toolCallId`、工具名、状态、是否可重试、尝试次数、耗时、安全 payload、截断/脱敏标记。ReAct 使用结果生成受控的模型观察文本；`ErrorRecoveryAdvisor` 改为优先读取错误码，不再依赖“工具执行失败”等中文文本判断。现有用户可见提示可以保留，但不作为控制流事实。

替代方案是继续扩展字符串前缀。它改动小，但无法稳定支持多语言、指标聚合或精确重试，并容易被工具正常内容误触发。

### 6. 工具 timeout 使用专用有界执行器，取消仍是协作式

工具实现通过配置了 `ContextPropagatingTaskDecorator` 的专用有界 `agentToolExecutor` 执行，确保 Security、MDC 和 AgentRunScope 传播且线程复用后清理。调用线程用 Descriptor timeout 与 Run 剩余 deadline 的较小值等待；超时后取消 Future 并返回结构化 `TIMEOUT`。

这不承诺强制杀死忽略线程中断的 JDBC/HTTP 调用。现有工具以只读为主，超时后的晚返回被丢弃；新增副作用工具前必须提供其客户端级 timeout 和持久化幂等方案。

替代方案是在当前 SSE/Orchestrator 线程直接执行并只在结束后检查耗时。那只能统计超时，不能及时释放主执行链路。

### 7. 重试是 Descriptor、错误分类和 Run 状态的共同决定

一次逻辑调用最多执行 `min(descriptor.maxAttempts, serverMaxAttempts)` 次，且必须同时满足：

```text
readOnly && idempotent && retryable && failure.retriable && Run active
```

只有明确瞬时异常（连接失败、连接/读取 timeout、429、5xx 等可识别类型）可重试，使用有上限的指数退避；未知工具、参数、权限、策略、4xx 和业务空结果不重试。每次尝试消耗一个 Run tool-call budget，但始终共用一个 `toolCallId`。

`ToolExecutionRequest.id()` 非空时优先作为稳定逻辑标识的一部分，否则生成 UUID。该 ID 用于相关性和运行内去重保护，不构成跨 Run exactly-once。

### 8. 安全输出只保留一次处理后的版本

`AgentToolOutputSanitizer` 在内容交给模型、事件、日志和 Trace 之前执行同一套规则：

- 屏蔽 Bearer Token、常见 API Key、password/secret/token 字段和连接串凭据
- 按 Descriptor 与服务端上限的较小值截断
- 日志参数只记录工具名、字段名、长度和哈希摘要，不记录完整值
- Trace Journal 只保存安全摘要，不保存原始参数/结果

原始结果只在执行管道当前栈内短暂存在，不写入 Agent Event、StructuredLogger 或 `agent_execution_trace`。

### 9. Run 内 Journal 汇总工具治理证据

新增线程安全且有条数上限的 `AgentToolExecutionJournal`，挂在 AgentRunContext 中，只记录安全元数据。执行管道对每个允许或拒绝的逻辑调用追加记录，发送一个兼容现有 `tool_call` 的类型化事件，并更新以下低基数指标：

- `agent_tool_calls_total{tool,status,risk}`
- `agent_tool_attempts_total{tool,outcome}`
- `agent_tool_duration_seconds{tool,status}`

`AgentExecutionTraceService.recordRun` 把 Journal 摘要写入现有 JSON 元数据，不新增表。指标不得以 runId、userId、异常消息作为 tag，避免时序基数失控。

## Risks / Trade-offs

- [现有 Profile 空工具列表表示“全部工具”] -> 保留该兼容语义，但由服务端目录和主体权限继续收窄；文档明确空列表不是绕过治理。
- [用户角色权限在 Run 期间变化] -> Run 使用创建时快照保证单次执行一致性；新 Run 立即读取新权限，紧急撤权无法终止已在途工具，后续可结合跨节点 Run Registry。
- [专用执行器 timeout 后底层任务仍运行] -> Future 中断、晚结果丢弃、队列有界；现有只读工具不自动扩大副作用，客户端级 timeout 留给具体连接器。
- [动态 Skill 的行为难以静态判定] -> 将 `useSkill` 标记为不可自动重试，并要求显式允许；本阶段不把任意 Skill 声明为安全幂等。
- [RBAC 查询增加每 Run 一次数据库访问] -> 只在创建 Run 时解析并固化，不在每次工具调用查询；后续再评估权限缓存和撤权时效。
- [结果脱敏误报或漏报] -> 使用字段名和内容模式组合、记录脱敏标记；高敏工具还需业务 Service 返回最小数据集，通用脱敏不是数据防泄漏的全部方案。
- [旧 ReAct 恢复依赖中文字符串] -> 迁移期间结构化错误生成兼容观察文本；控制流逐步切换到错误码后再删除字符串判断。

## Migration Plan

1. 增加治理配置、风险枚举、注解、Descriptor 和 Registry；为所有现有 `@Tool` 补齐元数据，保持 ToolSpecification 不变。
2. 增加 RBAC 权限快照和显式 InvocationContext，把配置 Agent、默认 ReAct、专家和并行预检的允许工具集合接入调用链。
3. 增加参数验证、结构化结果、安全输出与错误分类，先以单次执行替换旧 `invoke`。
4. 增加专用有界执行器、工具 timeout 和受限重试，并让每次真实尝试消费 Run tool-call budget。
5. 将 ReAct 恢复逻辑切到错误码，统一 `tool_call` 事件、Journal、Trace、日志和指标，移除下层重复事件。
6. 更新 Harness/工具治理文档并执行静态验收。每一步保持现有分析端点和数据库可用；若治理管道出现问题，可回退到上一代码版本，不需要回滚数据库。

## Open Questions

- 每个现有工具的 timeout 和结果长度默认值需要根据真实 Trace 分布调优，提案值只能作为保护性起点。
- 当前所有已有工具可继续复用 `app:use` 权限，何时引入 `agent:tool:sql`、`agent:tool:file` 等细粒度权限应结合真实组织角色决定。
- HIGH 风险工具的人工审批、持久化等待与恢复需要引入新的 Run 状态和存储合同，留到第三阶段。
- 跨 Run 幂等键、结果缓存和副作用补偿需要真实写操作场景，不能仅靠 `toolCallId` 推导。

