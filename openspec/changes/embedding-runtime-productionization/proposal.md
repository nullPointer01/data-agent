## Why

`embedding-service-only` 已把 JVM 内嵌模型收敛为单一外部 API，但当前每个 Chunk 仍单独同步请求，且 Embedding 调用没有独立的超时、分类重试、熔断、query/document 输入契约和运行指标。真实知识库重建前需要先补齐这些客户端能力，否则网络抖动、限流和模型格式差异会直接放大为慢写入、重复费用或不可解释的召回失败。

## Objective

把现有 `EmbeddingGateway` 提升为可批量、可治理、可观测的外部 Embedding 运行时，同时保持 Milvus、Elasticsearch、RRF 和 Agent Runtime 的职责边界不变。

## What Changes

- 增加文档 Chunk 批量 Embedding，并保证请求与返回顺序、向量维度和对应元数据一致。
- 增加显式请求超时、最大尝试次数、退避时间、熔断阈值和熔断窗口配置。
- 只对 HTTP 429、5xx 和明确的瞬时网络错误重试；400、401、403、404、维度冲突和无效响应立即失败。
- 增加 `query` 与 `document` 两种输入用途及可配置前缀，允许适配 E5 等非对称检索模型；默认前缀为空，不改变 BGE 等现有模型输入。
- 区分“期望返回维度”和“请求服务裁剪维度”：只有显式配置可选请求维度时才发送 `dimensions` 参数，避免不支持该扩展的兼容服务被误伤。
- 增加 Micrometer 调用次数、失败次数、重试次数、熔断拒绝、耗时和批大小指标，禁止记录 API Key、完整输入文本和向量内容。
- 索引写入从逐 Chunk HTTP 调用改为受控批次，查询仍使用单条低延迟调用。
- 保持启动真实维度探测和每次返回向量兼容性校验。

## Anti-Scope

- 不训练、微调或实现 Embedding 模型推理服务，不绑定具体云厂商、vLLM、TEI、Xinference 或 Ollama。
- 不实现 Embedding 缓存、异步消息队列、GPU 调度或自动模型选择；这些需要真实流量和成本数据后再设计。
- 不修改 Harness、Chat/ReAct/Orchestrated 路由，也不把 Embedding 改成 Harness 核心依赖。
- 不改变 Chunk 算法、Milvus 检索、Elasticsearch BM25、RRF、重排或黄金集指标定义。
- 不在本变更中删除历史 `__local__` Collection；该动作仍由 `embedding-service-only` 的受控迁移任务负责。

## Quantified Success Criteria

- **SC-1 Batch Contract**：文档索引使用可配置批大小，合法范围 `1..128`；每个批次返回数量必须与输入数量完全相等，且顺序一一对应，否则整个批次失败且不写入该批次向量。
- **SC-2 Timeout Contract**：Embedding 请求超时必须显式配置为正数，配置缺失或非法时初始化失败；禁止使用 SDK 隐式无限等待。
- **SC-3 Retry Contract**：单个逻辑调用的最大尝试次数可配置为 `1..5`；仅 429、5xx 和瞬时 I/O 错误可重试，400/401/403/404、维度错误和响应契约错误重试次数为 `0`。
- **SC-4 Circuit Contract**：连续失败达到可配置阈值后，在配置窗口内拒绝新调用；探测成功或窗口后成功调用恢复关闭状态，不静默切换模型。
- **SC-5 Input Contract**：查询和文档分别经过 `query-prefix`、`document-prefix` 处理；默认均为空，任何前缀变化要求提升 `EMBEDDING_INDEX_VERSION` 并重建向量。
- **SC-5A Dimension Request Contract**：期望维度始终用于返回校验；可选请求维度未配置时不发送 `dimensions`，配置时必须为正数且等于期望维度。
- **SC-6 Observability Contract**：每次调用记录 operation、outcome、modelId 的计数和耗时；批量调用额外记录批大小；指标和日志中 `0` 次出现 API Key、完整输入或向量数组。
- **SC-7 Retrieval Preservation**：静态调用链仍保留 Milvus vector candidates、Elasticsearch BM25 candidates 和 RRF fusion 三个阶段，生产代码中不新增本地模型回退。

## Assumptions

- 真实服务实现标准 OpenAI-compatible `/v1/embeddings` 请求/响应，并支持字符串或字符串数组输入。
- LangChain4j 0.30 的批量 Embedding 能力和异常类型将在实现前通过依赖 API 静态检查确认；不满足时使用受控的客户端适配层，而不是猜测 SDK 行为。
- 用户继续要求不新增/运行测试、不编译、不启动应用；本变更只执行依赖/API、控制流、配置、XML/YAML 和文档静态检查，真实服务指标保持未验证。
- `embedding-service-only` 的 API-only Profile 与版本化 Collection 设计继续作为前置契约。

## Boundaries

- **Always**：每个返回向量在写 Milvus 前校验数量、顺序映射、非空和维度；失败信息携带 operation/model/batch 上下文但不包含敏感内容。
- **Ask first**：引入新第三方容错库、改变现有模型调用重试器、修改 Agent Runtime 或选择具体 Embedding 厂商。
- **Never**：静默回退本地模型、对鉴权/参数错误重试、把不同输入契约的向量写进旧 Collection、记录密钥/完整文本/向量、自动删除 Collection。

## Capabilities

### New Capabilities

- `embedding-runtime`: 外部 Embedding 调用的批量处理、输入用途、超时重试熔断、兼容性校验和可观测性契约。

### Modified Capabilities

无。仓库当前没有已归档的持久化 capability spec；本变更依赖但不修改活跃 `embedding-service-only` 的 API-only 和 Collection 迁移契约。

## Impact

- 主要影响 `com.ai.vector` 的 Gateway、配置、批量索引入口和新增的 Embedding 调用策略组件。
- `VectorMemoryService` 的文件、知识、记忆分块写入将按批次提交，但其公开检索接口和 Milvus 元数据契约保持不变。
- `application.yml`、`.env.example`、README 和 RAG 运维文档增加生产化参数和失败语义。
- 不增加模型权重依赖，不修改前端、数据库 Schema、ES Mapping、Milvus Schema 或 Agent API。

## Test Strategy

用户明确要求不再新增或运行测试，也不编译项目，因此不创建测试任务，不声称覆盖率或运行时行为已验证。验证限定为静态 API 可用性核对、配置绑定检查、异常分类控制流审查、敏感信息搜索、结构化文件解析和 `git diff --check`；真实 API 连通性与性能在用户提供服务配置后单独执行。
