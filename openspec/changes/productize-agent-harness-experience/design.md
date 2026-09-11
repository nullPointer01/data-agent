## Context

当前系统已经有统一 `AgentRunCoordinator`、类型化 SSE 事件、五类运行预算、Tool Journal、Trace、RAG 引用和持久化审批 Run。`ChatPage` 也能显示知识引用、工具调用和部分执行事件，但运行信息只存在于单条消息的临时前端状态中；刷新历史会话后不能恢复完整 Run 证据，普通用户也没有独立运行记录入口。管理员侧的 Trace、Quality、Approval 页面各自成立，却没有围绕同一个 Run 形成产品闭环。

本变更横跨 React 信息架构、Agent Run API、运行持久化、质量评测和上下文管理。现有工作区包含未提交改动和多个未归档变更，必须以当前文件为准做增量演进，不能通过重置或重写恢复到历史版本。

约束：JDK 17、Spring Boot 3.2、React/Vite、MySQL、Milvus、Elasticsearch；用户此前要求不新增/运行自动化测试且不编译后端。第一阶段不引入新依赖。

## Goals / Non-Goals

**Goals:**

- 让首次访问者在 3 分钟内理解项目是 Agent Harness，而不是普通聊天壳。
- 让一次 Run 从请求、执行、证据、终态到反馈都能使用同一个标识追踪。
- 在不暴露隐藏推理和敏感工具数据的前提下展示足够的工程证据。
- 用明确任务契约和离线评测回答“Agent 到底有没有完成任务”。
- 以阶段化方式补齐上下文治理和能力扩展合同，每阶段都可单独演示和回滚。

**Non-Goals:**

- 不实现拖拽工作流、Agent 市场或全功能 SaaS 多租户计费。
- 不展示模型 Chain-of-Thought；“可解释”仅指路由、检索、工具、审批、预算和终态等操作证据。
- 不用 LLM-as-judge 的单次主观输出冒充真实业务成功率。
- 不把现有进程内 Tool timeout 描述成强沙箱，也不增加容器执行服务。

## Decisions

### 1. 以 Agent Run 作为产品和数据的唯一关联主键

所有执行模式继续由 `AgentRunCoordinator` 创建唯一 `runId`，且 `traceId` 沿用同一个值。SSE 只负责实时投递；终态、预算与恢复状态通过所有者可见的 Run 查询合同读取。前端刷新后根据消息中的 Run 引用恢复证据，不长期依赖内存中的 `traceEvents`。

替代方案是新增“前端任务 ID”并聚合 Trace、审批、会话，这会产生双主键和状态漂移，因此拒绝。

### 2. 将 UI 分成 Reference App 与 Control Plane，而不是按数据库实体分菜单

普通用户侧围绕“我的 Agent、任务、知识”组织；文件作为知识来源合并到知识体验，记忆作为 Agent 配置/上下文的一部分呈现。管理员侧围绕“Runtime、Capabilities、Governance”分组，已有页面先重组导航，再逐步合并重复页面。

替代方案是删除现有页面，只保留聊天页。这样会隐藏 Harness 深度，也会损失管理和诊断能力，因此拒绝。

当前页面到目标信息架构的映射如下，Stage 1 先改导航语义和入口，不删除仍有独立用途的页面：

| 当前页面 | 目标位置 | 处理方式 |
|---|---|---|
| 我的 Agent / `chat` | Reference App / 工作台 | 保留为首屏，加入 Run 摘要、时间线和 Harness 状态 |
| 知识库 / `knowledge` | Reference App / 知识 | 保留，强调它是 Agent 的证据来源 |
| 文件 / `files` | Reference App / 知识来源 | 一级导航并入“知识”，页面暂时保留为二级入口 |
| 记忆 / `memory` | Reference App / 上下文 | 保留但改为 Agent 上下文语义，后续展示上下文治理证据 |
| 执行追踪、动作审批 | Control Plane / Runtime | 放在运行治理组，围绕 `runId` 关联 |
| 统计、质量、反馈 | Control Plane / Observability | 放在质量与观测组；Quality 是线上健康，Agent Eval 是离线回归 |
| Agent 模板、模型、技能、数据源、RAG 调试 | Control Plane / Capabilities | 放在能力配置组，避免与用户任务入口平级 |
| 用户、角色权限、审计 | Control Plane / Governance | 放在访问与治理组 |
| `OverviewPage` | 无当前路由 | 视为遗留未使用页面，不作为新首页；清理需独立确认引用后执行 |

普通用户一级导航目标为 3 项：`我的 Agent`、`知识`、`上下文`。管理员控制台分为 4 个概念组，但保留现有 RBAC 与页面权限，不通过导航隐藏代替服务端授权。

