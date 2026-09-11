# Agent 开发面试作战手册

> 适用目标：Java 后端 / 业务开发转 Agent 开发 / 企业 AI 应用开发面试。
> 核心定位：你不是“调过一次大模型 API”，而是“做过一套企业数据分析 Agent Runtime，并且理解 Agent 从 demo 到生产的工程取舍”。

---

## 0. 先给自己定调

你目前已经够应对 **业务开发 + Agent 项目经验型面试**。不要把自己包装成“AI Infra 专家”或“模型训练专家”，那不是你的主场。

你的主场是：

```text
Java 后端工程经验
  + Spring Boot 企业系统能力
  + Agent Runtime / 工具调用 / RAG / 记忆 / 可观测性
  + 酒店业务分析场景落地
```

最适合你的岗位画像：

- Java 后端 + AI 应用开发
- 企业内部知识库 / 数据分析 Agent
- RAG 应用工程师
- Agent 平台业务侧开发
- AI 中台 / 智能客服 / 数据问答方向

暂时不要主打：

- 大模型训练 / 微调算法岗
- LangGraph / MCP / OpenAI Agents SDK 源码专家
- AI Infra 高并发平台架构师
- 多模态模型研发

可以这样说：

> 我不是做模型训练的，我更偏 AI 应用工程落地。我的重点是把 LLM 接到企业数据、知识库、权限、工具和可观测体系里，让它从“能聊天”变成“能完成数据分析任务”的 Agent Runtime。

---

## 1. 面试总目标

面试官真正想确认三件事：

1. 你是不是真的理解 Agent，不是只会背“LLM + Tool”。
2. 你有没有亲手处理过工程问题，比如工具失败、模型不稳定、RAG 不准、流式、trace、权限。
3. 你能不能把 Agent 和业务场景结合，而不是只讲框架名。

你要传递的核心信号：

```text
我理解 Agent 的本质：
LLM 负责推理和决策，工具负责接触真实世界，RAG/Memory 负责补上下文，Runtime 负责循环、编排、限流、观测和安全。

我理解生产落地的关键：
不是让模型更自由，而是在工具、权限、成本、错误恢复、评估和可观测性上加边界。
```

一句话总纲：

> 我做的不是 ChatGPT 套壳，而是一套面向个人任务的 Agent Runtime。它有 ReAct 工具循环、受控子 Agent 委派、RAG 混合检索、分层记忆、模型网关、SSE 流式输出、执行轨迹和权限治理。我的最大收获是理解了 Agent 的自主性和工程可控性之间的取舍。

---

## 2. 你的差异化卖点

### 卖点 1：你讲得出 Agent 主链路

```text
用户请求
  -> AnalysisController
  -> DataAnalysisAgentImpl
  -> AgentRuntimeService 路由
  -> Orchestrator 或 ReAct
  -> RAG / Memory 构建上下文
  -> 模型决定是否调用工具
  -> AgentToolInvoker 执行工具
  -> 工具结果回填给模型
  -> SSE 返回过程和结果
  -> agent_execution_trace / 结构化日志落地
```

口语版：

> 请求进来后不是直接丢给模型，而是先做会话、文件、RAG、记忆这些上下文构建，再由 Runtime 决定走指定 Skill、指定 Agent、Orchestrator，还是 ReAct 兜底。ReAct 里面模型每轮可以决定是否调工具，工具结果再回填给模型继续推理，最后通过 SSE 返回，并把执行轨迹落库。

### 卖点 2：你有“工具调用不是 prompt 魔法”的认知

你能说：

> 工具调用的核心不是 prompt 里写“你可以调用工具”，而是把工具以 schema 的方式交给模型。模型根据工具名、description 和参数 schema 选择工具，后端再通过 ToolExecutor 真正执行。description 写得好不好，会直接影响模型选工具的准确率。

结合项目：

- `AgentTools`：用 `@Tool` 声明工具。
- `AgentToolInvoker`：从注解生成工具规格并执行。
- `queryHotelOccupancy`：Day14 从“查一个出租率”升级为“酒店经营诊断能力”。

### 卖点 3：你有“编排动作，不要编排推理”的新颖观点

这是你的杀手锏。可以背：

> 我后来意识到企业 Agent 不是简单地“越自主越好”或“越编排越好”。正确的分界线不是信不信模型，而是风险高不高。推理、检索、拆问题这些可以更多交给模型；但写库、改价、发邮件、转账这类高风险动作必须由权限、审批和审计来编排。我的总结是：编排动作，不要编排推理。

