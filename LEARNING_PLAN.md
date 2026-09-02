# 15 天学会 Agent · 每天 1 小时

> 对象：同程酒店业务开发，已读完 [AGENT_KNOWLEDGE.md](AGENT_KNOWLEDGE.md) 理论手册，现在用这个真实项目把 Agent 吃透。
> 预算：15 天 × 1 小时 = 15 小时。
> 目标（务实校准）：**吃透 Agent 核心原理 + 能看懂并改动本项目的 Agent 代码 + 能自己设计一个简单 Agent 能力**。不是"精通分布式多 Agent 框架"——那是后话。
> 铁律：**做中学**。理论已经读过了，接下来每天都要动手——跑起来、改一行、看变化。光读不动手，等于没学。

---

## 每天 1 小时怎么花

```
25 分钟  读（带着当天的问题读代码，别通读）
25 分钟  动手（跑 / 改一行 / 观察日志变化）
10 分钟  记一句话心得 + 写下一个没搞懂的问题
```

> 「没搞懂的问题」攒着，第 7 天、第 15 天复盘时回头啃。

---

## 前置（Day 1 必须先做，否则全盘跑不起来）

Agent 的灵魂是 **LLM 推理循环**。模型调不通，你看到的永远是「模型调用失败」——等于发动机没点着。
**第一件事：在前端模型管理页，把一个 key 有效的模型（如你已加的 deepseek）设为默认模型。** 然后发一个问题，确认能拿到真实回答，再开始。

（Milvus 向量库报错可以完全无视——它只是"语义记忆"的存储，挂了不影响 Agent 核心循环。）

---

## 第一周：Agent 的核心（跑通 → ReAct → 工具 → Prompt）

### Day 1 — 点火：让 Agent 活起来 + 看清全貌
- **读**：[AgentRuntimeService.java](data-agent/src/main/java/com/ai/agent/AgentRuntimeService.java) 的 `execute()`——5 条路由分支（命令 / 指定Agent / 指定Skill / Orchestrator / ReAct 兜底）。
- **动手**：配好默认模型，发一个问题，看 SSE 返回的 thinking + result；看后端日志里的 `selectedAgent / selectedType`。
- **产出**：一句话画出「一个普通提问怎么被路由到哪个 Agent」。

### Day 2 — ReAct：Agent 的心跳
- **读**：[ReActAgent.java](data-agent/src/main/java/com/ai/agent/react/ReActAgent.java) 的 `SYSTEM_PROMPT`(约第 42 行)+ 主循环；配合 `ReActLoopRunner`、`ReActStepHandler`。
- **重点**：思考 → 行动(调工具) → 观察(工具结果) → 再思考 → 直到给出最终答案。模型怎么知道何时停？
- **产出**：手画 ReAct 循环流程图。

### Day 3 — 看 ReAct 真的在「想」（动手）
- **动手**：在 ReAct 循环里加一行日志，打印**每轮模型返回的原始文本**；发一个需要查数据/知识的问题，观察它怎么一步步决定调工具。
- **再动手**：把 temperature 在 0 和 0.7 之间切，同一问题各问 3 次，对比稳定性。
- **产出**：一次真实 ReAct 轨迹的逐轮注释（这轮在想什么、决定干什么）。

### Day 4 — 工具调用机制
- **读**：[AgentTools.java](data-agent/src/main/java/com/ai/agent/tool/AgentTools.java)(`@Tool` 注解定义工具)+ [AgentToolInvoker.java](data-agent/src/main/java/com/ai/agent/tool/AgentToolInvoker.java)(`ToolSpecifications` 生成 Schema → `DefaultToolExecutor` 执行)。
- **重点**：模型怎么知道有哪些工具？凭什么决定调哪个？——**靠工具的 `description`**。参数怎么从模型来、结果怎么回填给模型？
- **产出**：画「一次工具调用的数据流」(模型→工具请求→执行→结果回填→模型继续)。

