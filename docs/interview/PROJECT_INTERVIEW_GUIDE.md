# Data Agent 项目讲解稿

这份文档用于面试、答辩或项目复盘。重点不是“我做了一个聊天机器人”，而是讲清楚这个项目如何把大模型、Agent 编排、RAG、企业数据源、权限治理和可观测性组合成一个可落地的数据分析平台。

## 1. 一句话介绍

Data Agent 是一个面向企业数据分析场景的智能体平台。用户可以上传文件、维护知识库、配置外部数据源和模型，系统会通过 RAG 检索、ReAct 工具调用、多 Agent 编排、会话记忆和权限控制，为用户生成可追踪、可审计的数据分析结果。

可以这样开场：

> 这个项目不是简单调用一次大模型接口，而是围绕企业数据分析做了一套 Agent Runtime。它支持模型配置、RAG 检索、工具调用、文件解析、SQL 数据源访问、长期记忆、管理员权限、审计日志和执行追踪。核心目标是让 Agent 在回答时尽量基于企业资料和真实数据，而不是自由发挥。

## 2. 技术栈

- 后端：Spring Boot 3.2、Java 17、Spring MVC、Spring Security、Spring Data JPA
- 前端：React、Vite、lucide-react
- 存储：MySQL 作为业务主库，Redis 用于扩展缓存/限流场景，Milvus 作为向量库
- AI 框架：LangChain4j，兼容 OpenAI 风格的 Chat Completions API
- 文档/文件解析：Apache POI、PDFBox
- 可观测性：Actuator、Prometheus、结构化日志、Agent 执行轨迹

核心依赖可以从 `pom.xml` 里看到：`spring-boot-starter-web`、`spring-boot-starter-data-jpa`、`spring-boot-starter-security`、`langchain4j-open-ai`、`langchain4j-milvus`、`mysql-connector-j`、`poi-ooxml`、`pdfbox` 等。

## 3. 整体架构怎么讲

可以按 5 层讲：

1. 接入层：React 控制台 + Spring MVC API，支持登录、模型配置、知识库、文件、Agent 配置、数据源、审计和追踪。
2. 安全层：JWT 无状态认证、RBAC 角色权限、管理员初始化、租户隔离、管理员接口保护。
3. Agent Runtime 层：根据用户请求决定走命令、指定 Agent、指定 Skill、Orchestrator 或 ReAct 兜底。
4. 能力层：模型网关、RAG 管道、工具系统、文件解析、数据源查询、记忆系统、图表生成。
5. 存储与观测层：MySQL 存业务数据，Milvus 存向量，结构化日志和执行轨迹记录 Agent 的关键步骤。

一张口述链路：

> 用户从前端发起分析请求，后端 `AnalysisController` 进入 `DataAnalysisAgentImpl`。这里先校验问题、加载会话和文件内容，然后交给 `AgentRuntimeService`。Runtime 会判断请求类型，如果没有指定 Skill 或 Agent，就优先进入 `OrchestratorAgent`。编排器会做复杂度分类、意图识别、专家路由和多任务计划；如果没有匹配到专家，就回退到内置 `ReActAgent`。ReAct 执行前会通过 `ReActRequestContextBuilder` 注入 RAG 上下文和记忆上下文，随后在 `ReActLoopRunner` 中进行最多 8 轮“模型思考 - 工具调用 - 观察结果 - 最终回答”的循环。

## 4. 请求主链路

关键类：

- `DataAnalysisAgentImpl`：分析入口，负责请求校验、会话解析、文件内容加载和截断。
- `AgentRuntimeService`：运行时路由，决定走 command、指定 Agent、Skill、Orchestrator 或 ReAct。
- `OrchestratorAgent`：多 Agent 编排和专家路由。
- `ReActAgent`：内置 ReAct 智能体。
- `ReActLoopRunner`：同步/流式 ReAct 循环执行器。

主流程：

