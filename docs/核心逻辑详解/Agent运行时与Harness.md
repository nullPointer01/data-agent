# Agent 运行时与 Harness

## 四个核心概念

- `AgentProfile`：一份可复用的 Agent 配置，绑定模型、System Prompt、统一 Capability 身份列表和执行模式。
- `Agent`：基于 Profile 接受目标、使用获准能力并产出结果的任务执行主体。
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

Agent 配置不维护独立试运行协议。配置保存并设为默认 Agent 后，通过正式对话验证；结果、证据、审批和 Trace
都来自同一个 Run。这样不会形成一条与用户真实行为不同的旁路，也不需要为预览单独维护记忆和工具语义。

Coordinator 解析完成后，只有包内的 `ConfiguredAgentExecutionService` 可以把配置化路线交给
`ConfigurableAgentExecutor`。执行器要求当前线程已绑定 `AgentRunScope`，并强制接收完整且模式一致的
`RequestExecutionPlan`，不再提供自行读取 Profile 推断 Chat/ReAct 的兼容重载。业务 Controller 或 Service
因此不能绕过 Harness 直接启动一个顶层配置化 Agent。

同步和 SSE 只使用不同的 `AgentEventSink`，不会改变路由结果。路由只会产生配置化 Agent 目标：

```text
显式 agentId -> 当前用户默认个人 Agent
```

普通请求不会让用户选择模型、Agent 或执行模式。系统首次使用时创建一个 `default_agent=true` 的个人
`AgentProfile`，后续始终加载该配置中的模型、System Prompt、统一能力绑定和执行模式。`AgentRunRouteResolver`
只处理显式 Agent 和默认个人 Agent，然后交给 `PersonalAgentRequestPlanner` 生成一次不可变
`RequestExecutionPlan`。计划同时固化意图、Chat/ReAct/Orchestrated 模式、四类上下文三态计划、候选工具、置信度和
可展示原因，同步与 SSE 共用同一个结果。

`AgentProfile` 不再使用 `DATA/KNOWLEDGE/REPORT` 等固定类型决定行为。Agent 是什么由 System Prompt、模型、
Capability 绑定和执行模式共同决定；数据分析、知识检索、报告生成只是能力组合后的使用方式，不是互斥的运行时子类。
旧数据库在能力迁移通过后由 `finalize-agent-capability-bindings.sql` 一并删除不再使用的 `type` 和旧能力列。

模式决定不仅停留在路由层，还作为明确的 `AgentExecutionMode` 传给配置化执行器；否则
`execution_mode=auto` 会被执行器按非 Chat 处理，造成路由显示 Chat、实际执行 ReAct 的不一致。
历史 Profile 的空模式按 `auto` 处理；用户显式固定为 Chat、ReAct 或 Orchestrated 时，规划器保留该模式，但仍能关闭
本次不需要的 RAG、记忆和候选工具。

## 请求规划与资源准入

当前规划器版本为 `request-planner-v7`（2026-09-23）。完整问句“你是谁/你能做什么/你能干什么/你有哪些能力”
可由本地能力目录回答；带文件或附加任务的请求不走此快捷入口。其他请求由一次结构化 Router 根据原始问题、
近期会话、文件状态、Agent 职责及已授权能力描述生成计划。文件存在不代表必须使用工具，多个步骤也不代表必须委派。

```text
AgentProfile + 当前用户权限 + 固定模式约束
  -> 已绑定可用能力摘要（Tool 描述、Skill 目录、子 Agent 职责）
  -> 语义 Router：意图、上下文三态、独立检索问题、候选工具名
  -> 严格 JSON 合同和候选交集校验
  -> 根据实际候选决定自动模式
  -> Run 内重新授权、预算与 Tool Pipeline
```

`AgentToolCandidateSelector` 不再维护逐工具触发词；新增 Tool 的描述来自现有 `@Tool` 和 Capability Registry，
绑定成功后就能进入 Router 目录，无需在规划器里补业务分支。Skill 经 `listAvailableSkills/useSkill` 使用；
子 Agent 经 `delegateToAgent` 使用，Router 和主 Agent 都能看到已绑定专家的名称与用途。

