# AI 应用面试下午突击版

> 定位：Java 后端转 AI 应用工程。目标不是证明会训练模型，而是证明能把 LLM、企业数据、工具、权限和可观测性做成可控系统。

## 1. 今天只背这一条主线

```text
用户请求
  -> Spring MVC 接入
  -> AgentRuntimeService 路由
  -> Orchestrator 或 ReAct
  -> RAG + Memory 构建上下文
  -> 模型根据 Tool Schema 发起 Tool Call
  -> 后端执行受控工具并回填 Observation
  -> 模型生成最终回答
  -> SSE 返回过程事件
  -> Trace / Token / Audit 落库
```

Agent 的一句话定义：

> Agent 不是更会聊天的模型，而是由模型驱动的执行系统。模型负责理解、推理和选择下一步，工具连接真实数据，RAG 和 Memory 补充上下文，Runtime 负责循环、权限、成本、错误恢复和可观测性。

## 2. 90 秒自我介绍

> 我目前在同程旅行做酒店商品库和促销相关的 Java 后端开发，做过千万级数据拉取、多进程任务、Kafka 实时变更、JVM Full GC 和 OOM 治理，也做过酒店收益策略预测系统。在收益预测项目里，我接入过 DeepSeek，把 27 个核心指标通过固定 Prompt 和 HTML 模板生成分析报告。
>
> 我希望往 Java 后端加 AI 应用工程方向发展，所以做了 Data Agent 个人项目。它不是单次调用模型，而是一套 Spring Boot + LangChain4j 的 Agent Runtime，包含 ReAct 工具循环、Orchestrator 多专家编排、RAG 混合检索、分层记忆、多模型配置、SSE、Token 配额、RBAC、多租户和执行追踪。
>
> 我的优势不是模型训练，而是把传统后端的并发、稳定性、权限、监控经验带到 AI 应用里。我对 Agent 最大的理解是：推理可以给模型空间，但动作必须有权限、预算、审计和必要的人审，也就是带刹车的自主。

## 3. 60 秒项目介绍

> Data Agent 面向企业数据分析场景。用户可以上传文件、维护知识库、配置数据源和模型，再用自然语言完成检索、SQL 查询、计算和图表生成。
>
> 请求进入 Agent Runtime 后，根据命令、指定 Agent、指定 Skill 等条件路由；核心执行有 ReAct 和 Orchestrator 两种模式。ReAct 最多执行 8 轮“模型决策、工具调用、结果观察”，Orchestrator 则按意图拆任务并路由给数据、知识、图表、报告等专家。
>
> 为减少幻觉，RAG 使用 Milvus 向量召回和全文召回，再做 RRF 融合、轻量重排、父上下文补全、压缩和引用。生产治理上做了错误分类重试、断路器、Token 配额、SQL 只读、租户隔离、SSE 结构化事件和执行轨迹。

## 4. 十个必会问题

### 4.1 Agent 和普通 ChatBot 有什么区别？

ChatBot 通常是一次输入、一次输出；Agent 围绕目标多轮执行，能选择工具、读取外部数据，并根据 Observation 调整后续动作。关键不只是“有工具”，而是模型拥有有限的决策权。

### 4.2 ReAct 怎么实现？

ReAct 是 Reasoning + Acting。每轮把消息历史和工具 Schema 发给模型：返回 Tool Call 就执行工具，并把结果作为 Observation 加回消息；返回最终答案就停止。项目用 `ReActLoopRunner` 控制循环，最大 8 轮，并处理工具失败、最大轮次、工作记忆、SSE 和 Trace。

加分句：

> ReAct 难点不是 while 循环，而是模型协议遵循度、循环收敛、工具失败、上下文增长和轨迹复现。

### 4.3 模型怎么选择工具？

后端把工具的 `name + description + parameters JSON Schema` 传给模型。模型只产生结构化 Tool Call，真正执行由 `AgentToolInvoker` 完成。`description` 要写“什么场景用”，参数 Schema 要限制类型和含义。

项目当前有 18 个 `@Tool` 方法，覆盖 Skill、文件、知识、记忆、SQL、计算、图表和酒店经营分析。

