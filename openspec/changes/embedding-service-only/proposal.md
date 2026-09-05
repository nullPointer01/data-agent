## Why

当前应用同时支持 JVM 内嵌 `AllMiniLmL6V2` 与外部 Embedding API，默认值仍会选择本地 384 维模型。这使模型推理与 Java 业务进程耦合，也容易让“本地模型”和“本地向量库”概念混淆；项目已经明确使用 Milvus 作为唯一向量存储，应进一步将 Embedding 推理统一为独立服务。

## Objective

将 Embedding 从“local/api 二选一”收敛为“仅调用 OpenAI-compatible Embedding 服务”，继续使用 Milvus 存储向量、Elasticsearch 执行 BM25，并保留模型身份、维度探测和版本化 Collection 隔离。

## What Changes

- **BREAKING**：删除 JVM 内嵌 `AllMiniLmL6V2` 模型及其 Maven 依赖，不再接受 `EMBEDDING_PROVIDER=local`。
- **BREAKING**：删除 `EMBEDDING_PROVIDER` 选择项，`baseUrl`、`modelName` 与 `dimension` 改为必须显式配置；缺失时应用启动失败。
- `EmbeddingProfile.provider` 保留固定值 `api`，继续与模型、维度、Metric 和索引版本共同形成物理 Collection 身份。
- 保留启动阶段真实维度探测、每次向量化后的维度校验、归一化和版本化 Milvus Collection。
- 保留 Milvus 向量召回与 Elasticsearch BM25 全文召回，不修改 RRF、重排和黄金集评测流程。
- 更新环境模板、README 与项目知识文档，统一使用“Embedding 服务化”表述。
- 新服务必须提升 `EMBEDDING_INDEX_VERSION` 并显式重建数据；新 API Collection 验证可用后，按精确名称受控删除旧 `local` Collection。

## Anti-Scope

- 不选择或绑定特定云厂商，不写入真实 API Key。
- 不部署 BGE、Ollama、vLLM 或其他模型服务，只定义客户端契约。
- 不在应用启动或业务请求中自动删除 Collection，不使用前缀、模糊匹配或通配方式批量删除。
- 不在本 Change 中自动执行数据重建；真实服务配置和重建仍属于后续运行步骤。
- 不删除 Elasticsearch，不修改 BM25、RRF 或 Reranker。
- 不在本 Change 中重构 Agent Harness。

## Quantified Success Criteria

- **SC-1 Local Removal**：生产源码、POM、运行配置和主要文档中 0 处保留 `AllMiniLmL6V2` 或可选 `local` Embedding 路径。
- **SC-2 Required Contract**：`baseUrl`、`modelName` 或 `dimension` 任一缺失/非法时，100% 在创建 Milvus 写入前抛出包含字段名的配置异常。
- **SC-3 API-only Gateway**：所有文本和 TextSegment 向量化调用 100% 经过同一个 OpenAI-compatible Embedding 客户端及兼容性校验。
- **SC-4 Index Isolation**：新 Profile 的 provider 固定为 `api`；Collection 身份继续包含模型、维度、Metric 和 indexVersion，不复用旧 `local` Collection。
- **SC-5 Retrieval Boundary**：Milvus 与 Elasticsearch 两条检索链路、租户过滤和 RRF 输入保持不变。
- **SC-6 Engineering Gate**：遵循用户决定，不新增/运行自动化测试，不执行编译；通过依赖、调用点、配置、文档和旧路径的静态一致性检查，未真实运行的可用性与质量指标不得声称已达成。
- **SC-7 Controlled Cleanup**：删除前100%列出并核对精确 Collection 名；只有名称包含已确认旧 Profile 的 `__local__` Collection 可删除，0个 `__api__` 或其他 Collection 被删除；删除时间晚于新 API Collection 重建与可用性确认。

## Capabilities

### New Capabilities

- `embedding-service`: 定义外部 OpenAI-compatible Embedding 服务的必填配置、启动探测、向量身份和失效行为。

### Modified Capabilities

无。仓库当前没有已归档的基础 capability spec；现有 RAG 变更继续独立维护。

## Assumptions

具体假设与验证方式见 [sdd-assumptions.md](./sdd-assumptions.md)。

## Boundaries

### Always

- Embedding 服务输出必须经过实际维度校验后才能写入 Milvus。
- 模型、维度、Metric 或向量处理策略变化时必须提升索引版本并重建。
- Embedding API Key 只从外部配置读取，不写入日志、Trace 或仓库。
- 删除前读取并记录精确 Collection 名，只处理已确认属于旧 `local` Profile 的目标。

### Ask First

- 指定真实 Embedding 厂商、模型、Base URL、维度或密钥。
- 重建现有知识索引或改变已确认的旧 `local` Collection 目标清单。
- 修改 Milvus 索引类型、Metric 或 Elasticsearch Mapping。

### Never

- 不允许 Embedding 服务不可用时静默回退本地模型。
- 不允许维度不匹配的向量写入 Milvus。
- 不允许把旧 `local` 向量复制到新模型 Collection。
- 不允许在新 API Collection 完成重建和可用性确认前删除旧 Collection。
- 不允许删除任何未被精确确认的 `__api__`、非 `local` 或共享 Collection。

## Test Strategy

按用户已确认的项目约束，本 Change 不新增或运行自动化测试，也不执行 Maven 编译。实现阶段只做静态一致性检查；真实连通性、维度与检索质量留到外部 Embedding 服务配置完成后的运行验证阶段。

## Impact

- 后端依赖：删除 `langchain4j-embeddings-all-minilm-l6-v2`。
- 后端代码：`EmbeddingGateway`、`EmbeddingProperties` 及相关 Profile 装配。
- 配置：`application.yml`、`.env.example` 中的 Embedding 环境变量。
- 文档：根 README、后端 README、`CLAUDE.md`、RAG 核心逻辑与常见陷阱。
- 运维：应用启动新增外部 Embedding 服务强依赖；切换后需要使用新 indexVersion 重建 Milvus 向量，并在验证完成后受控删除旧 local Collection。