Router 只选输入目录中的精确工具名，最多 5 个。Skill 执行候选会补齐目录工具；OPTIONAL 检索会补齐对应已授权工具。
补齐后超过上限则保守降级，不截断依赖链。无效 JSON、重复字段、未知字段、非数字/越界置信度、超长字段、
未知或重复工具名均拒绝。模型自报置信度仅作为准入阈值，不能解释成测量得到的准确率。

模型负责理解用户意图；JSON 是服务之间的传输格式。Router 使用独立、严格的 ObjectReader 直接绑定
`SemanticRequestRouter.Decision`，不再逐字段读取 JsonNode。关闭隐式类型转换，缺失/null 字段、未知字段、
枚举序号、重复字段或候选均拒绝；业务层只校验有界长度、置信度和能力集合。
v7 默认调用 `McpModelService.callModelStructured`，通过 LangChain4j 的 ChatRequest.responseFormat 传递七字段
JSON Schema，客户端启用 strictJsonSchema。全部字段 required，禁止额外字段，枚举从 Java 定义生成。
Schema 保持稳定，工具目录仍在输入中，动态权限和长度/置信度/重复候选仍由 Java 校验；结构合法不表示理解正确。
统一模型门面按请求选择 TEXT / JSON_OBJECT / JSON_SCHEMA，普通 JSON 调用及后台记忆提取保留 JSON_OBJECT，
不再为 JSON 另建客户端缓存，YAML 默认模型也能收到格式要求。所有调用继续复用现有配额、重试和预算。

`app.agent.planning.response-format` 默认 JSON_SCHEMA；实际端点明确不支持时手动改为 JSON_OBJECT。
不根据厂商名称猜测支持，也不在超时、认证失败或格式拒绝后另发一种格式的请求。
原有瞬时故障重试仍保留；失败或非法合同沿用原有保守规划。后台“连接测试”只验证普通聊天，不证明 Schema 能力。

自动模式根据实际候选确定：含委派工具为 Orchestrated，含其他工具为 ReAct，没有候选为 Chat。
显式固定模式保留，目录从一开始就按模式过滤：固定 Chat 看不到工具，固定 ReAct 看不到子 Agent。
多步骤意图只是 Trace/Eval 标签，不再直接决定委派。首轮使用 Tool Choice AUTO，允许模型在参数不足时澄清；
工具参数齐全时框架提示要求实际发起调用，风险等级与是否审批仍由工具政策决定。

上下文计划保留四类来源：
- 会话历史：需要模型的请求只要 Session 有历史就 REQUIRED，空会话 OFF。
- 用户画像：需要模型时从 MySQL 独立读取，没数据则为空。
- 语义记忆、个人知识库：由 Router 分别规划 REQUIRED/OPTIONAL/OFF。
- REQUIRED 表示首次业务模型调用前尝试加载，不保证有命中；知识证据为空时提示模型说明证据不足。
- OPTIONAL 只有实际候选包含对应检索工具时才成立，否则降为 OFF。
- OFF 不预加载，且检索工具不会出现在实际模型规格中。

v6 将记忆读取按来源拆开：有会话历史时同时尝试读取当前 `sessionId` 的 Redis 工作状态，
画像独立从 MySQL 读取；`semanticMemory=REQUIRED` 仅调用 `MemoryManager.recallSemanticMemory` 检索相关长期记忆。
`searchMemory` 也共用此入口，使用同一用户过滤、topK 和分数阈值。预加载故障可以降级为空，工具故障则交给治理管道报错。
各来源独立读取，不会因为向量检索失败而清空会话和画像；Redis 不可用时工作状态为空，仍可能产生连接等待。

聚合 `MemoryContext` 不再包含全用户近期摘要，避免把另一个会话的近期话题混进当前回答。
短期摘要仍会写入并可在记忆中心查看，存量数据未删除。工作状态提示明确属于先前步骤，不能作为本次已执行的证明。

Router 根据近期会话生成 standalone `retrievalQuery`。REQUIRED 用它直接检索，OPTIONAL 将它作为数据放入
主 Agent 上下文供工具查询参考；最终回答始终保留原始用户问题。回答模型仍可根据工具结果调整后续查询。