这句话很新，也贴近当前市场。

### 卖点 4：你知道从 demo 到生产差什么

你能说：

> Agent demo 主要看模型会不会调工具；生产系统更关注工具权限、失败恢复、token 成本、执行轨迹、RAG 质量、上下文污染、prompt injection 和用户反馈闭环。

这会显得你不是只会“玩模型”。

---

## 3. 当前市场补充：面试更爱问什么

下面这些是 2025-2026 年 Agent 面试更常见的新方向，你不需要全会实现，但要能讲出判断框架。

### 3.1 MCP：工具集成标准化

你要知道：

```text
MCP = Model Context Protocol
作用：把外部工具、数据库、文件、业务系统以统一协议暴露给 AI 应用。
类比：AI 应用的 USB-C。
```

面试说法：

> 以前每个模型、每个工具都要单独适配，容易变成 N 个模型乘 M 个工具的集成复杂度。MCP 的价值是把工具、资源和 prompt workflow 标准化，让 Agent 可以用统一方式连接数据库、文件系统、企业应用和工作流。

但一定补一句安全：

> MCP 不是自动安全的。它只是连接标准，真正生产落地还要做身份透传、权限校验、工具白名单、超时预算、审计和 prompt injection 防御。

你和项目的连接：

> 我项目里还不是标准 MCP Server，但架构思想接近：`AgentTools` 暴露工具，`AgentToolInvoker` 做工具注册和执行。如果后续升级，可以把内部工具包装成 MCP Server，让 ChatGPT、Claude Code 或内部 Agent 客户端统一接入。

### 3.2 Agent SDK / LangGraph：从循环到可恢复工作流

你要知道：

```text
OpenAI Agents SDK 关注：Agent、Tools、Handoffs、Guardrails、Sessions、Tracing。
LangGraph 关注：长任务、状态图、持久化、Human-in-the-loop、可恢复执行。
```

面试说法：

> 简单 Agent 可以用 while 循环实现 ReAct；但生产里的长任务，需要 checkpoint、暂停恢复、人审、状态可视化。LangGraph 这类框架本质上是把 while/if 显式化成 State + Node + Edge，让 Agent 执行过程更可控。

你和项目的连接：

> 我自己在项目里写过一个最小 `StateGraph` demo，理解了图式 Agent 的核心：共享 State、节点执行、条件边路由和危险动作前 interrupt。它不是为了生产造轮子，而是为了吃透 LangGraph 为什么适合长任务和高风险工具审批。

### 3.3 RAG 不再只问“向量库怎么用”

现在更常问：

- 纯向量检索为什么不够？
- hybrid retrieval 怎么做？
- rerank 解决什么问题？
- RAG 怎么评估？
- GraphRAG / Agentic RAG 什么时候有必要？

你的回答框架：

> 纯向量检索适合语义相似，但对订单号、专有名词、字段名、精确关键词不敏感。所以我更倾向 hybrid retrieval：全文检索负责精确命中，向量检索负责语义召回，再用 RRF 融合、rerank 重排、context compressor 压缩上下文。

新颖一点的补充：

> 我不会一上来就上 GraphRAG。GraphRAG 适合实体关系强、路径推理明显的知识库，比如组织架构、供应链、风控关系网。普通企业文档问答先把分块、混合检索、重排、引用和评估做好，收益更直接。

### 3.4 评估与可观测性是 Agent 落地分水岭

你能说：

> Agent 线上最难的是“它看起来答得很顺，但其实可能工具用错、引用错、或把检索结果误读了”。所以不能只看用户满意度，要记录每一步工具调用、检索结果、模型输入输出、token、耗时、错误类型，并建立离线评估集。

项目连接：

- `AgentExecutionTraceService`
- `StructuredLogger`
- `AgentFeedbackService`
- `AgentQualityService`
- `RagQualityEvaluationService`

市场新词：

```text
groundedness：答案是否有资料支撑
tool-call accuracy：工具选择和参数是否正确
trajectory evaluation：不是只评最终答案，还评中间执行轨迹
LLM-as-judge：用模型辅助评价答案，但高风险场景要抽样人工复核
```

### 3.5 Guardrails / Prompt Injection / Tool Security

你要能说：

