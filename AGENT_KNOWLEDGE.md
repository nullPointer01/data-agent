# Agent 知识库

> 这份文档是你学习 Agent 系统的完整参考手册。
> 每个知识点都连接到本项目的真实代码，理论和实践对照着学。

---

## 目录

1. [LLM 是什么](#1-llm-是什么)
2. [Agent 核心概念](#2-agent-核心概念)
3. [工具调用](#3-工具调用)
4. [记忆系统](#4-记忆系统)
5. [RAG 检索增强生成](#5-rag-检索增强生成)
6. [多 Agent 系统](#6-多-agent-系统)
7. [框架与生态](#7-框架与生态)
8. [可靠性工程](#8-可靠性工程)
9. [生产环境思维](#9-生产环境思维)
10. [领域落地方法](#10-领域落地方法)

---

## 1. LLM 是什么

### 核心认知

LLM（大语言模型）是一个在海量文本上训练的"下一个词预测器"。它学会了语言规律和世界知识，但**没有真正的理解，只有极其精妙的模式匹配**。

> **比喻**：一个博览群书、极度聪明的顾问。他知识渊博，但他只能坐在房间里靠脑子回答问题——不能打电话、不能上网、不能查系统。

### 工作原理（不需要数学）

```
输入文字 → 预测下一个词 → 加入输入 → 再预测下一个 → ... → 完整回复
```

三个关键概念：

| 概念 | 含义 | 实际影响 |
|------|------|---------|
| **Token** | 词块，不等于字。"ChatGPT"=1个token，"你好"≈2个token | 决定处理速度和费用 |
| **上下文窗口** | LLM 能"看到"的最大 token 数 | 超过就截断，前面内容丢失 |
| **Temperature** | 随机性控制。0=确定性最高，1=最有创意 | 生产分析任务用 0~0.3 |

### LLM 能做什么，不能做什么

**✅ 擅长的**
- 理解和生成自然语言
- 推理、归纳、总结、翻译
- 写代码、解释代码
- 从上下文中提取信息

**❌ 天然缺陷（Agent 要解决的正是这些）**
- 没有实时数据（知识有截止日期）
- 会"幻觉"——编造听起来合理的假信息
- 不能主动查数据库/调接口
- 没有持久记忆（每次对话独立）
- 数学计算不可靠
- 不知道你公司内部资料

### 幻觉（Hallucination）——最重要的概念

LLM 不会说"我不知道"，它会用流畅自信的语气编造内容。

**为什么会幻觉？** 它的目标是"预测最可能的下一个词"，不是"说真话"。训练数据里专业问题后面通常跟着专业答案，它学会了这个模式。

**防御方法**：RAG（给它真实资料）+ 结构化输出 + 要求引用来源

### Prompt Engineering 基础

| 技巧 | 用法 | 效果 |
|------|------|------|
| **系统提示词** | 给 LLM 设定角色和规则，对话开始前注入 | 控制行为基线 |
| **少样本（Few-shot）** | 给几个例子，LLM 模仿格式 | 比纯描述提升 30%+ |
| **思维链（CoT）** | 加上"请一步一步思考" | 复杂推理准确率显著提升 |
| **负面约束** | "如果数据不足，直接说不知道" | 减少幻觉 |

**本项目代码对应**：`AgentPromptComposer.java`——组装系统提示词、用户问题、文件内容

---

## 2. Agent 核心概念

### 核心认知

**Agent = LLM + 工具 + 记忆 + 循环**

它把 LLM 从"回答问题"升级为"完成任务"。核心是一个循环：**思考 → 行动 → 观察 → 再思考**。

### 直接调 LLM vs Agent 模式

| | 直接调 LLM | Agent 模式 |
|--|-----------|----------|
| 调用次数 | 一问一答，单次 | 多轮循环，自主决策 |
| 工具能力 | 不能使用工具 | 能调用工具获取实时数据 |
| 知识来源 | 只有训练数据 | 能查知识库、数据库 |
| 适合场景 | 翻译、总结、写作 | 分析、调研、复合任务 |

### ReAct 循环——Agent 的心跳

ReAct = **Re**asoning（推理）+ **Act**ing（行动）

```
用户："查一下上海如家酒店1月的入住率"

思考：我需要查数据库，应该用 queryDatabase 工具
行动：queryDatabase("SELECT occupancy FROM hotels WHERE city='上海' AND brand='如家' AND month='2024-01'")
观察：[{occupancy: 78%}]
思考：拿到数据了，可以回答
回答：上海如家酒店1月平均入住率为 78%
```

**本项目代码对应**：
- `ReActAgent.java`——ReAct 执行入口
- `ReActLoopRunner.java`——驱动思考→行动→观察循环
- `ReActStepHandler.java`——处理每一步

### Agent 的四个组成部分

```
┌─────────────────────────────────────────┐
│              Agent                      │
│                                         │
│  ┌──────────┐    ┌──────────────────┐   │
│  │ LLM 大脑 │    │   工具（Tools）  │   │
│  │ 推理决策 │    │ 查数据/调接口/执行│   │
│  └──────────┘    └──────────────────┘   │
│                                         │
│  ┌──────────┐    ┌──────────────────┐   │
│  │ 记忆系统 │    │   规划（Plan）   │   │
│  │短期+长期 │    │ 拆解任务/排序步骤│   │
│  └──────────┘    └──────────────────┘   │
└─────────────────────────────────────────┘
```

### Function Calling 协议——工业标准

OpenAI 2023 年定义的标准，所有主流 LLM 都支持。用 JSON Schema 描述工具，LLM 返回结构化的工具调用请求。

这就是为什么不同框架（LangChain、LangChain4j）的工具调用逻辑基本一致——底层协议是统一的。

---

## 3. 工具调用

### 核心认知

你用 JSON 描述工具的名字、参数、用途，LLM 读完"工具清单"后，遇到需要工具的问题，输出结构化的"调用指令"，系统负责真正执行，结果再返回给 LLM 继续推理。

### 工具定义长什么样

```json
{
  "name": "query_hotel_occupancy",
  "description": "当用户询问特定城市或酒店品牌的入住率、RevPAR、ADR等历史数据时使用",
  "parameters": {
    "city": { "type": "string", "description": "城市名，如：上海、北京" },
    "brand": { "type": "string", "description": "酒店品牌，如：如家、汉庭" },
    "start_date": { "type": "string", "description": "开始日期，格式 YYYY-MM-DD" },
    "end_date": { "type": "string", "description": "结束日期，格式 YYYY-MM-DD" }
  },
  "required": ["city", "start_date"]
}
```

**本项目代码对应**：
- `AgentTools.java`——用 LangChain4j `@Tool` 注解声明工具，`ToolSpecifications.toolSpecificationFrom()` 自动生成 Schema
- `AgentToolInvoker.java`——分发执行工具调用

### LLM 怎么决定用哪个工具

**关键：`description` 字段。** LLM 不会执行工具，只会读 description 决定要不要调用。

```
❌ 差的 description："查询数据库"
✅ 好的 description："当用户询问特定酒店或城市的入住率、RevPAR、ADR 等历史数据时使用"
```

**好的 description 描述的是"什么时候用"，不只是"是什么"。**

### 本项目的工具体系

| 工具服务 | 职责 | 代码位置 |
|---------|------|---------|
| `AgentDataSourceToolService` | 查询外部数据库 | `agent/tool/` |
| `AgentKnowledgeToolService` | 搜索知识库 | `agent/tool/` |
| `AgentChartToolService` | 生成图表 | `agent/tool/` |
| `AgentConversationToolService` | 查询对话历史 | `agent/tool/` |
| `AgentFileToolService` | 读取文件内容 | `agent/tool/` |

### 工具调用常见失败

| 失败类型 | 原因 | 防御方法 |
|---------|------|---------|
| 参数类型错误 | LLM 传了字符串，期望数字 | 参数 Schema 写清楚类型和格式 |
| 工具选错 | description 不够精确 | 优化 description，加适用条件 |
| 无限循环 | 工具失败，LLM 反复重试 | `maxIterations` 硬限制 |
| 返回空结果 | 数据不存在，LLM 开始幻觉 | 工具返回"数据不存在"明确提示 |
| 执行超时 | 数据库查询慢 | 工具设置超时，返回超时错误 |

### 工具设计最佳实践

- 每个工具只做一件事（单一职责）
- 工具总数不超过 15 个（太多 LLM 会混乱）
- 返回结果简洁，避免返回大段 JSON
- 工具失败要返回明确错误信息，不要抛异常
- 高风险操作（写入/删除）需要确认机制

---

## 4. 记忆系统

### 核心认知

LLM 本身没有记忆，每次调用都是全新的。记忆系统是在 LLM 外部构建的——短期记忆放在 prompt 里，长期记忆存在向量数据库里，用时检索出来塞给 LLM。

### 记忆的四个层级

```
┌──────────────────────────────────────────────────────┐
│  工作记忆（In-context）                               │
│  当前对话内容，在 prompt 里，超出窗口就丢失            │
│  特点：最快，最贵（token 费用）                        │
├──────────────────────────────────────────────────────┤
│  短期记忆（Session）                                  │
│  当前会话历史消息，关闭对话清空                        │
│  代码：SessionManager.java                            │
├──────────────────────────────────────────────────────┤
│  长期记忆（Persistent）                               │
│  跨会话保留，存在数据库                                │
│  代码：MemoryEntry 实体 + memory_entry 表              │
├──────────────────────────────────────────────────────┤
│  语义记忆（Vector）                                   │
│  内容向量化，按语义相似度检索                          │
│  代码：MilvusVectorStoreGateway.java                  │
└──────────────────────────────────────────────────────┘
```

### 向量嵌入（Embedding）——记忆的核心技术

文字 → 数字数组（向量）的转换。语义相近的文字会转换为相近的向量。

```
"北京的酒店"  → [0.23, -0.11, 0.87, ...] (384个数字)
"首都的住宿"  → [0.21, -0.13, 0.85, ...] ← 相近！语义相似
"今天天气真好" → [-0.42, 0.33, -0.12, ...] ← 完全不同
```

**相似度计算**：余弦相似度——两个向量夹角越小，内容越相关。

**本项目代码对应**：
- `EmbeddingGateway.java`——负责文字→向量转换
- 默认用本地 AllMiniLmL6V2 模型，维度 384
- 可切换为 API 模式（`EMBEDDING_PROVIDER=api`）

### 记忆系统常见问题

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| 上下文溢出 | 对话太长超出 token 限制 | 压缩早期消息（`MemoryCompressor`）|
| 记忆污染 | 不同用户的记忆混在一起 | 严格按 tenantId 隔离查询 |
| 检索不准 | 语义相似但内容不相关 | 混合检索（向量 + 全文搜索）|
| 记忆过时 | 旧信息干扰新决策 | 记忆衰减策略（`memory_entry` 有权重字段）|

### 本项目记忆层级

```
working memory  → 当前 prompt 里的对话历史
short-term      → SessionManager 管理的会话
long-term       → memory_entry 表
user_profile    → user_profile 表，用户画像摘要
```

---

## 5. RAG 检索增强生成

### 核心认知

用户问问题 → 先去知识库搜索相关内容 → 把搜到的内容 + 用户问题一起发给 LLM → LLM 基于真实内容回答。这是**解决幻觉的核心武器**。

> **为什么不直接把所有资料喂给 LLM？**
> 资料太多放不进上下文窗口，即使放进去也会分散注意力，而且每次都要付大量 token 费用。

### RAG 完整流程

**离线阶段（一次性）**：
```
文档 → 切块（Chunking）→ 向量化（Embedding）→ 存入向量库 + 全文索引
```

**在线阶段（每次查询）**：
```
用户问题 → 向量化 → 向量库搜相似块 → 全文搜索补充 → 重排序（Rerank）
         → 取 Top-K 内容 → [内容 + 问题] → LLM → 回答
```

**本项目代码对应**：
```
RagRetrievalService → HybridRetriever → RagReranker → RagQueryRewriter
```

### 文档分块（Chunking）——最影响效果的环节

**为什么要切块？** 整个文档太大，无法向量化和检索，必须切成小段。

| 策略 | 做法 | 优缺点 |
|------|------|--------|
| 固定大小 | 按字符数切 | 简单，但可能切断语义 |
| 语义切块 | 按段落/标题切 | 保留语义完整性，效果更好 |
| 父子块 | 小块检索，返回大块上下文 | 精准检索 + 丰富上下文 |

**本项目代码对应**：
- `MarkdownStructureParser.java`——按 Markdown 结构切块
- `TextChunker.java`——切块主逻辑
- 父子块：`parentContext` 字段——检索小块，返回其所在段落的完整内容

### 混合检索——为什么比纯向量更好

```
纯向量检索：语义相似但关键词不同 → 可能漏掉
纯全文检索：关键词匹配但语义不同 → 可能误召回

混合检索 = 向量（捕获语义）+ 全文（捕获关键词）→ 两者取长补短
```

**本项目代码对应**：`HybridRetriever.java`

### RAG 效果评估指标

| 指标 | 含义 | 衡量方式 |
|------|------|---------|
| 召回率 | 相关内容找到了多少 | 人工标注评测集 |
| 精确率 | 找到的内容有多少是对的 | 人工标注评测集 |
| 答案忠实度 | 回答是否基于检索内容 | 检查回答中的引用 |
| 答案相关性 | 回答是否回答了问题 | 用另一个 LLM 评判 |

### 本项目 RAG 全链路

```
用户问题
  ↓
RagQueryRewriter     ← 改写问题，提高检索质量
  ↓
HybridRetriever      ← 向量检索 + 全文检索
  ↓
RagReranker          ← 重排序，把最相关的排在前面
  ↓
RagContextCompressor ← 压缩上下文，避免太长
  ↓
塞入 Prompt → LLM → 回答
```

---

## 6. 多 Agent 系统

### 核心认知

当任务复杂到一个 Agent 搞不定，或者需要不同专业能力时，用多 Agent。核心模式：**Orchestrator（项目经理）拆任务分派给 Specialist（专家），整合结果**。

### 什么时候用单 Agent，什么时候用多 Agent

| 用单 Agent（ReAct）当 | 用多 Agent（Orchestrator）当 |
|---------------------|---------------------------|
| 任务目标明确，步骤线性 | 任务需要不同专业能力 |
| 工具数量 < 10 个 | 子任务可以并行执行 |
| 不需要并行处理 | 需要专家级质量输出 |
| 对延迟要求高 | 任务太复杂，单 Agent 容易迷失 |

### Orchestrator 工作流程

```
用户请求
  ↓
意图决策（OrchestratorAgent → OrchestratorDecision）
  ↓
任务规划（OrchestratorTaskPlanner）
  ↓
专家调度（AgentSpecialistRegistry + SpecialistFactory）
  ├── 数据专家  → DataAgentSpecialist
  ├── 知识专家  → KnowledgeExpertSpecialist
  ├── 图表专家  → ChartExpertSpecialist
  ├── 报告专家  → ReportExpertSpecialist
  ├── 聊天专家  → ChatAgentSpecialist
  ├── 技能专家  → SkillAgentSpecialist
  └── ReAct专家 → ReActAgent（兜底）
  ↓
结果整合（OrchestratorResult / OrchestratorExecutionResult）
  ↓
写入执行轨迹（AgentExecutionTraceService）
  ↓
返回结果
```

### 本项目路由逻辑

```java
// AgentRuntimeService.java execute() 方法
if (request.isCommand())    → executeCommand()        // 1. 斜杠命令
if (request.hasAgent())     → multiAgentRuntimeService // 2. 指定Agent
if (request.hasSkill())     → skillExecutionService    // 3. 指定Skill
if (orchestratorAgent ≠ null) → orchestratorAgent     // 4. 编排器
return reActAgent.execute() →                          // 5. 兜底ReAct
```

**设计原则**：确定性 → 适应性。越靠前越快越确定，越靠后越智能越灵活。

### 多 Agent 常见陷阱

| 陷阱 | 表现 | 防御 |
|------|------|------|
| 通信爆炸 | Agent 越多，协调成本越高，延迟线性增加 | 控制专家数量，优先串行 |
| 错误传播 | 一个专家输出错误，下游基于错误继续 | 每个专家输出做验证 |
| 死锁 | Agent A 等 B，B 等 A | 超时机制 + 单向依赖 |
| 过度设计 | 简单任务也用多 Agent | 先用单 Agent，复杂了再升级 |

---

## 7. 框架与生态

### 关键认知

**所有框架解决同一个问题，概念完全一致。换框架 = 换语法，不换思想。** 学透一个框架的原理，其他框架一周内就能上手。

### 主流框架对比

| 框架 | 语言 | 定位 | 特点 |
|------|------|------|------|
| **LangChain** | Python | 通用 Agent | 最流行，生态最大，但过于抽象 |
| **LangChain4j** | Java | 通用 Agent | 你用的框架，API 严谨，适合企业 |
| **LlamaIndex** | Python | RAG 专注 | 知识库和索引场景最强 |
| **AutoGen** | Python | 多 Agent 对话 | Agent 间像人一样互相发消息 |
| **CrewAI** | Python | 角色式多 Agent | 每个 Agent 有角色和目标 |
| **Semantic Kernel** | .NET/Python | 企业级 | 微软生态，强调 Skill/Plugin |

### 你的系统在框架谱系里的位置

你用 LangChain4j 做底层模型调用，但 **Agent 核心逻辑是自研的**：

- ReAct 循环：自己实现，不依赖 LangChain4j 的 Agent
- Orchestrator：完全自研，业界少见的 Java 实现
- 记忆、RAG：自研 + LangChain4j 工具组合

**配置入口**：`LangChain4jConfig.java`

### LangChain4j 在本项目的使用边界（2026-06 盘点）

一条贯穿全项目的判断原则：**基础设施用框架，差异化价值自研**。盘点下来，没有"能用框架却还在手写"的窟窿，也没有"该自研却硬塞框架"的妥协。

**用框架的（基础设施，直接复用成熟实现）**

| 能力 | LangChain4j 类 |
|------|---------------|
| 模型调用（同步 + 流式） | `ChatLanguageModel` / `StreamingChatLanguageModel` |
| 工具协议（Function Calling 全链路） | `ToolSpecifications` / `DefaultToolExecutor` / `ToolExecutionRequest` / `ToolExecutionResultMessage` |
| 提示词模板 | `PromptTemplate` |
| 对话记忆（按 token 滚动） | `TokenWindowChatMemory` |
| token 计数 | `Tokenizer`（全局单例 `SharedTokenizer`，避免重复加载 BPE 词表） |
| 向量化 / 向量库 | `EmbeddingModel` / `MilvusEmbeddingStore` |
| 文本段载体 | `TextSegment` |

**有意自研的（差异化价值，框架覆盖不了）**

| 能力 | 为什么不用框架 |
|------|--------------|
| ReAct 循环 | 框架的 Agent 是黑盒，自研才能控制每步思考/观察/中断/重试 |
| Orchestrator 多专家编排 | 框架无对应能力 |
| RAG 多路融合 | 框架 `RetrievalAugmentor` 是线性编排，覆盖不了"全文 + 向量 + 父上下文 + 重排 + 压缩 + 质量评估" |
| 文档切分（4 个 Chunker） | 框架只有固定规则切分；自研有语义分块 `SemanticChunker`、层次父子分块 `HierarchicalChunker` |
| 分层记忆 | 框架 `ChatMemoryStore` 只是简单 KV，覆盖不了分层 + 压缩 + 衰减 + 用户画像 |
| 声明式 `AiServices` | 故意不用，改手动消息级编排以定制重试/熔断 |

> **学习要点**：框架是省力工具，不是信仰。**通用能力交给框架，护城河自己造。** 判断标准就一句话——这块逻辑是不是你的差异化价值：是，自研；不是，用框架。

### 模型选型逻辑

| 模型 | 最适合场景 | 注意 |
|------|----------|------|
| **GPT-4o** | 通用能力，工具调用最稳定 | 贵，延迟高 |
| **Claude 3.5** | 长文本、代码、分析 | 上下文 200K，中文好 |
| **Kimi / Moonshot** | 中文理解，长文档 | 代码里有专门适配逻辑 |
| **GPT-4o-mini** | 简单分类、低延迟场景 | 便宜，能力有限 |

**本项目模型配置**：`model_config` 表 + `ModelConfigService.java`
**Kimi 适配**：`com.ai.mcp.ModelHttpClient`——有 kimi-2.6 等别名归一化逻辑

---

## 8. 可靠性工程

### 核心认知

LLM 不是确定性系统，同一个输入可能给不同输出。**Agent 的可靠性工程 = 在不确定性上建立确定性**——通过结构化输出、重试、降级、验证来实现。

### LLM 会出哪些错（必须知道）

| 错误类型 | 表现 | 本项目防御手段 |
|---------|------|-------------|
| **幻觉** | 编造不存在的数据 | RAG + 要求引用来源 |
| **格式错误** | 要求 JSON，返回 Markdown | 结构化输出 Schema |
| **无限循环** | 工具调用没有终止 | `maxIterations` 限制 |
| **工具滥用** | 不该用工具时乱调用 | description 写清楚适用条件 |
| **上下文丢失** | 对话太长，前面内容被截断 | `MemoryCompressor` |
| **API 限速** | 429 Too Many Requests | `ModelRetryExecutor` 重试 |
| **API 不可用** | 503 Service Unavailable | 熔断 + 降级到备用模型 |

### 本项目可靠性设计

**重试熔断** (`ModelRetryExecutor.java`)：
```
429 / 5xx → 重试（有意义的失败）
401 / 403 / 404 → 不重试（避免无意义消耗）
```

**降级路径**：
```
Skill 返回 null → 继续路由到 Orchestrator
Orchestrator 不可用 → 回退 ReAct
命令未知 → 降级为普通问题交给 ReAct
```

**错误恢复** (`ErrorRecoveryType.java`)：工具调用失败后的处理策略枚举

**限流** (`RateLimitInterceptor.java`)：防止单用户打爆 API

**配额保护** (`TokenQuotaGuard.java`)：用户每日 token 上限

**缓存** (`RedisConfig.java` + `CacheNames.java`)：
```
MODELS     → 2 小时 TTL
ROLES      → 4 小时 TTL
SKILLS     → 1 小时 TTL
```

### Prompt 工程中的可靠性技巧

```
1. 角色设定：
   "你是一个严谨的数据分析师，只基于提供的数据回答，不做任何推断"

2. 负面约束：
   "如果数据不足，直接说'数据不足'，绝对不要猜测或推断"

3. 输出格式：
   "请严格按以下 JSON 格式返回，不要有任何多余内容：{...}"

4. 思维链：
   "请先列出你的推理步骤，再给出最终答案"

5. 引用来源：
   "回答时必须注明信息来源，格式为：根据[来源]..."
```

---

## 9. 生产环境思维

### 核心认知

能写出来 ≠ 能跑在生产。生产环境关心的是：
- **可观测性**：出了问题能不能发现
- **可调试性**：能不能快速定位原因
- **经济性**：能不能控制成本

### 可观测性三件套

| 工具 | 作用 | 本项目实现 |
|------|------|----------|
| **Metrics（指标）** | 系统整体状态：QPS、延迟、错误率 | `MeterRegistry`（Micrometer）|
| **Logs（日志）** | 每个操作的详细记录 | `LOGGER.info/warn/error` |
| **Traces（追踪）** | 一个请求的完整链路 | `AgentExecutionTrace` + `agent_execution_trace` 表 |

**关键指标应该监控**：
- LLM 调用延迟（P50/P95/P99）
- Token 消耗量（按用户/按租户）
- 工具调用成功率
- Agent 循环次数分布
- 错误率分类（4xx vs 5xx）

### 成本控制

**成本 = Token 数量 × 单价。** 一个 Agent 循环可能调用 LLM 5-10 次，每次都要付费。

```
控制手段：
1. Prompt 压缩 → 系统提示词精简，减少每次调用的基础消耗
2. 模型分级 → 简单任务用便宜模型，复杂任务才用贵的
3. 缓存热点 → Redis 缓存高频查询结果（本项目已实现）
4. 配额限制 → TokenQuotaGuard 每用户每日上限
5. 异常告警 → 某个请求消耗了正常 10 倍时立即告警
```

### 延迟优化

```
1. SSE 流式输出 → 边生成边返回，用户体验从"等10秒"变"立刻看到内容"（已实现）
2. 并行工具调用 → 多个独立工具同时执行，不串行等待
3. 向量索引预热 → Milvus collection 提前 load 到内存
4. 结果缓存 → 相同查询不重复调用 LLM
```

**本项目 SSE 实现**：`AnalysisStreamService.java`

### 安全与合规

| 风险 | 防御方法 | 本项目实现 |
|------|---------|----------|
| **Prompt 注入** | 输入过滤 + 系统提示词加固 | 系统提示词明确角色边界 |
| **数据越权** | 严格 tenantId 过滤 | 每个查询都带 tenantId |
| **API Key 泄露** | 加密存储，日志脱敏 | `APP_ENCRYPTION_KEY` + CryptoUtil |
| **操作审计** | 所有关键操作记录日志 | `AuditLogService.java` |

### 一个隐蔽的坑：异步线程会丢上下文

SSE 流式输出、文件处理都跑在异步线程池里。Spring Security 的 `SecurityContext` 和日志 `MDC` 默认**绑定在发起请求的那个线程**上，异步线程拿不到——结果是**异步线程里 tenantId 取不到、租户隔离失效，日志也断了链路**。

本项目用 `TaskDecorator` 在任务入队时快照上下文、在异步线程里还原（`AsyncConfig.wrapWithContext`）。注意线程池用了 `CallerRunsPolicy`，任务可能回到请求线程上跑，所以还原时要恢复"前一个上下文"而不是简单清空。

> **学习要点**：Agent 大量用异步（流式、并行工具、后台任务）。只要涉及多租户或链路追踪，异步边界上的上下文透传就是必查项。

### RBAC 权限体系（本项目）

```
sys_user → sys_user_role → sys_role → sys_role_permission → sys_permission

角色：USER（普通用户）/ ADMIN（管理员）
权限：app:use（使用应用）/ *:*（管理员全权）
```

---

## 10. 领域落地方法

### 核心认知

技术不值钱，解决问题才值钱。Agent 落地的核心问题是：**这个任务适合用 Agent 做吗？**

### 识别好的 Agent 用例——三个标准

| 标准 | 说明 | 举例 |
|------|------|------|
| **重复性高** | 每天/每周都要做同样的分析流程 | 每天生成入住率报告 |
| **数据驱动** | 答案可以从数据中推导，不是主观判断 | RevPAR 异常分析 |
| **有明确输出** | 知道"好的结果"长什么样，可以评估质量 | 结构化的分析报告 |

**❌ 不适合 Agent 的场景**：需要创意、需要线下判断、法律红线领域、实时性要求极高

**✅ 适合 Agent 的场景**：数据分析、报告生成、规则查询、异常检测、客服问答

### 酒店业高价值 Agent 场景

| 场景 | 描述 | 业务价值 |
|------|------|---------|
| **收益管理分析** | 分析入住率、ADR、RevPAR 趋势，识别定价机会 | 直接影响收入 |
| **竞对价格追踪** | 实时比较竞对价格，生成调价建议 | 替代 2-3 小时人工 |
| **客诉智能分析** | 归类评论问题，生成改进建议 | 提升客服效率 |
| **运营自动报告** | 拉取多维数据，生成周/月报含异常预警 | 管理决策支持 |
| **需求预测** | 基于历史数据和事件，预测未来入住率 | 提前调整策略 |

### 酒店业关键指标（领域知识）

| 指标 | 英文 | 计算方式 | 意义 |
|------|------|---------|------|
| **入住率** | Occupancy Rate | 已售房间 / 可用房间 | 销售效率 |
| **平均房价** | ADR | 客房收入 / 已售房间数 | 定价能力 |
| **每间可用房收入** | RevPAR | 入住率 × ADR | 综合经营绩效 |
| **总收入** | TRevPAR | 总收入 / 可用房间数 | 多元收入能力 |

### 从用例到 Agent 的设计步骤

```
1. 定义问题
   "每天早上，运营经理需要花 2 小时整理昨日各酒店数据并发邮件"

2. 拆解需要的工具
   - query_yesterday_metrics(hotel_id) → 查昨日指标
   - compare_with_last_week(hotel_id)  → 对比上周
   - detect_anomalies(metrics)         → 检测异常
   - generate_report(data)             → 生成报告
   - send_email(report, recipients)    → 发邮件

3. 设计 Prompt
   "你是一个酒店运营分析师，每天生成运营日报。
   数据来源：[工具列表]。
   报告格式：[模板]。
   如发现异常（入住率下降超过 10%），必须标注原因分析。"

4. 跑 Demo，评估质量
   - 数据准确吗？
   - 异常检测到了吗？
   - 报告格式符合要求吗？

5. 迭代优化
   - Prompt 加约束
   - 工具返回格式优化
   - 边界情况处理（节假日、数据缺失）
```

---

## 附录：本项目核心文件索引

### Agent 执行链路

```
POST /api/v1/analysis/analyze
  └── AnalysisStreamService
        └── AgentRuntimeService.execute()
              ├── SkillManager.processWithCommand()   [斜杠命令]
              ├── MultiAgentRuntimeService.execute()  [指定Agent]
              ├── SkillExecutionService.execute()     [指定Skill]
              ├── OrchestratorAgent.executeStructured() [编排器]
              └── ReActAgent.execute()                [兜底]
```

### 关键配置文件

| 配置 | 文件 | 作用 |
|------|------|------|
| 应用配置 | `application-local.yml` | 数据库、Milvus、Redis 连接 |
| 模型配置 | `model_config` 表 | LLM 的 API Key、模型名、参数 |
| Agent 推理 | `app.agent.reasoning.*` | 是否开启 LLM 意图识别 |
| 编排器 | `app.orchestrator.*` | 编排器功能开关 |
| 记忆 | `MEMORY_MODEL_COMPRESSION_ENABLED` | 是否用模型压缩记忆 |

### 数据库核心表

| 表名 | 作用 |
|------|------|
| `model_config` | LLM 模型配置 |
| `skill_config` | 技能配置 |
| `memory_entry` | 长期记忆存储 |
| `user_profile` | 用户画像 |
| `knowledge_entry` | 知识库文档 |
| `agent_execution_trace` | 执行轨迹（调试用） |
| `agent_feedback` | 质量反馈 |
| `file_metadata` | 上传文件元数据 |
| `sys_user/role/permission` | RBAC 权限体系 |

---

*最后更新：2026-06-14*
*对应项目版本：data-agent main 分支*
*本次更新：修正迁移后失效的代码引用（`AgentToolDefinition`→`AgentTools`+`@Tool`、Orchestrator 意图/注册/整合类名）；新增「LangChain4j 使用边界盘点」与「异步线程丢上下文」两节*