### 4.4 ReAct 和 Orchestrator 怎么选？

- ReAct：模型临场决定下一步，适合开放式、探索式问题。
- Orchestrator：先分类、拆任务、分派专家，适合结构清晰、可并行、需要审计的流程。

项目要如实说：同步默认请求优先走 Orchestrator；流式默认请求当前直接走 ReAct。两条入口策略还没有完全统一，这是可以继续收敛的架构点。

### 4.5 RAG 完整链路是什么？

```text
文档解析
  -> Markdown 结构分块 / 语义分块 / 父子块
  -> Embedding
  -> Milvus + 全文索引

Query
  -> 规则化 Query Rewrite
  -> 向量召回 + 全文召回
  -> RRF 融合
  -> 轻量规则重排
  -> 父上下文补全
  -> 按字符预算压缩
  -> 引用注入 Prompt
```

为什么不是纯向量：向量擅长语义，但订单号、字段名、专有名词等精确词容易漂；全文检索补精确命中，RRF 用排名而不是直接混合不同量纲的原始分数。

### 4.6 分块怎么设计？

先保留 Markdown 标题、段落、表格、代码等结构，再按句子边界合并到目标大小；超长块才强制切分并加 overlap。检索用小块提高精度，返回时补同 section 的父上下文，兼顾精确召回和语义完整。

### 4.7 RAG 如何评估？

分两层：

- 检索层：Recall@K、MRR/NDCG、引用命中率、各通道召回数和耗时。
- 生成层：groundedness、答案相关性、引用正确性、无答案时是否拒答。

如实说：项目已有检索 Trace、质量接口和用户反馈，但系统化离线 Golden Dataset 还不完整，这是下一步最优先补的能力。

### 4.8 Memory 和 RAG 有什么区别？

RAG 面向企业知识和文件；Memory 面向用户、会话、偏好和历史结论。两者都可能进入 Prompt，但来源、生命周期和权限边界不同。

项目分 working、short-term、long-term 和 user profile。工作记忆用 Redis，持久记忆落 MySQL，长期记忆可写 Milvus；有压缩、配额、衰减、晋升和保留策略。模型压缩默认关闭，以降低延迟、成本和信息损失风险。

### 4.9 模型失败和成本怎么治理？

不能无脑重试：401/403/404 一般是鉴权或配置问题，立即失败；429 和 5xx 才指数退避重试。项目单次最多尝试 3 次，连续失败达到阈值后按模型打开 60 秒断路器。

Token 治理是调用前查每日用户配额，调用后记录 prompt/completion/total token，并打耗时和成功率。当前是 Token 配额与记账，不是人民币成本核算，也没有按成本自动切换备用模型。

### 4.10 怎么保证 Agent 安全？

不能靠 Prompt 保安全。模型只能访问受控工具；SQL 工具限制只读查询；数据按 tenantId 隔离；RBAC 控制管理能力；API Key 加密；外部 URL 做 SSRF 防护；工具调用写 Trace/Audit。写库、改价、发消息等高风险动作需要 Human-in-the-loop。

## 5. 三个高分技术故事

### 故事一：弱模型“只说要调用工具，但没发 Tool Call”

S：不同模型跑同一 ReAct 问题，弱模型输出“我先查询”，却没产生原生 Tool Call，循环误判为最终答案。

T：保证多模型下工具执行行为稳定。

A：把 ReAct 中间工具决策改为完整响应解析，SSE 仍推送结构化过程事件；再加 intent-without-action 守卫，检测“有调用意图但无 Tool Call”时纠偏，最多 2 次。

R：多步工具链稳定性提升。认知是“模型能力差异先用机制消除，再用有限兜底吸收”。

边界：这是个人项目的对照实验和工程改造，不要说成线上大规模指标。

### 故事二：补齐 ReAct 流式路径 Trace

S：同步 Orchestrator 有 Trace，但默认流式 ReAct 的 `done` 事件没有 `traceId`，事后无法关联用户反馈和执行过程。

T：让流式 ReAct 也可复现、可审计。

A：为 ReAct 结果补 iterations、toolCallCount、success；新增 ReAct Trace 落库；`done` 返回 traceId；结构化日志用 sessionId/traceId 关联。