规划失败后只保留会话、画像与已加载文件，不自动扩大工具集，也不执行额外检索。回答提示明确本次能力限制；
固定 ReAct/Orchestrated 虽保留模式，候选仍为空。新计划不再写入 `toolSelectionFallback`，
旧 Trace 的读取兼容保留。审批恢复和常规执行共用快照的 `callableToolNames()`，
再与暂停前候选取交集，不会因恢复而开放新绑定。

Router 输入最多 12000 字问题、64 个工具摘要及累计 64 个 adapter 目标，序列化输入上限 36000 字符。
超过上限整体保守降级，不能截断用户尾部条件后继续猜意图。近期历史保留最新一段，长度由配置控制。
这套有界目录适合当前规模；没有实现大规模工具语义索引或运行途中重新发现工具。

成本口径：顶层 Router 在根 Run 创建前执行，受用户模型配额和模型服务治理，不计入根 Run 业务预算；
子 Agent Router 已在根 Run 作用域中，同时消耗共享预算，取消/预算终止不能吞成普通降级。
配置 `app.agent.planning.model-id` 为空时沿用 Agent 模型，支持指定独立轻量模型。
配置方法：在模型管理中新增并启用规划模型（地址、凭据、模型名），把该记录的 modelId 写入 planning.model-id；
不要填供应商模型名或 API Key。该 ID 仍受当前租户访问控制；公共配置适合当前单租户部署，不能给不同租户共用
一个其他租户的私有模型 ID。多租户场景先保留为空，租户级规划模型角色配置属于后续工作。
2026-09-23 按用户要求将 local 规划角色显式绑定现有 `gpt-5.6-terra`（共享 default 租户、已启用），
modelId 为 `6fac3eb2-aea9-4bc7-a61a-ee65e3290e3c`；地址/凭据/参数复用模型记录，未新建小模型。
切换回答模型不会改变这一规划绑定；公共配置仍为空，其他环境不复用未经确认的本地数据库 ID。
配置变更需重启后端生效，原生 Schema 能力尚未在线验收。调研和验收方法见
[结构化路由验证记录](../../openspec/changes/modernize-agent-context-routing/structured-routing-verification.md)。
因此除本地快捷回答外，多了一次规划调用；耗时、成本和路由准确率必须用真实 Eval 衡量，不能直接声称更快或更准确。

Trace 记录决策来源、意图、上下文要求、候选工具和 Router 预算范围。计划中的“预期使用”
与工具 Journal 中的“实际调用”要分开查看。Trace 不保存完整 Prompt、记忆正文或隐藏推理。

知识库 REQUIRED 预加载和 OPTIONAL 的 `searchKnowledge` 共用 `RagRetrievalService`，
统一经过 Milvus、Elasticsearch BM25、RRF、Reranker 和引用生成，并使用 `tenantId + userId` 双重过滤。
检索入口只校验空查询和身份，不再按包含问候词跳过。预加载通过原有 SSE `rag_context` 展示引用；
按需工具将含 [R1] 编号及来源的证据返回给模型，实际调用记录在工具 Journal。此次未扩展按需工具的独立引用卡片事件。
未检索时不伪造未命中事件；工具检索异常与正常无命中分开处理。

前端分为两个产品面：普通用户只看到“我的 Agent、专家助手、知识、上下文”；文件作为知识来源进入“知识”页面；管理员通过独立入口切换到控制面，
处理模型、技能、数据源、用户 Agent 治理、审批和可观测性。管理员角色只由现有管理员主动分配，系统不提供自助提权流程。

## 用户、管理员与 Harness 的权限边界

Harness 管理的是“一次 Agent Run 能怎么执行”，管理员管理的是“平台允许哪些用户和能力存在”，两者不是同一个概念。
普通用户可以使用和配置自己的 Agent、会话、文件、知识、记忆以及个人 RAG 检索；管理员额外维护用户、角色、模型、
Skill、数据源、租户资源、审批、运行质量和用户 Agent 治理清单。该清单用于查看归属、角色、状态、详情和用户级 Trace，
不提供新增、编辑或删除用户 Agent 的入口；管理员只可启停专家助手，默认主 Agent 不可停用或删除。前端分区只是产品导航，
真正授权由 Spring Security 和服务层查询共同完成。

```text
JWT subject
  -> 查询当前 sys_user
  -> 校验 enabled
  -> 读取当前 tenantId + 启用角色
  -> Spring Security 路径鉴权
  -> Service 使用 tenantId + userId 做资源所有权检查
  -> Harness 固化本次 Run 的权限快照并按 Profile、模式和运行状态继续收窄
```