> Agent 的攻击面比普通后端更大，因为用户输入、知识库内容、网页内容都可能变成 prompt 的一部分，里面可能夹带“忽略之前规则、导出数据库”等恶意指令。防御上不能只靠 prompt，要做工具最小权限、敏感动作审批、数据源只读、出站 URL 防护、租户隔离和审计。

项目连接：

- SQL 工具限制 SELECT。
- RBAC + tenantId。
- `OutboundUrlGuard` 防 SSRF。
- API Key 加密存储。
- 执行轨迹可审计。

---

## 4. 简历项目写法

### 4.1 简历项目标题

```text
企业数据分析 Agent 平台（Spring Boot + LangChain4j + RAG + ReAct）
```

### 4.2 简历项目描述

```text
基于 Spring Boot 3.2、LangChain4j、MySQL、Milvus、React 构建企业数据分析 Agent 平台，支持模型配置、知识库/RAG、文件解析、外部数据源、ReAct 工具调用、多 Agent 编排、分层记忆、SSE 流式输出、执行追踪和权限审计。用户可通过自然语言完成知识检索、文件分析、SQL 查询、图表生成和酒店经营指标诊断。
```

### 4.3 简历职责 bullet

可以选 5-7 条放简历：

- 设计统一 Agent Run 路由链路，支持 Chat、ReAct、受控子 Agent 委派以及命令和指定 Skill 执行。
- 基于 LangChain4j Function Calling 实现工具系统，通过 `@Tool` 注解生成工具 schema，支持知识库搜索、文件分析、SQL 查询、图表生成、计算和酒店经营指标诊断。
- 实现 ReAct 推理循环，支持“模型决策 -> 工具调用 -> 结果观察 -> 继续推理”，并通过最大迭代次数、错误恢复和 fallback 防止无限循环。
- 构建 RAG 混合检索管道，结合 Milvus 向量检索、全文检索、RRF 融合、重排、父上下文补全和上下文压缩，提高企业知识问答的可追溯性。
- 建设分层记忆能力，区分 working memory、short-term memory、long-term memory 和 user profile，减少长对话上下文膨胀。
- 设计模型网关，支持 OpenAI-compatible 模型供应商接入，统一处理模型配置、API Key 加密、token 记账、限流配额、重试和错误分类。
- 补齐 Agent 可观测性，记录 SSE thinking steps、工具调用次数、ReAct 迭代轮数、traceId、结构化日志和 `agent_execution_trace`，便于线上问题复盘。
- 结合酒店业务设计 `queryHotelOccupancy` 工具，将出租率、ADR、RevPAR、预订提前期、取消率等指标封装为 Agent 可调用的经营诊断能力。

### 4.4 简历里的高阶关键词

不要堆太多，挑你能讲的：

```text
ReAct
Function Calling
Tool Schema
Hybrid RAG
RRF Fusion
Rerank
Memory Layering
SSE Streaming
Agent Trace
Guardrails
Human-in-the-loop
MCP-ready
Tool-call Recovery
```

---

## 5. 30 秒 / 1 分钟 / 3 分钟项目介绍

### 5.1 30 秒版

> 我做的是一个企业数据分析 Agent 平台，不是简单套壳。用户可以上传文件、维护知识库、配置模型和数据源，然后用自然语言做分析。后端通过 Agent Runtime 路由到 Orchestrator 或 ReAct，模型可以调用知识库、文件、SQL、计算、图表等工具。系统还做了 RAG、分层记忆、SSE 流式输出、模型重试、token 记账、权限控制和执行追踪。

### 5.2 1 分钟版

> 这个项目是一个可配置的个人 Agent Harness，后端是 Spring Boot + LangChain4j，前端是 React，MySQL 存业务数据，Milvus 做向量库。核心不是单次调用模型，而是一套统一 Agent Run：请求规划器按需选择 Chat、ReAct 或 Orchestrated，并决定是否加载 RAG、记忆和候选工具。Orchestrated 通过受治理的 `delegateToAgent` 委派用户显式绑定的子 Agent，父子共享预算和取消信号。为了生产可用，我还做了模型错误分类重试、Token 配额、SQL 只读、租户隔离、SSE 流式输出和执行轨迹。

### 5.3 3 分钟版