```text
前端分析请求
  -> AnalysisController
  -> DataAnalysisAgentImpl.analyze()
  -> AgentRuntimeService.execute()
  -> OrchestratorAgent.executeStructured()
  -> ReActAgent.execute() 或专家 Agent
  -> McpModelService / AgentToolInvoker / RAG / Memory
  -> AnalysisResponse
```

这里可以强调一个设计点：

> 我没有把所有逻辑堆在 Controller 里，而是拆成入口校验、Runtime 路由、编排器、ReAct 循环、工具调用、模型网关这些模块。这样后续新增 Agent 类型、工具或模型供应商时，不需要重写主流程。

## 5. Agent 实现细节

### 5.1 ReAct Agent

`ReActAgent` 的核心是让模型不是直接回答，而是按“思考、行动、观察”的模式工作。系统提示词中明确要求：

- 先分析用户意图；
- 需要数据时调用工具；
- 需要计算时使用计算工具；
- 搜索知识时使用 `searchKnowledge` 或 `searchMemory`；
- 每轮只调用一个工具；
- 最终回答必须整合工具结果。

`ReActLoopRunner` 负责真正的循环。它的关键机制：

- 最大迭代次数为 8，避免 Agent 陷入无限循环；
- 每轮调用模型，解析模型返回中的工具调用语法；
- 如果需要工具，交给 `AgentToolInvoker` 执行；
- 工具结果作为 Observation 追加回消息上下文；
- 如果模型给出最终答案，就结束循环；
- 全程记录 `REACT_START`、`REACT_ITERATION_START`、`REACT_ITERATION_RESULT`、`REACT_COMPLETION` 等结构化事件。

面试说法：

> ReAct 的难点不是写一个 prompt，而是要把循环边界、工具解析、异常恢复、流式输出、工作记忆和日志追踪都控制住。我这里把循环本身放在 `ReActLoopRunner`，工具执行放在 `AgentToolInvoker`，请求上下文构建放在 `ReActRequestContextBuilder`，这样每个模块职责比较清楚。

### 5.2 工具系统

Agent 可调用的工具集中在 `AgentTools`，再由 `AgentToolInvoker` 注册成模型可识别的工具规格。

当前工具包括：

- 技能：`listAvailableSkills`、`useSkill`
- 文件：`listFiles`、`getFileContent`、`analyzeFileData`
- 知识和记忆：`searchKnowledge`、`searchMemory`、`getMemoryStats`
- 数据源：`listDataSources`、`getDatabaseSchema`、`executeSQL`、`previewDataSource`
- 通用：`calculate`、`getCurrentTime`、`askUserForInfo`
- 图表：`generateChart`

这里可以讲安全边界：

> SQL 工具不是让模型随便执行任意 SQL，而是通过数据源服务做约束，设计上只支持查询类操作。面试时我会强调：Agent 工具必须有权限和行为边界，否则模型一旦生成危险指令就会影响真实系统。

### 5.3 Fast Path 和规划

`ReActAgent` 中还有两个优化：

- Fast Path：简单问题可以直接回答，减少不必要的工具循环。
- Planning / Parallel Precheck：复杂任务先生成执行计划，必要时并行做预检。

这些开关在 `application.yml` 中：

```yaml
app:
  agent:
    reasoning:
      fast-path-enabled: true
      planning-enabled: true
      parallel-precheck-enabled: true
      reflection-enabled: true
      working-memory-enabled: true
```

讲法：

> 我把 Agent 分成“简单问题直答”和“复杂问题推理执行”两条路径。简单问题直接走 Fast Path，降低成本和延迟；复杂问题再进入 ReAct 或 Orchestrator，保证复杂任务的工具调用能力。

## 6. 多 Agent 编排怎么讲

`OrchestratorAgent` 是这个项目比普通 ReAct 更进一步的地方。它负责：

- 用 `TaskComplexityClassifier` 判断任务复杂度；
- 用 `IntentAnalyzer` 判断用户意图和推荐 Agent 类型；
- 从 `AgentProfileService` 获取已启用的 Agent 配置；
- 先按名称/描述做文本匹配，再按意图类型匹配；
- 对复杂任务生成 `OrchestrationPlan`；
- 按阶段执行多个 `OrchestrationTask`；
- 通过 `CollaborationManager` 注入共享上下文；
- 最后用 `ResultIntegrator` 汇总结果。