因此，管理员禁用账号或修改角色后，下一次 HTTP 请求立即使用数据库新状态；旧 JWT 中的角色和租户不能继续授权。
普通用户配置 Agent 时调用 `/api/v1/my/models`，只能看到启用模型的五个展示字段，模型密钥、Base URL、连接探测和
管理 CRUD 仍留在 `/api/v1/models/**` 管理控制面。普通注册是否开放及账号落入哪个租户同样由服务端配置决定。

知识同步的用户入口沿用个人知识边界，读取、保存、删除和手动触发都先查询
`knowledgeId + tenantId + createdBy`。定时同步线程没有登录上下文，继续使用已经持久化并经过用户入口校验的租户配置执行。

当前角色模型仍有明确债务：`ADMIN` 暂时代表平台治理角色，尚未拆分平台管理员和租户管理员；Harness 的高风险动作审批
也尚未拆成“用户确认”和“管理员批准”。这些能力不能仅靠增加前端按钮解决，需要后续独立设计作用域和状态机。

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

子 Agent 委派线程通过 `ContextPropagatingTaskDecorator` 继承同一个 Run Context，各任务竞争共享原子预算；线程任务结束后恢复原上下文，避免线程池复用造成串 Run。

工具预算统计的是实际进入工具实现的尝试次数，而不是模型提出工具调用的次数。未知工具、参数非法、无权限和风险策略拒绝都发生在预算前；一次允许重试的逻辑调用可能消耗多个工具预算，但始终共享同一个 `toolCallId`。

## 事件与 SSE

内部统一使用类型化 `AgentEvent`，每个事件都带 `runId`、mode、事件时间和安全载荷。只有 `AgentRunCoordinator` 可以发布 `RUN_STARTED` 和 `RUN_TERMINATED`。`GuardedAgentEventSink` 接受第一个终态事件后拒绝后续事件。

`AgentSseEventWriter` 负责兼容旧前端协议：模型输出仍是 `token/content`，工具仍是 `tool_call/toolName/result`，编排仍是 `orchestration`，终态仍是 `done`；同时新增 `runId`、mode、status、terminationReason 和 usage。流式请求的第一个 Agent 事件是 `run_started`。Chat 与 ReAct 都复用受治理的 `StreamingChatModel`，模型返回片段会立即转成 token 事件；完整回答只用于最终响应、会话持久化和 Trace，不再等模型全部生成后一次性伪装成流式输出。

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

`runId` 直接复用为现有 `agent_execution_trace.trace_id`，不新增表或字段。Chat、ReAct 和 Orchestrated 都走 `recordRun`。运行模式、终态、耗时、迭代/模型/工具调用次数、Token 和 deadline 写入现有 JSON 元数据列；请求计划、Tool Journal、上下文治理和安全的可见步骤继续保留。

Trace 与事件不保存 API Key、完整 System Prompt 或隐藏 Chain-of-Thought。Trace 落库失败只影响可观测性，不反转已经确定的业务终态。

Agent Eval 的版本化样本可标注期望 intent、mode 和部分 Context Plan；运行结果从真实 Trace 读取观察值。指标同时计算意图准确率、
模式准确率和上下文计划准确率。Context Plan 只比较样本显式标注字段，忽略动态 `retrievalQuery`；标签或 Trace 证据缺失时指标为
`unavailable`，不会用 0% 或 100% 掩盖数据缺口。

每个 Run 还持有固定容量的 Tool Journal。Journal 只保存 `runId + toolCallId`、工具名、准入结果、尝试次数、耗时、风险和脱敏/截断标记；达到容量后丢弃最旧记录并增加 overflow 计数。它汇总进现有 `shared_context_json`，不新增数据库表。

## Capability 扩展合同

Tool、Skill 和 sub Agent 共享只读 `AgentCapabilityDescriptor`：

```text
identity + type + version
input/output contract
tenant + owner + required permissions
risk metadata + lifecycle + availability
```