> 我这个项目叫 Data Agent，是一个企业数据分析 Agent 平台。它解决的问题是：企业内部有知识库、上传文件、数据源和业务指标，用户希望用自然语言直接做分析，而不是自己去找资料、写 SQL 或拼图表。
>
> 架构上我分了几层。入口层是 React 控制台和 Spring MVC API；安全层是 JWT、RBAC、管理员初始化和租户隔离；Agent Runtime 层负责路由，请求进来后由 `AgentRuntimeService` 判断走命令、Skill、自定义 Agent、Orchestrator，还是 ReAct 兜底；能力层包括模型网关、RAG、记忆、工具、文件解析、数据源查询和图表生成；最后是可观测层，记录执行轨迹、结构化日志、反馈和审计。
>
> Agent 的核心是 ReAct。模型不是直接回答，而是在最多几轮内循环执行“思考、行动、观察”。如果它需要知识，就调用 `searchKnowledge`；需要数据，就调用 `executeSql` 或数据源预览；需要计算，就调用 `calculate`；需要酒店业务指标，就调用我扩展的 `queryHotelOccupancy`。这些工具通过 LangChain4j 的 `@Tool` 注解生成 schema，模型根据 description 选择工具，后端执行后把结果再喂回模型。
>
> 为了减少幻觉，我做了 RAG 混合检索：向量召回负责语义相似，全文检索负责关键词精确匹配，再用 RRF 融合、重排、补父上下文、压缩后注入 prompt。为了支持连续任务，我做了记忆分层，区分当前工作记忆、会话短期记忆、长期记忆和用户画像。为了生产可控，我做了模型重试、token 记账、SQL 只读、租户隔离、SSE 流式输出和 `agent_execution_trace`。
>
> 我最大的收获是：Agent 不是“模型越自主越好”，而是要把自主性放在推理和检索上，把权限、危险动作、成本和审计放在工程边界里。我的总结是：编排动作，不要编排推理。

---

## 6. 核心知识点答法

### Q1：Agent 和普通 ChatBot 有什么区别？

标准答：

> 普通 ChatBot 通常是一次输入、一次模型输出；Agent 是一个执行系统。它会围绕目标进行多轮推理，可以使用工具、查询外部数据、读取知识库、维护记忆，并根据工具结果继续调整下一步动作。

结合项目：

> 在我项目里，普通问答会直接经过模型回答；但 Agent 路径会先构建 RAG 和记忆上下文，再进入 ReAct 或 Orchestrator。ReAct 里模型可以决定调用知识库、SQL、文件、计算器、图表等工具，工具结果回填后再生成答案。

新颖补充：

> 我理解 Agent 的关键不是“有工具”，而是“模型拥有一定决策权”。但生产里不能无限放权，所以需要工具权限、执行轨迹和高风险动作审批。

### Q2：ReAct 是什么？怎么实现？

答：

> ReAct 是 Reasoning + Acting，也就是模型先推理，再决定是否行动。工程上就是一个循环：每轮把系统提示、上下文和工具 schema 发给模型；模型如果返回 tool call，就执行工具并把 Observation 加回上下文；如果返回最终答案，就结束。为了防止死循环，会设置最大轮数。

项目细节：

- `ReActAgent`：组织执行入口。
- `ReActLoopRunner`：循环控制。
- `ReActStepHandler`：处理单步结果。
- `AgentToolInvoker`：执行工具。

一句加分：

> ReAct 的难点不是 while 循环本身，而是循环边界、工具失败恢复、上下文截断、流式输出和 trace 怎么做。

### Q3：模型怎么知道有哪些工具？怎么选工具？

答：

> 后端把工具以 schema 形式传给模型，包括 name、description、parameters。模型不会真的执行工具，它只返回一个结构化的 tool call。后端再根据工具名找到执行器执行。模型选择工具主要看 description，所以 description 要写清楚“什么时候用”，不只是“这个工具是什么”。

结合 Day14：

> 比如我把 `queryHotelOccupancy` 从“查询出租率”扩展成“查询并分析酒店经营表现”，description 里加了出租率、ADR、RevPAR、预订趋势、是否调价这些关键词，让模型在用户问经营诊断时更容易选中它。

### Q4：ReAct 和 Orchestrator 有什么区别？

答：

> ReAct 更像一个模型自己循环思考和调工具，适合开放式、探索式、需要临场决策的问题。Orchestrator 更像指挥官，先判断意图和复杂度，再把任务派给不同专家，适合结构更明确、可拆分的复杂任务。

结合项目：