### Day 5 — 给 Agent 加一个工具（最有成就感的一天）
- **动手**：参照 [AgentDataSourceToolService.java](data-agent/src/main/java/com/ai/agent/tool/AgentDataSourceToolService.java)，加一个 `query_hotel_occupancy` 工具（输入城市+日期，先返回 mock 出租率数据）；写好 `description`，注册进工具列表。
- **测试**：问 Agent「杭州上周的出租率」，看它会不会自己调用你的新工具。
- **产出**：能跑的新工具 + 模型成功调用它的日志。

### Day 6 — Prompt 工程（搞破坏式学习）
- **动手**：对 `SYSTEM_PROMPT` 做 4 个实验，每个记录「改了什么→预期→实际→为什么」：
  1. temperature 0 vs 0.7 的输出差异；
  2. 删掉 prompt 里工具格式说明，看还能不能正确调工具；
  3. 末尾加「必须用中文回答」看效果；
  4. maxTokens 设 100，问复杂问题，看截断。
- **产出**：4 个实验对比记录。

### Day 7 — 第一周复盘
- 不看代码，重画 ReAct + 工具调用全流程；写 300 字：**Agent 的「自主性」到底来自哪里？**(答案藏在「模型决策 + 工具 + 循环」里)

---

## 第二周：编排 → 记忆 → RAG → 工程 → 实战

### Day 8 — Orchestrator：从单兵到团队
- **读**：[OrchestratorAgent.java](data-agent/src/main/java/com/ai/agent/orchestrator/OrchestratorAgent.java) 的 `executeStructured()`——意图识别 → 任务规划 → 专家派发 → 结果汇总。
- **重点**：ReAct(一个模型自己循环) vs Orchestrator(指挥官派给多专家)，什么场景用哪个？为什么默认关 LLM 意图识别/规划？
- **产出**：Orchestrator 流程图 + 与 ReAct 的对比笔记。

### Day 9 — 专家协作
- **读**：`agent/specialist/` 下的专家（`DataAgentSpecialist`、`KnowledgeExpertSpecialist`、`ChartExpertSpecialist`…）+ `AgentSpecialistRegistry`。
- **动手**：发一个稍复杂的问题，看日志里 Orchestrator 怎么派发、结果怎么合并。
- **产出**：专家职责表 + 一次多专家请求的派发观察。

### Day 10 — 记忆：Agent 怎么「记住」
- **读**：`com.ai.memory` 下的 `MemoryManager` + `memory_entry`/`user_profile` 表。
- **重点**：LLM 本身无记忆——记忆是外挂。分层：working(当前上下文) / short-term(近期) / long-term(持久) / user_profile(画像)。
- **产出**：记忆层级图 + 酒店场景需求(「我上次住的杭州那家叫啥」需要哪层记忆？)。

### Day 11 — RAG：Agent 怎么「查资料」防幻觉
- **读**：`com.ai.rag` 的 `EnhancedRagPipeline`——检索(向量+全文) → 融合 → 重排 → 压缩 → 塞进 prompt。
- **重点**：为什么 RAG 是防幻觉的核心武器？检索到垃圾内容会怎样？
- **产出**：RAG 管道流程图。

### Day 12 — 发动机与保险丝：模型调用 + 可靠性
- **读**：`McpModelService`→`ModelHttpClient` 调用链；[ModelRetryExecutor.java](data-agent/src/main/java/com/ai/mcp/ModelRetryExecutor.java)。
- **重点**：哪些错误重试(429/5xx)、哪些不重试(401/403/404)、为什么；模型持续失败时用户看到什么。
- **产出**：重试规则笔记 + 「模型挂了系统怎么兜底」。

### Day 13 — 看见 Agent：可观测性
- **读/查**：SSE 的 thinking steps([AnalysisStreamService.java](data-agent/src/main/java/com/ai/service/AnalysisStreamService.java))、结构化日志、`agent_execution_trace` 表。
- **动手**：发一个请求，去 `agent_execution_trace` 表查它这次的完整轨迹。
- **产出**：一次请求的「可观测性地图」(从入口到返回，每步有什么 log/trace)。

#### 📝 Day 13 学习记录（2026-06-28）

**今日主题**：可观测性三件套——「实时 → 近实时 → 持久化」三层。