R：从“只能看最终答案”变成能定位第几轮、用了几个工具、哪里失败。

### 故事三：RAG 选择确定性 Baseline

S：纯向量检索对编号和专有名词不稳；每个环节都调用 LLM 又会增加延迟、成本和不确定性。

T：先做可解释、可测试的企业知识检索基线。

A：向量 + 全文双路召回，RRF 融合；Query Rewrite、Rerank、Compression 先用规则和加权打分；补父上下文与引用。

R：链路可观测、行为确定、可单测。后续有评估集后，再判断是否用 LLM rewrite 或 Cross-Encoder reranker，避免为了高级而高级。

## 6. 简历里的“可讲 / 不可吹”

| 简历词 | 可以讲 | 不要讲成 |
|---|---|---|
| 多模型 API / 模型路由 | 按租户配置默认模型、请求可指定 modelId、OpenAI 兼容接入、缓存刷新 | 按质量/成本自动选择模型，失败自动切备用模型 |
| 结构化输出 | `response_format=json_object` + JSON 解析 + 失败兜底 | 100% 保证 Schema 永不出错 |
| RAG 重排 | RRF 后按融合分、关键词覆盖、来源权重做轻量重排 | Cross-Encoder 或 LLM reranker |
| 问题改写 | 清理上下文标记、停用词、关键词和查询变体 | LLM 多查询扩展 |
| 上下文压缩 | 按引用 section、句子边界和 max chars 裁剪 | LLM 摘要压缩 |
| 模型降级 | 错误提示、确定性分类/规划、模型压缩关闭时走规则 | 自动切换备用厂商模型 |
| Token 成本治理 | 配额、估算、真实 usage 记账、上下文预算 | 已做人民币计费或成本最优路由 |
| Redis 分级缓存 | 会话/working memory、Spring Cache、Redis 异常时部分查询回 DB | 所有链路都有多级本地 + Redis 缓存 |
| 生产化 | 有权限、租户、审计、重试、熔断、指标和测试 | 已经过真实大规模生产流量验证 |

数字口径：简历的“12 类工具”来自早期版本，当前代码是 18 个 `@Tool`。面试口头统一说“十余个受控工具”，避免版本数字冲突。

## 7. 面试官质疑“是不是 AI 写的”

> 我确实把 Claude Code、Cursor 当日常开发工具，但不是把生成代码当结果。我负责需求拆解、架构边界、验收标准和故障定位，并通过对照实验、单元测试和代码追踪验证实现。例如模型只输出调用意图但不发 Tool Call、流式 ReAct 没有 Trace，都是我从运行行为反推到具体链路后修的。AI 提高编码速度，但关键判断、验证和责任仍然在我。

不要回答“全部是我手写的”，也不要回答“AI 帮我生成的，我大概懂”。重点用一个真实 Debug 故事证明所有权。

## 8. 不会的问题怎么接

```text
先给定义 -> 说项目当前实现 -> 说取舍 -> 承认边界 -> 给演进方案
```

示例：

> Cross-Encoder reranker 会把 query 和候选文档成对输入模型，相关性通常比向量相似度更准，但延迟和计算成本更高。我当前项目没有上 Cross-Encoder，而是用 RRF 加关键词/来源权重做可解释 baseline。下一步会先建立标注集，对比 NDCG、Recall 和 P95 延迟，收益足够再替换。

## 9. 面试前 2 小时安排

1. 20 分钟：脱稿说 90 秒自我介绍和 60 秒项目介绍，各录两遍。
2. 35 分钟：只背 ReAct、Tool Calling、RAG、Memory 四题。
3. 25 分钟：背“可讲 / 不可吹”，尤其规则 Rerank、无自动模型切换、未上真实生产。
4. 25 分钟：各讲一遍三个 STAR 故事。
5. 15 分钟：复习简历里的酒店收益预测项目，准备“工作 AI 项目”和“个人 Agent 项目”的区别。

最后一句收口：

> 我不是做模型训练的，我的价值是把 LLM 接进 Java 企业系统，并用传统后端工程能力解决 Agent 的数据、权限、稳定性、成本和可观测问题。