> 我项目里先由请求规划器固化 Chat、ReAct 或 Orchestrated 模式。Orchestrated 不会扫描全部专家，而是只把当前 Profile 显式绑定的子 Agent 作为受治理能力暴露；没有委派必要时就保持 Chat 或 ReAct，避免额外规划成本和权限扩大。

取舍：

> 企业场景往往需要可控和可审计，所以 Orchestrator 有价值；但过度编排会削弱模型的自主推理能力。所以我更倾向“模型负责推理，编排负责危险动作和流程边界”。

### Q5：RAG 怎么做？为什么不用纯向量检索？

答：

> 纯向量检索适合语义相似，但对精确关键词、订单号、字段名、专有名词不稳定。所以我项目里采用混合检索：向量检索负责语义召回，全文检索负责精确匹配，然后用 RRF 做融合，再重排、补父上下文、压缩上下文，最后带引用注入 prompt。

链路：

```text
query rewrite
  -> vector retrieval + full-text retrieval
  -> RRF fusion
  -> rerank
  -> parent context
  -> compression
  -> citation
  -> prompt context
```

加分：

> RAG 的目标不是“塞更多资料”，而是把正确、足够、可引用的上下文塞给模型。资料太多会污染上下文，反而降低答案质量。

### Q6：Memory 和 RAG 有什么区别？

答：

> RAG 主要是企业知识、文件、文档和业务资料；Memory 主要是用户、会话和长期偏好。它们最后都可能进入 prompt，但来源、生命周期和权限边界不同。

项目答法：

> 我项目里 Memory 分 working、short-term、long-term、user profile。当前任务临时信息放 working memory，会话近期信息放 short-term，跨会话沉淀放 long-term，用户偏好放 user profile。这样避免把所有历史对话都塞进上下文。

### Q7：模型调用失败怎么办？

答：

> 不能无脑重试。401、403、404 通常是鉴权、权限或路径错误，重试没有意义；429 和 5xx 才适合退避重试。除此之外，还要有 token 配额、熔断、错误分类和用户可理解的失败提示。

项目连接：

- `ModelRetryExecutor`
- `McpModelService`
- `ModelHttpClient`
- `TokenQuotaGuard`
- `TokenUsageRecorder`

加分：

> Agent 系统里模型失败不只是“回答失败”，还可能发生在中间工具决策轮，所以 trace 里要记录是哪一轮、哪个模型、耗时、token 和错误类型。

### Q8：SSE 流式输出怎么做？为什么不用 WebSocket？

答：

> Agent 的输出是服务端持续推给前端，用户主要是接收 thinking、tool_call、token、done、error 这些事件，SSE 单向推送就够了，协议简单、浏览器原生支持。WebSocket 更适合双向实时协作。

加分：

> Agent 流式和普通 ChatBot 流式不一样。普通 ChatBot 只推 token；Agent 要推结构化事件，比如正在思考、正在查知识库、工具调用结果、最终答案和 traceId。

### Q9：怎么做 Agent 可观测性？

答：

> 不能只记录最终答案，要记录执行轨迹。至少包括：用户问题、选中的 Agent、模型调用、token、耗时、每轮 ReAct、工具名、工具参数、工具结果摘要、RAG 引用、错误信息、traceId。

项目连接：

> 我补过 ReAct 路径 trace，把流式路径的 done 事件带上 traceId，并把 iterations、toolCallCount、thinkingSteps 写到 `agent_execution_trace` 的 plan_json 中，同时打结构化日志，方便用 sessionId 串起日志和数据库轨迹。

### Q10：怎么防止 Agent 乱查数据库或执行危险动作？

答：

> 不能把数据库直接交给模型。模型只能通过受控工具访问数据源。工具层要做 SQL 只读、权限校验、租户过滤、超时控制、敏感字段脱敏和审计。高风险动作要 human-in-the-loop。

结合项目：

- `executeSql` 只允许 SELECT。
- 数据源管理需要权限。
- tenantId 隔离。
- trace 审计工具调用。

新颖补充：

> 未来如果做 MCP Server，也不能因为 MCP 标准化就默认安全，仍然要做身份透传和工具级授权。

---

## 7. 你的三个高分技术故事

### 故事 1：工具调用从 demo 到业务能力

结构：