| 层 | 给谁看 | 技术 | 关键文件 | 特点 |
|---|---|---|---|---|
| ① 实时流 | 用户 | SSE 事件流 | `ReActStreamEventWriter` → `AnalysisStreamService` | 边算边推，10 种事件 type |
| ② 结构化日志 | 开发/运维 | JSON 日志 + MDC | `StructuredLogger` | 带 requestId/userId/tenantId，可检索 |
| ③ 执行轨迹表 | 事后排查 | 落库 + REST | `AgentExecutionTraceService` + `agent_execution_trace` 表 | 永久保存，可回溯 |

**关键概念 1：SseEmitter 是什么**
Spring 对 SSE（Server-Sent Events 单向推送）的封装。Controller `return emitter` 后 HTTP 线程立刻释放，真正干活（调模型、跑 ReAct）在 `sseExecutor` 线程池异步跑。每次 ReAct 产生事件就 `emitter.send(...)` 实时推给浏览器——这就是「打字机」效果的来源。断线时 `onTimeout/onError` 会 `cancel(true)` 中断后台线程，避免往死管道写数据。

**关键概念 2：SSE 的 10 种事件 type**
`rag_context / thinking_start / token / tool_call / reflection / execution_plan / parallel_precheck / orchestration / done / error`——每种对应 ReAct 循环的一个动作，前端靠事件 name 区分「开始」(`start`) 和「内容流」(`stream`)。

#### 🔥 重大发现：ReAct 路径没有 trace（设计缺口）

**现象**：聊天框普通提问，DevTools 里 SSE `done` 事件的 `traceId` 是空的，`agent_execution_trace` 表查不到。

**根因定位**（读代码 `AgentRuntimeService.executeStreaming:122-129`）：
- 前端默认走**流式接口** `/analyze/stream` → 进 ReAct 真流式分支
- `traceService.record()` 全项目**只有 1 处调用**（`AgentRuntimeService:98`，仅 Orchestrator 同步路径）
- 流式路径完全绕过 Orchestrator，所以 `record()` 从头到尾没被调用

**判断**：不是 bug，是设计取舍——trace 表的字段（`intent/complexity/plan_json/task_results_json`）都是「多专家编排」概念，ReAct 是单模型循环塞不进去。但留下「默认路径无 trace」的真实缺口。

#### 🛠 今日动手：补齐 ReAct 路径可观测性（已写代码）

按「复用现有表 + done 事件回传 traceId + 结构化日志关联」方案，改了 5 个文件：

1. **`ReActLoopRunner.runStreaming`**：返回类型 `String` → `ReActExecutionResult`（补回被丢失的「迭代轮数」+ 新增「工具调用次数」累计）。这是侵入性最大的一处，但补了既有缺陷。
2. **`ReActExecutionResult`**：2 字段 → 4 字段（加 `toolCallCount/success`），保留 2 字段向后兼容构造。
3. **`AgentExecutionTraceService.recordReAct`**：新增方法，ReAct 专属信息（iterations/toolCallCount/thinkingSteps）塞进 `plan_json`，多专家专属字段（`task_results_json=[]`、`shared_context_json={}`）留空。`@Transactional(REQUIRES_NEW)` 即使主流程回滚 trace 也落库。
4. **`ReActAgent`**：注入 `AgentExecutionTraceService`（`@Nullable`，缺失降级），5 个落 trace 点（fast-path/主循环/autonomous × 同步+流式）都落 trace 并把 traceId 回传到 `emitDone`。`recordReActTrace` 辅助方法统一处理 traceService 为空降级 + 写回 `response.setTraceId`。
5. **结构化日志关联**：`recordReAct` 内部 `structuredLogger.logEvent(AGENT_TRACE, {traceId,sessionId,iterations,...})` 打汇总日志，靠 sessionId 把「trace 表 ↔ 日志」串起来。

**测试**：适配 `ReActLoopRunnerTest`（runStreaming 断言）、`ReActAgentFastPathTest`（构造函数新参 + 返回类型），新增 `recordReAct` 落库测试（验证 iterations/toolCallCount 进 plan_json、多专家字段留空）。