统一身份使用 `tool:<name>`、`skill:<skillId>` 和 `agent:<agentId>`。Tool 的风险与权限来自 `@AgentToolPolicy`；Skill 的稳定身份、版本和归属来自 `skill_config`，运行时可用性由 `SkillManager` 按稳定 `skillId` 精确确认；sub Agent 的版本使用 `AgentProfile.updatedAt` 修订标识，风险从当前有效工具集合保守推导。

`GET /api/v1/my/capabilities` 会重新读取持久化用户权限，只返回当前租户可见、当前用户有权限了解的能力。停用但仍可见的能力保留安全原因，供设置页禁选或移除旧绑定。Profile 保存时服务端再次校验三类身份、可用性、自引用和已知引用环，不能依赖前端隐藏防伪造。目录中已经消失的历史绑定只在设置页显示安全占位，允许移除但不能重新选择。

Capability Registry 只负责描述、发现和绑定准入，不保存执行器。Tool 仍只能通过 `AgentToolRegistry -> AgentToolExecutionPipeline` 执行，因此未来 MCP-backed Tool 也必须声明相同权限、风险、timeout 和结果边界，不能获得旁路。

Profile 的唯一组合字段是 `capability_bindings`：显式 `[]` 表示用户选择零能力，`NULL` 或损坏 JSON 直接失败关闭。设置页无损保存 Tool、Skill 和子 Agent 的稳定身份；不可用能力不能新增，已经绑定的不可用项可以移除；能力目录加载失败时禁止保存。默认个人 Agent 首次创建时固化当时可用的 Tool 快照，后续新 Tool 不会自动进入已有配置。

能力字段由 `AgentCapabilityConfigurationCodec` 统一解析，JPA Entity 不持有 ObjectMapper 或吞异常 helper。保存、响应、Registry 风险投影、循环引用检查、请求规划和 Runtime 只复用这一份合同，不再读取或投影 `tools/skill_id/datasource_id`。历史数据通过独立 SQL 审计、显式迁移和收口，启动与读取链路绝不静默补权限。

每层配置化 Agent 开始前都会生成不可变 `AgentCapabilityBindingSnapshot`。Chat 不构造能力规格；ReAct 只加入绑定 Tool 和有效 Skill adapter；Orchestrated 再加入委派 adapter。Skill 先检查当前 Scope 再按稳定 ID 精确执行，不存在的 ID 不会回退默认 Skill。子 Agent 在执行时重新读取自己的 Profile，使用自己的模型、Prompt 和能力快照，同时复用根 `AgentRunContext` 的迭代、模型、Tool、Token 与 deadline 预算。

委派活动路径最多包含 3 个 Agent，并拒绝重复 ID。委派 adapter 使用独立有界执行池，避免父委派占满普通 Tool worker 后等待下层 Tool；它仍经过共同的参数、RBAC、风险、预算、timeout、脱敏、Journal 和事件管道。审批恢复按 Checkpoint 的原执行 Agent 重建当前能力作用域，已撤销绑定或停用 Agent 会失败关闭。

## 当前边界

当前已实现组合式个人 Agent、三层受限委派、MySQL Checkpoint/Resume、单级人工审批和数据库恢复租约，但没有通用图引擎、任意 DAG、跨 Run 消息总线、多级会签、事件 replay 或跨系统 exactly-once。个人 Orchestrated 只开放 Profile 显式绑定的子 Agent，不等同于把租户全部专家开放给模型。内置 `updateHotelPrice` 只操作隔离沙箱，以 `approvalId + toolCallId` 数据库唯一键证明本地去重；真实支付、发布、发消息或业务写入仍需把幂等键传给下游，并提供对账与补偿。timeout 和租约都是协作式边界，不能强制终止忽略线程中断的 JDBC/HTTP 请求。组合运行链路目前完成静态验证，真实模型的父子委派、预算共享和取消行为仍需后端重启后的运行证据。

项目不再维护旧 `OrchestratorAgent`、系统专家注册表、专家适配器、关键词复杂度分类或并行预检。
顶层 Orchestrated 模式只由配置化 Agent 在能力快照内通过 `delegateToAgent` 受控委派；请求级资源和模式选择统一由
`PersonalAgentRequestPlanner` 完成。

面试或验收按 [Agent Harness 求职演示手册](../interview/AGENT_HARNESS_DEMO.md) 执行，未产生真实 Run 证据的步骤只能标记为未验证。