### 3. 使用安全的操作事件时间线，不展示隐藏推理

时间线事件统一包含事件类型、时间、状态、标题和安全摘要。允许展示路由/计划、RAG 引用、工具名与脱敏结果、审批、重试、预算和终态；禁止展示模型内部思维链、原始凭据和完整敏感参数。历史事件优先来自持久化安全事件或 Trace 摘要，实时事件来自 SSE。

替代方案是直接把模型 thinking 文本输出到页面。该方案不可控且容易泄露敏感上下文，因此拒绝。

### 4. 任务成功与运行成功分离

`runStatus=COMPLETED` 仅说明运行正常结束；新增 `outcomeStatus` 表示目标是否达成。请求未提供成功标准时返回 `NOT_EVALUATED`。提供标准后，先支持确定性断言和显式人工评价，后续可选择 LLM judge，但必须记录 judge 模型、Prompt 版本和证据，且不能覆盖原始运行结果。

替代方案是把 HTTP 200 或用户点赞当作任务成功，两者都无法表达任务是否真正完成，因此拒绝。

### 5. Agent Eval 采用版本化数据集与样本级证据

评测运行绑定 Agent Profile 版本、模型配置标识、数据集版本和 Harness 配置快照。聚合指标必须能下钻到样本，失败样本保留稳定原因码。RAG benchmark 作为知识型样本的子指标复用，不与 Agent 完成率混为一谈。

替代方案是直接从线上 Trace 聚合一个“质量分”。现有 Quality 看板可以提供运行健康线索，但缺少固定输入和期望输出，不能证明版本改动有效，因此只作为观测而非回归评测。

### 6. 上下文治理位于模型调用前的统一边界

新增 Context Governor，在每次模型调用前基于模型窗口、Run Token 预算和消息优先级选择上下文。系统指令、当前任务、未完成工具交互和最近关键证据不可被静默丢弃；触发压缩时记录输入估算、输出估算、保留项和原因，原文仍受既有数据保留策略控制。

替代方案是在每个 Agent/专家内部各自截断，容易产生行为不一致且无法统一评测，因此拒绝。

### 7. 扩展能力采用 Descriptor + Registry 合同

Tool、Skill、子 Agent 都通过统一的 Capability Descriptor 描述标识、版本、Schema、权限、风险、所有者和生命周期状态；执行仍由各自 Registry/Invoker 完成。第一版先定义只读目录和验证合同，不实现远程插件市场。MCP 作为后续 Tool Provider 之一，而不是替换 Harness。

### 8. 问题日志是变更验收的一部分

`problem-log.md` 使用稳定编号记录问题、发现阶段、影响、根因、决策、修改位置、验证证据和状态。无法当期解决的问题必须标记残余风险和后续任务，禁止直接删除记录。

### 9. Agent 组合使用稳定身份列表，不复用互斥 Agent 类型

新增 `capability_bindings` JSON 列表作为组合能力的权威配置，元素统一使用 `tool:<name>`、`skill:<skillId>`、`agent:<agentId>`。旧 `tools` 与单值 `skill_id` 继续服务旧客户端和专用 Agent 类型，不再承担通用组合语义。

`capability_bindings IS NULL` 表示尚未迁移的旧 Profile，继续使用原兼容规则；`capability_bindings = []` 表示用户明确选择零能力。新客户端保存时总是提交显式列表，避免“零工具”被历史逻辑解释为“全部工具”。

替代方案是新增 `skill_ids`、`sub_agent_ids` 两个独立 JSON 字段。该方案会继续让每种能力拥有不同合同，未来接入 MCP Provider 时还要再加字段，因此拒绝。

### 10. Skill 与子 Agent 通过受治理的内部适配工具进入 ReAct

Tool 继续直接走 `AgentToolExecutionPipeline`。绑定 Skill 时 Runtime 自动暴露内部 Skill 适配工具；绑定子 Agent 且处于 `orchestrated` 模式时自动暴露委派适配工具。适配工具本身也注册治理元数据并消耗 Tool 预算，具体 Skill/子 Agent ID 再与当前能力绑定快照核对。

每层子 Agent 使用自己的模型、Prompt 和能力绑定，但复用根 `AgentRunContext`，因此共享 deadline、迭代、模型调用、工具调用与 Token 预算。能力作用域随异步工具执行传播；委派守卫维护活动 Agent 路径，默认最多 3 层并拒绝重复 Agent ID。审批恢复根据 Checkpoint 的执行者重新解析当前绑定，不沿用过期权限。