#### 🗺 可观测性地图（一次 ReAct 流式请求）

```
用户提问
  ├─ SSE: start 事件
  ├─ 日志: AGENT_STEP("路由决策")
  │
  ├─ ReAct 循环第 1 轮
  │    ├─ SSE: thinking_start(iteration=1)
  │    ├─ 日志: LLM_CALL(模型返回)
  │    ├─ 模型决定调工具 → SSE: tool_call
  │    ├─ 日志: TOOL_CALL(params, result, 耗时)
  │    └─ （继续下一轮...）
  │
  ├─ 最终答案 → SSE: token（逐字推送）
  ├─ SSE: done(traceId=xxx)  ← 今天补的！之前是空的
  │
  └─ 落库: agent_execution_trace(selected_type=REACT, plan_json 含 iterations)
        └─ 日志: AGENT_TRACE(traceId, sessionId, iterations, toolCallCount)
```

#### ❓ 留给 Day 15 的问题
- `StructuredLogger` 的 MDC（requestId）在 SSE 线程池多线程间怎么传递？会不会丢？（引出 `ContextPropagatingTaskDecorator`）


### Day 14 — 综合实战：设计一个酒店分析 Agent 能力
- **动手**：列 5 个酒店真实分析问题(出租率、RevPAR、预订趋势…)；为其中一个设计：需要什么工具 + 什么 prompt + 什么领域知识(KPI 定义)；把 Day 5 的工具扩展成能用的小能力。
- **产出**：酒店 Agent 能力设计 + 一个能跑的小能力。

#### 📝 Day 14 学习记录（2026-06-29）

**今日主题**：把 Day 5 的“会调工具”升级成一个最小可用的酒店经营分析能力。

**5 个真实酒店分析问题**

1. 杭州上周出租率怎么样？和上一周期比是变好还是变差？
2. 某城市 RevPAR 下滑，是 ADR 下降导致，还是出租率下降导致？
3. 未来 7 天预订趋势是否健康？是否需要提前促销？
4. 某商圈取消率升高，会不会影响最终入住和收益？
5. 出租率高但房价低时，是否应该提价？提价幅度怎么试？

**本次选中的小能力：酒店经营诊断**

- **用户问法**：`分析杭州上周酒店经营表现，看看要不要调价`
- **工具**：复用并升级 `queryHotelOccupancy(city, date)`，让它不只返回出租率，而是返回出租率、ADR、RevPAR、预订提前期、取消率、环比变化、诊断结论和建议动作。
- **Prompt 触发点**：`@Tool` description 增加“经营诊断 / 预订趋势 / 是否需要调价”等关键词，让模型在用户问业务问题时能把它和工具语义匹配起来。
- **领域知识**：工具返回里直接带 KPI 定义，避免模型凭空解释：
  - 出租率 = 已售间夜 / 可售间夜
  - ADR = 房费收入 / 已售间夜
  - RevPAR = ADR * 出租率
- **边界**：当前仍是 mock 数据，只用于验证 Agent 能力闭环；真实生产应接 BI/数仓/实时订单，并做权限、口径版本和数据血缘。

**今日动手**

升级 [AgentTools.java](data-agent/src/main/java/com/ai/agent/tool/AgentTools.java) 的 `queryHotelOccupancy`：

- 从固定 mock 数字升级成按 `city + date` 生成稳定样例数据。
- 返回从“指标值”升级成“指标值 + 环比 + KPI 定义 + 领域判断 + 建议动作”。
- 保留单工具入口，避免 Day14 实战过度扩散。

新增 [AgentToolsHotelAnalyticsTest.java](data-agent/src/test/java/com/ai/agent/tool/AgentToolsHotelAnalyticsTest.java)：

- 验证工具输出包含经营诊断、KPI 定义、RevPAR 公式和建议动作。

**一句话总结**

Day 5 解决的是“模型会不会调用工具”；Day 14 解决的是“工具返回的结果是否足够业务化，能不能支撑模型给出像样的酒店经营判断”。