可以这样讲：

> Orchestrator 不是替代 ReAct，而是在 ReAct 外面加了一层任务路由和协作调度。比如一个问题同时涉及数据查询、知识检索和报告总结，它可以拆成多个专家任务，先执行数据专家，再执行知识专家，最后整合答案。如果没有匹配到专家配置，就兜底到内置 ReAct，保证系统可用性。

项目中有这些专家类型：

- `DataAgentSpecialist`
- `KnowledgeExpertSpecialist`
- `ChartExpertSpecialist`
- `ReportExpertSpecialist`
- `SkillAgentSpecialist`
- `ChatAgentSpecialist`

面试亮点：

> 我这里没有把专家 Agent 写死在代码里，而是有 `AgentProfile` 配置和 `AgentSpecialistRegistry` 注册机制。配置层面可以维护 Agent 名称、类型、提示词、模型、技能和数据源，运行时再动态路由。

## 7. RAG 实现细节

RAG 主链路在 `EnhancedRagPipeline`。

它不是简单向量检索，而是完整管道：

```text
用户问题
  -> RagQueryRewriter 分析/改写查询
  -> HybridRetriever 混合检索
     -> Milvus 向量检索
     -> JPA/Elasticsearch 全文检索
  -> RagReranker 重排
  -> RagParentContextResolver 补充父级上下文
  -> RagContextCompressor 压缩上下文
  -> 生成引用 RagCitation
  -> 注入 ReAct prompt
```

关键设计点：

- 向量库：`MilvusVectorStoreGateway`
- Embedding：`EmbeddingGateway` 统一调用 OpenAI-compatible 外部服务，启动探测并逐次校验向量维度
- 分块：`TextChunker`、`SmartTextChunker`、`HierarchicalChunker`、`MarkdownStructureParser`
- 混合检索：`DefaultHybridRetriever`
- 全文召回：`ElasticsearchFullTextRetriever`，BM25 + 租户/来源精确过滤
- 融合排序：`RrfFusionRanker`
- RAG 健康检查：`RagHealthService`
- RAG 质量评估：`RagQualityEvaluationService`

可以这样讲：

> RAG 部分我做了向量检索和全文检索的混合召回。向量检索负责语义相似，全文检索负责关键词精确命中，然后用 RRF 做融合，再重排、补父级上下文、压缩上下文，最后带引用编号注入到 Agent。这样相比单纯向量检索，能减少漏召回，也更方便排查每次回答到底参考了哪些资料。

真实踩坑可以补一句：第一版 JPA LIKE 会扫描业务表并在查询时重新切块，既不是 BM25，也没有稳定分数；切到 ES 后又遇到动态 Mapping 把租户字段映射成 `text`，导致 `term` 过滤零命中。最终改成 ES-only、`data-agent-rag-v2` 显式 Mapping，并让索引失败直接暴露。

配置在 `application.yml`：

```yaml
app:
  rag:
    enabled: true
    top-k: 6
    candidate-top-k: 20
    min-score: 0.45
    max-context-chars: 5000
    elasticsearch:
      index-name: data-agent-rag-v2
```

## 8. 记忆系统怎么讲

项目里有短期记忆、长期记忆、用户画像和工作记忆：

- `MemoryManager`：统一构建记忆上下文；
- `ShortTermMemory` / `LongTermMemory`：不同生命周期的记忆；
- `ConversationMemoryCaptureService`：从对话中提取可沉淀记忆；
- `MemoryRetentionService` / `MemoryDecayScheduler`：保留和衰减策略；
- `UserProfileMemoryService`：用户画像记忆；
- `VectorMemoryService`：把对话、文件、知识、技能、长期记忆写入 Milvus。

请求进入 ReAct 前，`ReActRequestContextBuilder` 会做两类增强：

