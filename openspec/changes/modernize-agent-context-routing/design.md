# Design

## Context

现有 `AgentRunCoordinator` 已提供 Run 生命周期，`AgentContextGovernor` 已提供 Token 准入和保守裁剪，工具执行也已有完整治理。缺口位于模型调用之前：`PersonalAgentRequestPlanner` 通过关键词决定 `ragRequired`、`memoryRequired` 和 `historyRequired`，导致会话连续性、语义检索和评测证据都受限于脆弱规则。

## Goals

- 让“选择哪些上下文来源”和“上下文过长如何裁剪”成为两个清晰阶段。
- 让多轮会话连续性由 Session 状态决定，而不是代词关键词决定。
- 让规则、模型 Router 和主 Agent 各自只处理适合自己的决策。
- 让每次路由都有可持久化、可评测、无敏感正文的证据。

## Non-Goals

- 本次不实现通用 DAG、事件回放或跨系统 exactly-once。
- 本次不改变 RAG 的 Milvus、Elasticsearch、RRF 和 Reranker 实现。
- 本次不让普通 Chat 无条件暴露全部工具。

## Decisions

### C004：长期记忆写入准入修复（2026-09-23）

针对真实验收的回忆问句与临时任务约束误入库，删除基于 contains 的“显式意图→正则/整句哈希直接入库”路径。显式与隐式对话统一进入 SemanticMemoryExtractor 的一次模型调用，返回候选的 speechAct（ASSERTION/SAVE_REQUEST/QUESTION/QUOTATION/HYPOTHETICAL/REJECTION）及 scope（CROSS_SESSION/CURRENT_TASK）。服务端仅接受 CROSS_SESSION 的 ASSERTION/SAVE_REQUEST，同时严格验证类型、证据、有限置信度、长度及类型对应的语义键；不增加业务词表或第二次模型审判。

仅完整命令前缀“请记住：/记住：/帮我记住：”用于选择同步调度，不代表允许保存；其他消息走原有异步线程池。同步和异步都遵守 semantic-extraction-enabled，模型失败/合同错误不写语义记忆。短期摘要仍保存供会话管理。同步明确保存将增加一次模型提取耗时，不能再把任意含“记住”的问题同步写为 confidence=1 的原句。

真实验收覆盖两条原故障、显式保存、稳定偏好修正、否定保存、引用/假设、敏感信息及混合句中的稳定事实。不自动重写或删除已有用户记忆；只清理本轮隔离验收数据。此修复按用户“继续修复”授权落地，不新增测试类。

### C003：原生结构化路由（2026-09-23）

用户授权“两种方式哪个好哪种好直接改”。优先采用原生 JSON Schema，在 ChatRequest 中传递七字段合同，OpenAI-compatible 客户端启用 strictJsonSchema；保留 Java 类型和业务校验，因为结构约束不保证语义或权限正确。通过 planning.response-format 显式选择 JSON_SCHEMA（默认）或 JSON_OBJECT（兼容），不按厂商名称猜测支持，也不在调用失败后额外尝试另一格式。独立轻量模型沿用 planning.model-id，不虚构模型配置或凭据。

输出格式改为每次请求的参数，复用同一个受治理调用和同步客户端，删除重复 JSON 客户端缓存，确保 YAML 默认客户端也收到 JSON 格式要求。Schema 采用稳定结构和来自 Java 枚举的值，动态工具白名单继续由服务端校验，避免每次工具目录变化都生成新 Schema。现有摘要提取保持 JSON_OBJECT，普通 Chat/Streaming 行为不变。版本升级 v7，按项目约束只静态审查，不新增测试或主动编译、调用外部模型。

官方依据及兼容性边界记录在 structured-routing-verification.md；该文件的运行验收状态必须与静态检查区分。

### D1：结构化 Context Plan

引入 `ContextRequirement` 与 `RequestContextPlan`。四类来源分别是会话历史、用户画像、语义记忆、知识库；每类使用 `REQUIRED/OPTIONAL/OFF`。`REQUIRED` 在模型首次调用前加载，`OPTIONAL` 通过精确候选工具暴露，`OFF` 不加载也不暴露对应工具。

### D2：会话历史确定性启用

同一 Session 只要存在已持久化历史就标记 `REQUIRED`。是否裁剪由已有 `AgentContextGovernor` 决定，规划器不再猜测当前句是否为追问。

### D3：能力描述驱动的语义规划（2026-09-23 修订）

保留完整句精确匹配的能力介绍快捷入口；携带文件、Session 历史、执行模式与能力绑定以结构化字段表达。移除通用知识、业务领域、动作、多步骤动词数量与逐工具中文触发词表。其余请求统一由一次 `SemanticRequestRouter` 调用规划，输入当前问题、近期历史、Agent 职责、文件状态以及当前模式下已绑定且授权的能力摘要。工具描述复用 Capability Registry；Skill 和子 Agent 通过通用 adapter 暴露目录摘要，不添加业务专用路由。