```text
背景：Day5 做了 queryHotelOccupancy mock 工具。
问题：只返回出租率，业务价值很薄。
行动：Day14 扩展成酒店经营诊断能力，加入 ADR、RevPAR、取消率、预订提前期、环比和建议动作。
结果：工具从“查一个数”变成“支撑 Agent 业务判断”的能力。
认知：Agent 的价值不是会调工具，而是能把工具结果转成业务可用判断。
```

面试说法：

> 我一开始给 Agent 加了一个酒店出租率工具，后来发现这只是工具调用 demo。真实业务不会只问“出租率是多少”，而是问“经营是否健康，要不要调价”。所以我把工具输出升级为一组 KPI：出租率、ADR、RevPAR、取消率和预订提前期，并把 KPI 定义和诊断逻辑一起返回。这样模型拿到的不是孤立数字，而是可以解释和行动的业务上下文。

### 故事 2：ReAct 流式路径没有 trace

结构：

```text
背景：Day13 查可观测性。
现象：普通聊天走 SSE，done 事件 traceId 为空，trace 表查不到。
原因：traceService.record 只在 Orchestrator 同步路径调用，ReAct 流式路径绕过了它。
行动：为 ReAct 增加 recordReAct，记录 iterations、toolCallCount、thinkingSteps，并在 done 事件回传 traceId。
结果：默认 ReAct 路径也可观测。
认知：Agent 生产化不能只看最终答案，要看执行轨迹。
```

面试说法：

> 我排查过一个很典型的 Agent 可观测性问题：前端默认走流式 ReAct，但 done 事件里 traceId 是空的，事后查不到执行轨迹。最后发现 trace 只记录了 Orchestrator 同步路径，ReAct 流式路径漏掉了。我补了 ReAct 专属 trace，把迭代轮数、工具调用次数和 thinking steps 写入 trace 表，并在 SSE done 事件回传 traceId。这个经历让我意识到，Agent 的可观测性必须覆盖默认路径，否则线上答错时根本没法复盘。

### 故事 3：编排优先到带刹车的自主

结构：

```text
背景：系统有 Orchestrator、分类器、规划器、Fast Path。
问题：过度确定性编排会提前替模型做决定，削弱 Agent 自主性。
思考：企业需要可控，但不该把推理也编排死。
结论：推理交给模型，动作交给权限/审批/审计。
金句：编排动作，不要编排推理。
```

面试说法：

> 我对 Agent 架构最大的反思是：不要把所有事情都交给确定性编排。分类器、规划器、Fast Path 单独看都有道理，但叠太多层后，模型还没看到工具和上下文，代码已经替它决定了很多事。我的结论是：推理、检索、拆解任务可以更信任模型；但写库、改价、发消息这种高风险动作必须编排和审批。也就是“带刹车的自主”。

---

## 8. 当前市场加分点怎么自然带出来

### 8.1 MCP 怎么带

被问“后续怎么优化工具系统”时说：

> 现在工具是项目内 `@Tool` 方式注册，后续可以演进成 MCP Server，把知识库、SQL 数据源、酒店经营指标、文件分析这些能力标准化暴露。这样不只本项目的 Agent 能用，其他支持 MCP 的客户端也能复用。但生产里要补 identity propagation、工具级权限和审计，否则 MCP 只是连得上，不代表用得安全。

### 8.2 LangGraph 怎么带

被问“复杂长任务怎么做”时说：

> 现在 ReAct 是循环式，复杂长任务可以升级成图式执行。每个节点代表模型推理、检索、工具、审批或总结，状态统一放在 State 里，关键节点 checkpoint，高风险动作前 interrupt 等人工确认。这类模式适合长任务、可恢复执行和 human-in-the-loop。

### 8.3 Evaluation 怎么带

被问“怎么证明 Agent 好用”时说：

> 我会分两层评估：最终答案评估和轨迹评估。最终答案看相关性、完整性、是否有引用、是否 grounded；轨迹评估看工具选得对不对、参数对不对、是否重复调用、是否误读工具结果。只看最终答案会漏掉很多 Agent 特有错误。

### 8.4 Guardrails 怎么带

被问“Prompt 注入怎么防”时说：

> Prompt 注入不能只靠 prompt 防。检索出来的文档、网页、用户上传文件都要当作数据，不当作指令；工具要最小权限；高风险动作要二次确认；敏感数据要脱敏；最终所有工具调用要有 trace 和审计。

---

## 9. 一周面试准备计划

### Day 1：项目主线背熟

目标：不卡顿讲完 3 分钟项目介绍。