- RAG 上下文：企业资料检索结果；
- Memory 上下文：当前会话和用户相关记忆。

讲法：

> 我把 RAG 和 Memory 分开理解：RAG 是企业知识和文件资料，Memory 是用户和会话历史。RAG 解决“系统知道什么资料”，Memory 解决“系统记得这个用户和这次任务什么上下文”。两者最后都会变成 prompt 上下文，但来源、生命周期和隔离策略不同。

## 9. 模型配置和 Kimi 接入怎么讲

模型配置实体是 `ModelConfig`，字段包括：

- `provider`
- `apiKey`
- `baseUrl`
- `modelName`
- `temperature`
- `maxTokens`
- `enabled`
- `isDefault`
- `tenantId`

调用链路：

```text
业务代码
  -> McpModelService
  -> ModelClientRegistry / ModelHttpClient
  -> OpenAI-compatible Chat Completions API
```

设计细节：

- `McpModelService` 负责上下文、配额检查、重试、token 估算、调用日志；
- `ModelClientRegistry` 缓存 LangChain4j 模型客户端；
- `ModelHttpClient` 作为 HTTP fallback，直接兼容 OpenAI 风格接口；
- 对 Kimi / Moonshot 做了 provider 识别和 baseUrl/modelName 默认值；
- API Key 使用 `EncryptedStringConverter` 加密落库；
- 模型配置更新后通过 `ModelConfigChangeEvent` 刷新缓存。

可以这样讲 Kimi 问题：

> 我把模型接入抽象成 OpenAI-compatible 的配置，而不是写死某一家。Kimi/Moonshot 本质上也走 Chat Completions 风格接口，所以只要配置 provider、baseUrl、modelName 和 apiKey，Agent 主流程不需要改。之前遇到的 401/404，本质是 baseUrl、模型名或 key 权限不匹配，所以我在 `ModelHttpClient` 里对 401、403、404 做了更明确的错误提示。

## 10. 权限和多租户

安全配置在 `SecurityConfig` 和 `AuthService`。

关键点：

- JWT 无状态登录；
- Access Token + Refresh Token；
- BCrypt 加密密码；
- `bootstrap-admin` 创建首个管理员；
- Spring Security 方法级权限；
- 管理接口统一要求 ADMIN；
- 普通 `/api/v1/**` 需要认证；
- CORS 只放行本地开发或配置的 origin；
- 用户带 `tenantId`，向量检索和业务数据支持租户隔离。

可以这样讲：

> 因为这是企业管理后台，所以我没有只做一个登录接口。系统有首个管理员初始化、角色权限、管理员接口保护和租户字段。RAG 和记忆检索时也会带 tenant/user 过滤，避免不同租户或用户之间的数据串读。

## 11. 文件和知识库

文件处理相关类：

- `FileProcessingServiceImpl`
- `FileProcessingWorker`
- `PdfFileContentParser`
- `DocxFileContentParser`
- `PptxFileContentParser`
- `SpreadsheetFileContentParser`
- `PlainTextFileContentParser`
- `FileVectorCleanupService`

知识库相关类：

- `KnowledgeService`
- `KnowledgeVectorIndexService`
- `KnowledgeSyncService`
- `KnowledgeVectorEventPublisher`
- `KnowledgeVectorEventListener`

讲法：

> 文件上传后不是只存原文件，而是会解析成文本，生成元数据，再异步写入向量库。知识库也类似，新增或同步后触发向量索引。这样 Agent 后续可以通过 RAG 找到文件和知识内容，而不是每次都把全文塞进 prompt。

## 12. 数据源和 SQL 工具

数据源相关类：

- `DataSourceService`
- `DataConnectorService`
- `DataConnectorJdbcService`
- `DataConnectorHttpService`
- `DataConnectorSqlPolicy`
- `DataConnectorLookupService`

Agent 工具包括：

- `listDataSources`
- `getDatabaseSchema`
- `executeSQL`
- `previewDataSource`

面试可以强调：