Router 返回受限枚举、三态上下文、独立检索查询与最多 5 个精确工具名；名称必须来自提供给 Router 的目录。未知工具、错误字段类型、非法枚举、过低/越界置信度、超长输出均拒绝并降级，无授权集合扩大 fallback。工具选择器只把受验证选择映射为运行候选，补齐 Skill 目录/执行组合与 OPTIONAL 检索，拒绝超过候选上限的组合。自动模式由实际候选决定：有委派 adapter 为 Orchestrated，有其他候选为 ReAct，无候选为 Chat；固定模式保持用户配置。

OPTIONAL 必须有对应已授权且实际入选的检索工具，否则降为 OFF；REQUIRED 仅预加载，避免重复检索；OFF 对应工具不会进入实际工具规格。执行器重新与最新能力快照求交集，取消旧的“候选失败后开放全部绑定工具”行为。Router 失败后仅保留会话历史、用户画像和已加载文件，在回答提示中明确无法查询私有资料或执行动作，避免伪造执行成功。

能力目录和路由输入有字符/数量上限，超过整体目录上限时保守降级而非任意丢弃可用工具；Router 成本增加是明确取舍。根请求 Router 处于业务 Run 之外，子 Agent Router 在根 Run Scope 内计费，证据按真实作用域记录。

审批恢复与普通执行复用能力快照的可调用工具集合（含有效 Skill/委派 adapter），并与 Checkpoint 原候选取交集；不能因恢复只检查普通 toolNames 而丢掉原先合法 adapter。该任务依赖在 Task 12 中修复，不重建或扩大暂停时的候选。

本修订验收（责任人：本会话实现者；环境：本地工作树静态审查，遵从用户不新增测试类及项目不主动构建约定）：词表/动词计数为 0；Router 无未知工具准入；OFF 不暴露检索；固定 Chat 不开放工具；失败不扩大候选；元数据新增工具无需修改路由器。真实模型质量与延迟需由 Agent Eval 人工验收，静态检查不代表这些指标通过。

参考（2026-09-23 查阅）：
- https://www.anthropic.com/engineering/building-effective-agents
- https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents
- https://docs.langchain.com/oss/python/langgraph/overview

### D4：检索查询与回答问题分离

计划同时保存原始问题和 `retrievalQuery`。需要前文时，Router 基于最近会话生成 standalone query；检索使用改写查询，最终回答仍保留原始问题，避免语义漂移。

### D5：复用现有受治理检索工具

`searchKnowledge` 与 `searchMemory` 已存在，不新增旁路执行器。OPTIONAL 来源通过候选工具进入 ReAct；所有执行继续经过 `AgentToolExecutionPipeline`。

### D6：扩展现有 Agent Eval

在数据集样本与样本结果中增加期望/观察到的 intent、mode、context plan。指标计算器新增路由准确率和上下文计划准确率，并保持缺失证据为 unavailable。

## Risks / Trade-offs

### C002：意图合同、记忆装配和统一检索（2026-09-23）

- 规划模型仍负责理解意图，JSON 只作为内部传输格式。将 JsonNode 字段逐个读取替换为严格反序列化的 Decision 类型，保留缺字段、null、未知字段、类型强转、枚举序号、重复字段/候选及越界检查。不直接切换所有兼容服务为原生 JSON Schema：尚无厂商支持的实测证据，也不另建绕过配额的 AiServices 客户端。轻量模型仍通过已有 model-id 指定。
- 当前会话历史和工作状态按 Session 装配，画像独立加载；跨会话 semanticMemory 只控制相关长期记忆检索。移除聚合上下文中的全用户近期摘要，保留摘要存储、查看和清理功能；不删除用户数据。长期记忆预加载和 searchMemory 复用同一检索入口，避免阈值/身份规则分叉。预加载允许独立降级，工具检索失败保留错误，不伪装未命中。
- searchKnowledge 改走 RagRetrievalService，与 REQUIRED 预加载复用混合召回、精排、引用和 tenantId + userId 双重隔离。移除旧纯向量知识检索分支、专用格式化和重复参数。RAG 入口只校验空查询和身份，不再用包含问候词的规则跳过真实查询。工具结果保留引用编号和来源，区分服务不可用与无命中。
- 版本升级为 request-planner-v6，便于区分上下文装配和检索口径。同步文档并静态审查；遵从用户/项目约束，不新增测试类、不主动编译构建。运行效果及模型兼容性未实测。
- 代码引用审查后补充 Task 15a：移除停止自动注入摘要后唯一失去调用的 ShortTermMemory.recall 与配套查询，保留摘要写入、用户查看和定时维护功能。

验收方法：本会话实现者在当前工作树逐项追踪 DTO 解析、MemoryManager 读路径、RAG 双入口和下游过滤参数；补充非法合同、记忆 OFF、同租户不同用户、纯向量遗漏编号等人工验收输入。此方法不替代运行验收。

- 除完整能力介绍外，请求均增加一次 Router 模型调用，会增加延迟和 Token；保留有限本地快捷入口并记录 Router 使用情况，实际收益须重新评测。
- 始终携带近期会话可能引入旧信息；通过消息数上限和 Context Governor 控制，不通过关键词关闭连续性。
- OPTIONAL 检索依赖模型工具选择能力；黄金集必须分别衡量“是否应检索”和“是否实际调用”。
- 数据库新增字段需要幂等升级脚本；历史 Eval 数据允许字段为空，并显示指标不可用。