练习：

- 背 30 秒、1 分钟、3 分钟版本。
- 画出主链路图。
- 能说出 10 个核心类的职责。

验收：

> 不看文档，说出“请求从前端到最终答案”的完整链路。

### Day 2：ReAct + Tool 深挖

目标：把 Agent 内核讲清楚。

准备问题：

- ReAct 是什么？
- Function Calling 怎么工作？
- 工具怎么定义？
- 模型怎么选工具？
- 工具调用失败怎么办？
- 怎么避免无限循环？

验收：

> 用 `queryHotelOccupancy` 举例讲清楚 tool schema、description、参数、执行和观察结果回填。

### Day 3：RAG + Memory

目标：讲清楚“让模型基于资料回答”的能力。

准备问题：

- RAG 怎么做？
- 为什么 hybrid retrieval？
- RRF / rerank / context compression 是什么？
- Memory 和 RAG 区别？
- 上下文窗口满了怎么办？

验收：

> 能用酒店业务例子说明“知识库资料”和“用户记忆”分别解决什么问题。

### Day 4：生产化

目标：讲出你不是 demo 选手。

准备问题：

- 模型失败怎么处理？
- token 成本怎么控制？
- SSE 为什么用？
- trace 怎么查问题？
- Agent 答错怎么复盘？

验收：

> 能完整讲 Day13 ReAct trace 缺口和修复思路。

### Day 5：安全 + 权限 + MCP

目标：补当前市场最关心的“Agent 接真实系统怎么管”。

准备问题：

- SQL 工具怎么防危险操作？
- 多租户怎么隔离？
- prompt injection 怎么防？
- MCP 是什么？有什么风险？
- high-risk action 怎么 human-in-the-loop？

验收：

> 背熟“编排动作，不要编排推理”。

### Day 6：业务场景

目标：把 Agent 讲到酒店业务里。

准备问题：

- 酒店 Agent 可以解决什么问题？
- 出租率、ADR、RevPAR 怎么解释？
- 为什么只看出租率会误判？
- 从 demo 到生产还差哪些数据？

验收：

> 用 `杭州上周经营怎么样，要不要调价` 讲出指标拆解、工具设计、业务诊断。

### Day 7：模拟面试

目标：练追问，不再只背答案。

流程：

1. 3 分钟项目介绍。
2. 面试官从 ReAct 深挖到工具。
3. 从工具深挖到安全。
4. 从 RAG 深挖到评估。
5. 从架构深挖到取舍。

验收：

> 每个问题都能从“是什么”讲到“为什么这么设计”和“有什么不足”。

---

## 10. 高频问答速记

| 问题 | 你的短答 |
|---|---|
| Agent 是什么？ | LLM + 工具 + 记忆 + 循环 + 工程边界。 |
| Agent 和 ChatBot 区别？ | ChatBot 生成回答，Agent 能多步决策并调用工具完成任务。 |
| 模型凭什么调工具？ | 后端传入 tool schema，模型根据 name/description/parameters 生成 tool call。 |
| ReAct 什么时候停？ | 模型不再发 tool call 或达到最大迭代次数。 |
| 工具调用失败怎么办？ | 返回结构化错误，模型可自我修正；同时有重试上限和 trace。 |
| RAG 解决什么？ | 给模型真实企业资料，减少幻觉，提高可追溯性。 |
| 纯向量检索有什么问题？ | 对精确关键词、编号、字段名不敏感，所以要 hybrid retrieval。 |
| Memory 是什么？ | LLM 外部的持久上下文系统，不是模型自己记住。 |
| Orchestrated 作用？ | 在同一 Run 内把明确子任务委派给显式绑定的子 Agent，并继续受权限、预算和 Trace 治理。 |
| SSE 为什么适合？ | Agent 主要服务端单向推送 thinking/tool/token/done 事件。 |
| 模型失败怎么处理？ | 401/403/404 不重试，429/5xx 重试，配额和熔断兜底。 |
| 怎么防 SQL 危险？ | 模型不能直连库，只能调用受控 SELECT 工具。 |
| MCP 是什么？ | 连接 AI 应用和外部工具/数据的开放标准。 |
| 生产 Agent 最难？ | 评估、观测、安全、权限、成本和错误恢复。 |

---

## 11. 不要过度包装的边界

面试里最怕吹过头。下面这些要诚实：

