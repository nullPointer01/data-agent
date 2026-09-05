## Context

当前 `EmbeddingGateway` 根据 `app.embedding.provider` 在 JVM 内嵌 `AllMiniLmL6V2EmbeddingModel` 和 `OpenAiEmbeddingModel` 之间选择，默认走本地 384 维模型。Milvus 已经是唯一向量存储，Elasticsearch 已经是唯一 BM25 全文索引，因此内嵌模型只承担推理，不是存储兜底。

本变更把 Embedding 推理从 Java 业务进程剥离为独立 HTTP 服务，同时保留现有 `EmbeddingProfile`、启动维度探测、向量归一化、版本化 Collection 和混合检索。用户明确要求不新增/运行测试、不编译，设计必须支持纯静态一致性检查，并把真实连通性留到后续运行阶段。

## Goals / Non-Goals

**Goals:**

- 生产代码只有一个 OpenAI-compatible Embedding 调用路径。
- 配置缺失或实际维度冲突时快速失败，不允许写入错误向量。
- Java 服务不再加载本地模型权重，删除对应依赖。
- Milvus 与 Elasticsearch 的职责和 RRF 输入保持不变。
- 模型切换通过新 Collection 隔离，旧 Collection 在新索引验证前不被破坏，验证后允许受控下线。

**Non-Goals:**

- 不决定具体模型厂商或部署拓扑。
- 不实现模型服务本身、批量 Embedding、缓存或异步队列。
- 不由应用运行时代码自动迁移、删除或切换旧 Milvus Collection；迁移后的精确目标清理由运维步骤执行。
- 不改变 Chunk、BM25、RRF、重排或 Agent Runtime。

## Decisions

### D1. 使用单一 API Gateway，不保留 Provider 开关

`EmbeddingGateway` 始终构造 `OpenAiEmbeddingModel`，`EmbeddingProfile.provider` 固定为 `api`。删除 `EmbeddingProperties.provider`、`EMBEDDING_PROVIDER`、`LOCAL_PROVIDER` 和本地模型分支。

保留 provider 配置但只接受 `api` 会产生没有实际选择能力的伪开关，因此直接删除选择项；Profile 中固定的 `api` 仍用于索引身份和报告兼容。

### D2. 核心服务参数必须显式配置

`baseUrl`、`modelName` 和 `dimension` 不提供能够误启动的业务默认值。Gateway 构造阶段逐项校验非空/正数，并在异常中返回配置字段名。`apiKey` 允许为空，以支持企业内网的无鉴权 OpenAI-compatible 服务；客户端内部仍使用非敏感占位值满足 SDK 契约。

`indexVersion`、`normalize`、`metric` 和请求超时属于客户端策略，可以保留明确默认值。任何密钥不得进入 Profile、日志或异常。

### D3. 保留真实维度探测与逐次校验

应用启动时继续发送固定探测文本，校验服务实际输出维度；每次 `embed` 后再次经过 `EmbeddingCompatibilityValidator`。启动探测失败会阻止依赖它的 Milvus Gateway 初始化，不提供本地回退。

只依赖静态配置而不探测会把错误推迟到首个业务写入，因此不采用。

### D4. 新旧模型使用不同物理 Collection

Collection 名继续由基础名称、固定 provider `api`、模型、维度、Metric 和 `indexVersion` 组成。运维切换时必须提升 `EMBEDDING_INDEX_VERSION` 并重新索引知识和文件。

旧的 `__local__...` Collection 不由应用自动删除。新 API Collection 完成重建并确认可用后，运维步骤先读取完整 Collection 清单，再按精确名称删除已确认属于旧 Profile 的目标。自动迁移既无法把旧向量转换成新模型空间，运行时代码自动删库也会引入不可恢复的数据操作，因此两者都拒绝。

### D5. Elasticsearch 继续作为必要检索通道

Embedding 服务化只改变向量产生方式。Milvus 继续负责语义召回，Elasticsearch 继续负责编号、错误码、专有名词和关键词的 BM25 召回，两路仍由 RRF 融合。

只保留 Milvus 会降低精确词召回能力，也破坏现有分阶段评测契约，因此不采用。

## Risks / Trade-offs

- **外部服务不可用会阻止应用启动** → 通过明确健康语义、请求超时和无静默回退暴露真实依赖；生产部署必须先保证服务就绪。
- **模型切换后旧知识不可直接搜索** → 使用新 indexVersion 全量重建，完成前不删除旧 Collection。
- **删除旧 Collection 后无法直接回滚旧向量** → 把删除放在重建、连通性和召回验证之后；删除前记录精确目标，删除后回滚必须重新生成旧模型向量。
- **API 调用增加网络延迟和费用** → 本 Change 先建立统一边界；批量化、缓存和成本治理在有真实指标后单独设计。
- **不同厂商的 OpenAI 兼容程度不一致** → 当前只承诺标准 Embeddings 请求/响应；非兼容协议通过后续 Provider Adapter 扩展，不在 Gateway 中加入厂商分支。
- **无自动化测试降低回归保障** → 按用户约束执行静态引用、配置和文档一致性检查；不把未运行的连通性写成完成事实。

## Migration Plan

1. 部署或选择一个 OpenAI-compatible Embedding 服务，确认 Base URL、模型名和实际维度。
2. 配置新的 `EMBEDDING_API_BASE_URL`、`EMBEDDING_MODEL_NAME`、`EMBEDDING_DIMENSION` 和密钥。
3. 将 `EMBEDDING_INDEX_VERSION` 从当前版本提升到新版本，例如 `v2`。
4. 部署 API-only 应用，启动探测确认维度一致。
5. 对知识库和文件执行全量重建，写入新的 `__api__...` Collection 和现有 ES 索引。
6. 使用固定黄金集验证 Vector、BM25、RRF 和重排结果。
7. 读取 Milvus Collection 清单，逐个核对旧 Profile 的精确 `__local__...` 名称。
8. 删除已确认的旧 local Collection，并记录删除目标；禁止通配删除和删除任何 `__api__` Collection。

删除前回滚时可以恢复旧应用配置并重新使用旧 `local` Collection。删除后若要回滚，必须重新部署旧模型并全量生成旧向量；因此删除属于迁移完成后的不可逆清理步骤，不由应用自动执行。

## Open Questions

- 实际使用哪个 Embedding 服务、模型和维度，由部署前配置决定。
- 是否需要对 Embedding API 增加专用重试、熔断、批量和缓存，将基于真实流量与失败数据决定。