> 数据库不是直接暴露给模型，而是通过工具门面访问。模型先调用 Schema 工具了解表结构，再生成 SELECT 查询。SQL 执行经过策略类限制，只允许查询类语句，这是为了防止 Agent 产生修改或删除数据的 SQL。

## 13. 可观测性和质量闭环

这个项目的亮点之一是 Agent 不只是“回答完就结束”，而是有追踪和反馈闭环。

相关模块：

- `StructuredLogger`：结构化日志；
- `AgentExecutionTraceService`：执行轨迹；
- `AgentFeedbackService`：用户反馈；
- `AgentQualityService`：质量看板；
- `AgentReasoningHealthService`：推理健康；
- `RagHealthService`：RAG 健康；
- `RagQualityEvaluationService`：RAG 质量评估。

讲法：

> 我在 Agent 执行过程中记录模型调用、ReAct 迭代、工具数量、是否 fallback、执行耗时、RAG 检索数量、引用数量等信息。这样当用户说答案不对时，不需要猜，可以回看执行轨迹：模型用了哪个模型、检索到了哪些资料、调用了哪些工具、哪一步失败。

## 14. 项目难点和我的解决方式

### 难点 1：如何减少模型幻觉

解决：

- RAG 上下文优先；
- 工具获取真实文件、知识、数据库结果；
- Prompt 中要求无数据时说明缺口；
- 最终答案附带引用和执行轨迹。

### 难点 2：如何让 Agent 可控

解决：

- ReAct 最大 8 轮；
- 工具白名单注册；
- SQL 工具限制 SELECT；
- 管理接口 ADMIN 权限；
- token 配额和模型调用重试；
- 结构化日志追踪每轮行为。

### 难点 3：如何支持多个模型供应商

解决：

- 抽象 `ModelConfig`；
- OpenAI-compatible HTTP client；
- provider/baseUrl/modelName 可配置；
- 模型缓存和配置变更事件刷新；
- API Key 加密存储。

### 难点 4：如何处理企业资料检索质量

解决：

- 文档分块；
- Milvus 向量检索；
- JPA/ES 全文检索；
- RRF 融合；
- 重排；
- 上下文压缩；
- RAG 健康和质量评估。

## 15. 面试常见问题回答

### Q1：你这个 Agent 和普通 ChatGPT 套壳有什么区别？

答：

普通套壳是把用户问题直接发给模型。我这个项目有完整 Agent Runtime：进入模型前会做会话、文件、RAG、记忆上下文构建；执行时可以调用工具查询文件、知识库、数据库、计算器和图表；复杂任务还可以由 Orchestrator 拆成多个专家任务；执行过程有轨迹、反馈和审计。因此它更像一个可治理的数据分析 Agent 平台。

### Q2：ReAct 是怎么实现的？

答：

我把 ReAct 拆成四块：`ReActAgent` 负责组织执行，`ReActLoopRunner` 负责循环，`ReActResponseParser` 负责解析模型输出中的工具调用，`AgentToolInvoker` 负责执行工具。每轮模型输出后，如果检测到工具调用，就执行工具并把 Observation 追加回上下文；如果没有工具调用且是最终回答，就结束。为了避免死循环，我设置了最大迭代次数，并记录每轮结构化日志。

### Q3：RAG 是怎么做的？

答：

RAG 主流程在 `EnhancedRagPipeline`。用户问题先经过查询分析，然后走混合检索：Milvus 做向量召回，全文检索做关键词召回，之后用 RRF 融合、重排、补父级上下文、压缩上下文，并生成引用信息。最后 `ReActRequestContextBuilder` 把检索上下文注入到 Agent prompt 中。

### Q4：模型怎么支持 Kimi、Qwen、DeepSeek？

答：

我没有为每个模型写独立业务逻辑，而是抽象了 `ModelConfig`。只要供应商兼容 OpenAI Chat Completions，就能通过 provider、baseUrl、modelName、apiKey 接入。`McpModelService` 统一做配额、重试、token 记录和日志，`ModelHttpClient` 负责 HTTP/SSE 调用。Kimi/Moonshot 这类 provider 做了默认 baseUrl 和模型名处理。