### Day 15 — 落地与总结
- **想**：Agent 在同程酒店业务能解决什么真实问题？从这个 demo 到生产还差什么(数据接入、权限、成本、监控)？
- **画**：重画一张完整的 Agent 架构图，对比第一天的理解。
- **写**：500 字——「我现在怎么理解 Agent」+ 下一步深入方向。
- **自测**(答不上来就是没吃透)：
  1. ReAct 和 Orchestrator 的区别？什么场景用哪个？
  2. 模型凭什么决定调哪个工具？
  3. Agent 没有记忆，那「记住上次对话」是怎么实现的？
  4. RAG 检索到错误内容会怎样，怎么防？
  5. 模型 API 持续超时，你的系统会怎样？

---

## Day 16（可选加餐）— 补 LangChain4j 盲区：写个 AiServices demo

> 为什么加这天：前 15 天你通过项目学到的是 LangChain4j 的「工程用法子集」，但项目**有意没用**它几个标志性能力（尤其声明式 `AiServices`）。花 1–2 小时补上，LangChain4j 才算完整，不只是「会用项目里这套」。

- **盲区清单**（项目自研代替了，所以跟着项目学不到）：
  - `AiServices`——LangChain4j 最招牌的声明式 AI 服务（项目改手动编排了）
  - `RetrievalAugmentor` / `ContentRetriever`——RAG 编排（项目自研多路融合）
  - `DocumentSplitter`——文档切分（项目自研 4 个 Chunker）
  - `ChatMemoryStore`——记忆持久化（项目自研分层记忆）
- **动手**（最值得做的是 `AiServices`）：新建一个**独立的小 demo**（别放进本项目，免得被它的手动编排干扰）——定义一个接口 + `@SystemMessage`/`@UserMessage` 注解 + 绑一个 `@Tool`，几行就跑出一个带工具的 Agent。对比你 Day 4-5 看到的「手动 `ToolSpecifications` + `DefaultToolExecutor`」，体会声明式帮你省了多少样板，以及**项目为什么故意不用它**（要手动控制重试/熔断）。
- **产出**：能跑的 `AiServices` demo + 一句话——「声明式 vs 手动编排，各自的取舍」。

---

## Agent 视角的 10 个核心文件（吃透这些就够站住脚）

| # | 文件 | 是什么 |
|---|---|---|
| 1 | `AgentRuntimeService` | 路由：请求怎么分到各 Agent |
| 2 | `ReActAgent`(+`ReActLoopRunner`/`ReActStepHandler`) | ReAct 思考-行动-观察循环 |
| 3 | `AgentToolInvoker` + `AgentTools` | 工具定义与调用 |
| 4 | `OrchestratorAgent` + `specialist/*` | 多专家编排 |
| 5 | `MemoryManager` | 分层记忆 |
| 6 | `EnhancedRagPipeline` | RAG 检索增强 |
| 7 | `McpModelService` + `ModelHttpClient` | 模型调用 |
| 8 | `ModelRetryExecutor` | 重试 / 熔断 |
| 9 | `AnalysisStreamService` | SSE 流式输出 |
| 10 | `AgentExecutionTraceService` | 执行轨迹（可观测） |

---

## 学完 Agent 核心后的进阶方向（不在这 15 天内，想更全再做）

这些是「后端工程」而非「Agent 核心」，本计划刻意没排进来——等 Agent 吃透了，想成为能扛生产的 Agent 工程师，再补：

- 安全 / RBAC / 多租户隔离（`SecurityContextHelper`、`tenantId` 审计）
- 异步与线程池、`ContextPropagatingTaskDecorator`（上下文跨线程传播）
- Token 成本核算与配额（`TokenQuotaGuard`）
- 向量检索原理（embedding 维度、Milvus 索引/度量）
- 文件处理与知识库入库链路
- 全文检索 JPA vs Elasticsearch
- 故障推演、性能容量计算、安全审计

---

## 核心心法

> 别试图「学会」440 个文件。Agent 的本质就一句话：**LLM + 工具 + 记忆 + 一个让它自主决策的循环**。
> 把上面 10 个文件每个能讲清 3 分钟，你就真的「学会 Agent」了。