替代方案是为每个子 Agent 创建完全独立 Run。它会让一次用户任务产生多个不受统一预算约束的 Run，证据和取消语义也会碎裂，因此第一版拒绝。

### 11. 四种模式具有明确能力边界

- `chat`：单模型回答，可使用 RAG/记忆上下文，但不暴露 Tool、Skill 或子 Agent。
- `react`：暴露显式绑定的 Tool 和 Skill，不允许委派子 Agent。
- `orchestrated`：在 ReAct 循环上增加显式绑定的子 Agent 委派，仍由同一 Harness 控制。
- `auto`：快速路径走 Chat；非快速任务在存在可用子 Agent 时走 Orchestrated，否则走 ReAct。

这与现有面向系统专家的全局 `OrchestratorAgent` 并存。个人 Agent 的编排只看该 Profile 的绑定，不把租户内所有专家自动开放给模型。

## Delivery Stages

### Stage 1: Harness Workbench

重组导航和首屏，补齐 SSE 终态消费、运行摘要、事件时间线和用户所有者 Run 查询。完成后即可演示一次任务如何被 Harness 路由、约束并产出证据。

### Stage 2: Outcome & Eval

增加任务目标/成功标准、结果判定和版本化 Agent Eval。优先确定性、可复现的样本，不先做复杂在线自动评委。

### Stage 3: Context & Recovery Evidence

加入 Context Governor、压缩证据和更完整的暂停/恢复时间线；用长会话和审批 Run 证明效果。

### Stage 4: Extension & Interview Demo

统一能力目录边界，补三条端到端演示脚本、架构图和限制清单。

### Stage 5: Composable Agent Capabilities

增加显式能力绑定、Skill 适配执行、受限子 Agent 委派和四模式一致路由，再把三类能力选择真正接入个人 Agent 设置页。

## Risks / Trade-offs

- **[Risk] 当前 durable Run 默认关闭，普通 Run 不写 `agent_run_state`** -> Stage 1 先完整消费 SSE 终态信息，并从始终写入的 Trace 提供 owner-only 安全投影；开启 durable 后再合并审批与恢复状态，不新增第三套 Run 表。
- **[Risk] Trace 当前偏管理员视角，直接开放可能越权或泄露** -> 新增所有者专用 DTO，仅返回安全字段；不复用管理员 Trace DTO 直接下发。
- **[Risk] 页面一次展示过多内部信息会影响普通使用** -> 默认只展示结果与 3-5 个状态摘要，时间线放入可展开的 Run Inspector。
- **[Risk] LLM 评测不稳定且有成本** -> 第一版使用确定性断言、人工标签和可选 judge；所有自动评测标记方法和模型。
- **[Risk] 上下文压缩可能丢失关键约束** -> 使用不可丢弃消息类别、压缩前预算检查和保守回退；未验证前默认关闭。
- **[Risk] 变更跨度大** -> 每阶段独立验收，任务控制在最多 5 个文件；阶段失败不阻止保留上一阶段可用版本。
- **[Risk] 子 Agent 递归放大成本或形成环路** -> 所有层共享根 Run 预算，保存时检查静态引用环，运行时再限制深度并拒绝活动路径中的重复 Agent。
- **[Risk] 旧空 Tool 列表表示全部工具** -> 以 nullable `capability_bindings` 做版本分界；新显式空数组不再回退到全部工具。
- **[Risk] SkillManager 是进程级注册表** -> 执行前必须先用租户、所有权、RBAC 和绑定快照解析稳定 ID，禁止仅凭自然语言全局匹配执行。

## Migration Plan

1. 先以无数据迁移的前端工作台与现有 SSE 字段完成 Stage 1A。
2. 对缺失的历史 Run 证据增加向后兼容 API；新字段均可选，旧客户端继续可用。
3. 需要新增表/列时提供独立幂等 SQL，先写后读再切换 UI；不依赖 JPA 自动建表。
4. 每阶段前后更新问题日志并执行静态门禁；前端产物验证后再同步到 Spring Boot 静态目录。
5. 回滚时优先回退前端入口和新 Feature Flag；新增数据结构保留，不执行破坏性删除。

## Open Questions

- 普通非审批 Run 不强制进入 `agent_run_state`：保持 durable Feature Flag 语义，普通历史证据使用 Trace 的 owner-only 安全投影，审批/恢复证据再与 durable 状态合并。
- Agent Eval 的首批三条真实演示任务需要结合用户最终用于面试的业务故事确认，框架实现不依赖具体题目。
- MCP 的接入深度延后到 Stage 4 决策，当前不影响核心 Harness 展示。