### Q5：怎么保证数据库安全？

答：

数据库访问不是模型直接连库，而是通过数据源工具服务。工具只暴露受控能力，例如获取 schema、预览数据、执行 SELECT。SQL 会经过策略校验，避免 UPDATE、DELETE、DROP 这类危险操作。同时管理数据源本身需要 ADMIN 权限。

### Q6：如果 Milvus 挂了怎么办？

答：

当前项目定位是强依赖向量检索的数据分析平台，所以启动时会检查 Milvus 可用性。运行时向量写入和查询有异常捕获、collection 刷新和重试逻辑。对 RAG 查询来说，如果检索失败，`ReActRequestContextBuilder` 会回退到无上下文模式，并记录告警，保证主流程不会因为一次检索失败直接崩掉。

### Q7：为什么要做 Orchestrator？

答：

单个 ReAct 适合通用任务，但复杂数据分析经常包含多个子目标，例如查数据、找知识、生成图表、写报告。Orchestrator 可以先做意图和复杂度分析，再匹配 AgentProfile 或拆成多个专家任务，通过共享上下文协作，最后整合结果。它让系统从“一个 Agent 做所有事”升级成“多个专家协作”。

### Q8：项目有哪些可以继续优化的地方？

答：

- 增加更严格的 SQL AST 解析，进一步保证数据源安全；
- 引入更强的 reranker 模型，提高 RAG 排序质量；
- 为 Agent 工具调用增加更细粒度的权限策略；
- 建立离线评测集，持续评估 RAG 命中率和答案质量；
- 对长任务增加任务队列和可恢复执行；
- 把模型调用错误分类做成前端可理解的配置诊断。

## 16. 三分钟讲法

如果只有三分钟，可以这么讲：

> 我做的是一个企业数据分析 Agent 平台，后端用 Spring Boot，前端用 React，MySQL 存业务数据，Milvus 存向量。用户可以上传文件、维护知识库、配置模型和数据源，然后通过 Agent 做数据分析。
>
> 核心链路是：请求进来后由 `DataAnalysisAgentImpl` 做校验和文件加载，再交给 `AgentRuntimeService` 路由。如果是复杂任务，先进入 `OrchestratorAgent` 做意图识别、复杂度判断和专家路由；如果没有匹配到专家，就回退到内置 `ReActAgent`。ReAct 会在最多 8 轮内循环执行“模型思考、工具调用、观察结果、最终回答”。
>
> 为了让回答基于真实资料，我做了 RAG 管道：问题先改写，再走 Milvus 向量检索和全文检索，RRF 融合后重排，补父级上下文，压缩后注入 prompt，并生成引用。为了让 Agent 能操作真实数据，我做了工具系统，包括知识搜索、文件分析、SQL 查询、图表生成、计算器等。
>
> 模型层不是写死一家供应商，而是抽象成 `ModelConfig`，通过 OpenAI-compatible API 接入 Qwen、DeepSeek、Kimi/Moonshot 等模型。安全上使用 JWT、RBAC、管理员初始化、租户隔离和 SQL 只读策略。可观测性上记录模型调用、ReAct 每轮迭代、工具调用、RAG trace、用户反馈和审计日志。整体目标是让 Agent 不仅能回答，还能可控、可追踪、可治理。

## 17. 一分钟极简版

> 这个项目是一个企业数据分析 Agent 平台。它不是简单调模型，而是有完整的 Agent Runtime：请求进来后会加载会话、文件、RAG 和记忆上下文，再由 Orchestrator 判断是否需要多专家协作，最后通过 ReAct 循环调用知识库、文件、SQL、计算和图表等工具完成分析。模型接入是 OpenAI-compatible 的配置化设计，支持 Kimi、Qwen、DeepSeek 等供应商。安全上有 JWT、RBAC、首个管理员初始化、租户隔离和 SQL 只读限制；可观测性上有执行轨迹、结构化日志、RAG 健康和反馈质量看板。