- 不要说你做了模型训练，你做的是模型应用和 Agent Runtime。
- 不要说 MCP 已经生产落地，你可以说“当前项目是 MCP-ready 的工具抽象，后续可包装为 MCP Server”。
- 不要说 RAG 准确率已经量化很高，除非你有评测数据。可以说“具备质量评估模块，下一步会建设离线评估集”。
- 不要说完全防 prompt injection，可以说“做了最小权限、SQL 只读、租户隔离和审计，prompt injection 还需要进一步加文档隔离和动作审批”。
- 不要说 LangGraph 很熟源码，可以说“理解图式 Agent 的 State/Node/Edge/Interrupt 模式，并写过最小 demo”。

这反而更可信。

---

## 12. 面试官追问时的“升级句”

这些句子很值钱，可以自然插入：

- Agent 的核心不是“有工具”，而是模型有一定决策权。
- 工具 description 是模型选工具的路由提示，也是工具契约的一部分。
- RAG 不是召回越多越好，而是上下文要正确、足够、可引用。
- Memory 和 RAG 都是外部上下文，但生命周期和权限边界不同。
- ReAct 的难点不是循环，而是边界、失败恢复、上下文和观测。
- 生产 Agent 要评估 trajectory，不只评估 final answer。
- MCP 解决连接标准化，不解决权限和安全。
- 企业 Agent 的甜点区是带刹车的自主。
- 编排动作，不要编排推理。
- 从 demo 到生产，差的是评估、观测、安全、成本和治理。

---

## 13. 酒店业务 Agent 场景稿

### 13.1 业务问题

用户不会说“调用工具”。用户会问：

```text
杭州上周酒店经营怎么样？要不要调价？
```

这个问题要拆成：

- 出租率高不高？
- ADR 是涨还是跌？
- RevPAR 是否提升？
- 取消率是否异常？
- 预订提前期是否健康？
- 如果要动作，是提价、促销还是观察？

### 13.2 KPI 口径

```text
出租率 = 已售间夜 / 可售间夜
ADR = 房费收入 / 已售间夜
RevPAR = ADR * 出租率
```

面试要点：

> 只看出租率会误判。出租率高可能是低价换量，ADR 高也可能压制需求。RevPAR 把价格和入住结合起来，更适合看综合收益。

### 13.3 Agent 能力设计

工具：

```text
queryHotelOccupancy(city, date)
```

返回：

- 出租率
- ADR
- RevPAR
- 环比变化
- 取消率
- 预订提前期
- KPI 定义
- 诊断结论
- 建议动作

讲法：

> Day5 我只是做了一个查出租率的 mock 工具；Day14 我把它升级成酒店经营诊断工具。这个变化代表我对 Agent 的理解升级了：工具不是为了返回一个数，而是要给模型提供足够业务化、可解释的上下文，让模型能生成业务判断。

---

## 14. 最后一页：面试前背这段

> 我对 Agent 的理解是：它不是一个更会聊天的模型，而是一个由模型驱动的执行系统。模型负责理解任务、规划下一步、决定是否调用工具；工具负责连接真实世界的数据、知识库、文件、数据库和业务系统；RAG 和 Memory 负责给模型补充外部上下文；Runtime 负责循环、编排、错误恢复、权限、成本和可观测性。
>
> 我在 Data Agent 项目里从 ReAct、工具调用、RAG、记忆、多 Agent 编排、模型重试、SSE、执行轨迹到酒店业务能力都做过一遍。我的最大收获是：Agent 的生产落地不是让模型无限自主，而是在推理上给模型空间，在动作上加权限、审批和审计。也就是带刹车的自主。

---

## 15. 参考补充

这些不是背诵材料，是用来让你的回答贴近当前市场：

- OpenAI Agents SDK：关注 Agents、Tools、Handoffs、Guardrails、Sessions、Tracing、MCP 和 Human-in-the-loop。
- Model Context Protocol：把工具、数据源和工作流标准化暴露给 AI 应用，适合解释“未来工具系统怎么标准化”。
- LangGraph：把长任务 Agent 做成 stateful graph，强调 persistence、human-in-the-loop、streaming、memory 和 observability。
- 当前 RAG 趋势：从单纯向量检索走向 hybrid retrieval、rerank、GraphRAG、Agentic RAG 和系统化 evaluation。
- 当前安全趋势：prompt injection、tool permission、MCP 安全、数据泄露、工具审计会越来越常被问。
