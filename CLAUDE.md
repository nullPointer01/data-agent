# CLAUDE.md

This file provides guidance to Claude Code when working with this repository.

## 项目定位

Data Agent 是一个数据分析智能体系统，后端基于 Spring Boot 3.2、Spring Security、JPA、LangChain4j、Milvus，前端基于 React + Vite。当前代码已经不是单一 ReAct Demo，而是包含：

- 智能对话与 SSE 输出
- ReAct 工具调用
- Orchestrator 多专家编排
- 自定义 Agent 配置
- 动态 Skill
- 文件解析与知识库
- RAG 检索
- 长短期记忆
- 模型配置与 Kimi/OpenAI 兼容接入
- RBAC、管理员申请、审计、质量反馈、执行追踪

Maven 工程、前端和 `docker-compose.yml` 都位于仓库根目录。

## 常用命令

所有 Maven 命令都在仓库根目录执行：

```bash
# 后端启动，默认 local profile
mvn spring-boot:run

# 后端启动但跳过前端构建，适合日常 Java 调试
mvn -Dfrontend.skip=true spring-boot:run

# 指定端口启动
mvn -Dfrontend.skip=true spring-boot:run \
  -Dspring-boot.run.arguments='--server.port=18080'

# 只校验 JDK 17 基线，不编译
mvn -Dfrontend.skip=true validate

# 前端单独构建
cd frontend
npm run build
```

外部依赖在仓库根目录启动：

```bash
docker-compose up -d
```

项目使用标准 Maven 命令运行。仓库根目录 `.java-version`、Maven Enforcer、CI 和应用 Dockerfile 均固定 JDK 17；JDK 8、11 或 21 会在 Maven `validate` 阶段失败。macOS 可执行 `export JAVA_HOME=$(/usr/libexec/java_home -v 17)` 切换当前终端。

## 当前运行依赖

local profile 默认配置在 `src/main/resources/application-local.yml`。

- MySQL 是业务主库，默认连接 `jdbc:mysql://localhost:3306/data_agent`，用户名 `root`。
- Milvus 是强依赖，默认 `localhost:19530`，collection 为 `data_agent_vectors`。应用启动会校验 Milvus 可达；向量写入/检索使用懒加载的 embedding store，避免启动阶段被 collection load 卡住。
- Redis 依赖存在，但 local profile 关闭 Redis repository 扫描；当前主链路不要求 Redis 必须先启动。
- Elasticsearch 是 RAG 唯一的 BM25/全文检索实现，默认地址 `localhost:9200`。不可用时健康检查明确失败，不回退到 SQL LIKE。
- 模型 API 需要在后台模型配置或环境变量中配置。local profile 里的默认 key 是占位值，只能用于启动，不能保证真实模型调用成功。

本地 MySQL 初始化：

```bash
mysql -uroot -pzym190457 -hlocalhost data_agent < sql/init.sql
```

## Profile 约定

- `local`：默认 profile，本地 MySQL + Milvus。适合 IDE 直接启动。
- `dev`：共享开发环境，数据库、Redis、Milvus、JWT、模型等通过环境变量注入。
- `prod`：生产环境，`JWT_SECRET`、`APP_ENCRYPTION_KEY`、数据库和模型凭据必须显式配置。

## 前端

前端源码在 `frontend/src`，Vite 构建产物输出到 `src/main/resources/static`，由 Spring Boot 托管。

当前导航功能包括：

- 工作台
- 智能对话
- 资料中心
- 记忆中心
- 知识库
- 文件
- 管理员申请
- 用户
- 角色权限
- 模型
- 技能
- Agent
- 数据源
- 统计
- 质量
- 追踪
- 反馈
- 审计

修改前端后需要跑：

```bash
cd frontend
npm run build
```

## 认证和 RBAC

认证接口在 `/api/v1/auth`：

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/bootstrap-admin`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`

RBAC 已经落表：

- `sys_user`
- `sys_role`
- `sys_permission`
- `sys_user_role`
- `sys_role_permission`

默认初始化会确保：

- `USER` 角色
- `ADMIN` 角色
- `app:use` 权限
- `*:*` 权限
- `BOOTSTRAP_ADMIN_USERS` 指定的已有用户自动补 ADMIN，local 默认是 `super`

如果系统里没有任何管理员，可以调用 `/api/v1/auth/bootstrap-admin` 创建或提权第一个管理员。已有管理员后，用户应走管理员申请流程，由管理员审核。

管理员相关接口被 `ADMIN` 角色保护。`/actuator/health` 和 `/actuator/info` 放行，其他 `/actuator/**` 需要 ADMIN。

## 模型接入

模型配置在 `model_config` 表，后台模型管理页面可维护。`ModelConfigService` 保证同一租户只有一个默认模型，变更后通过事件清理同步、JSON 和 Streaming 客户端缓存。

模型接入统一经过 `ModelProviderCatalog -> ModelEndpointResolver -> ModelClientFactory`：

- Catalog 是前后端唯一厂商目录，包含 OpenAI/GPT、13 个国内/聚合入口和 Custom，共 15 个入口。
- Resolver 负责厂商别名、默认值、URL 规范化、鉴权规则和 `OutboundUrlGuard`；运行时、`/models` 发现和连接探测不得自行拼端点。
- Factory 使用 LangChain4j 1.19 构建 OpenAI-compatible 同步、JSON 和 Streaming 客户端；同步 SDK 重试固定关闭，由 `ModelRetryExecutor` 管理业务重试。
- `ModelHttpClient` 只负责可选的 `GET /models`。发现失败不阻止手动填写模型 ID，也不作为保存门禁。
- `POST /api/v1/models/probe` 会真实执行一次最多 32 输出 token 的 Chat 请求，临时客户端不缓存、不重试、不保存提示词或回复；每次探测可能产生少量厂商费用。

OpenAI-compatible 只表示当前项目复用 Chat 基线协议，不代表各厂商的 Tool Calling、JSON Schema、多模态或推理参数完全一致，也不覆盖 Claude Native、Bedrock、Vertex AI、盘古等专有协议。Custom 可连接 OpenAI 官方、GPT 代理网关或其他兼容端点，并允许无鉴权的自托管服务不填 API Key；公网目录厂商按目录要求必须提供。私网端点仍受 SSRF 防护控制，仅在受控环境显式设置 `app.security.outbound.allow-private-network=true`。

## Agent 执行链路

主入口：

- `POST /api/v1/analysis/analyze`
- `POST /api/v1/analysis/analyze/stream`

`AgentRuntimeService` 是薄入口，核心治理在 `AgentRunCoordinator`。每个有效请求先由 `AgentRunRouteResolver` 解析一次路线，再创建唯一 `runId` 和 `AgentRunContext`：

1. 斜杠命令：`SkillManager.processWithCommand`
2. 指定 `agentId`：`MultiAgentRuntimeService`
3. 指定 `skillId`：`SkillExecutionService`
4. 默认优先 `OrchestratorAgent`
5. Orchestrator 不可用时回退 `ReActAgent`

同步和流式共享路线与生命周期。Run 使用 timeout、iteration、model-call、tool-call、Token 五类服务端预算；深层模型/工具通过 `AgentRunScope` 准入。SSE 由 `AnalysisStreamService` 协调，通过类型化 `AgentEventSink` 保留旧事件协议，并在断开、超时或显式停止时协作式取消节点内 Run。`runId` 同时作为现有 Trace 的 `traceId`。

每个 Run 创建时还会根据持久化用户、租户和启用 RBAC 权限固化工具授权快照，并创建有界 Tool Journal。模型提出的工具名和参数一律视为不可信；实际执行必须通过 Registry、Run 权限、当前 Agent 精确白名单和风险策略的交集。

`app.agent.durable.enabled` 默认关闭。开启后，ReAct 审批工具可以将 Run 暂停为 `WAITING_APPROVAL`，把版本化消息和剩余预算使用 AES-GCM 加密写入 MySQL；批准后由数据库租约 worker 领取为 `RESUMING`，重读当前双方 RBAC、Tool Registry、allowlist 与风险策略，再通过原 Tool Pipeline 执行并继续循环。人工等待不消耗 active timeout，审批 TTL 独立计算；旧 SSE 不回放，客户端通过 Run 查询接口获取恢复结果。

`updateHotelPrice` 是默认关闭的隔离演示工具，只写 `hotel_rate_sandbox`。它用 `approvalId + toolCallId` 唯一键防止本地重复写入，不代表真实酒店改价，也不证明跨系统 exactly-once。`APP_ENCRYPTION_KEY` 变化会让旧 Checkpoint 无法解密，生产密钥轮换必须使用后续的多版本密钥方案。

## ReAct 与工具

ReAct 相关代码在 `com.ai.agent.react`。工具不直接塞在 ReAct 主类中，而是在 `com.ai.agent.tool` 下拆分：

- `AgentTools`
- `AgentToolInvoker`
- `AgentToolDefinition`
- `AgentFileToolService`
- `AgentKnowledgeToolService`
- `AgentSkillToolService`
- `AgentDataSourceToolService`
- `AgentConversationToolService`
- `AgentChartToolService`
- `AgentUtilityToolService`

`com.ai.agent.tool.governance` 是唯一工具执行控制面。新增 `@Tool` 必须同时提供 `@AgentToolPolicy`，声明风险、只读、幂等、重试、timeout、权限和结果长度；禁止绕过 `AgentToolExecutionPipeline` 直接调用模型工具执行器。Profile 空工具列表仍兼容“服务端全部启用工具”，但执行期 RBAC 和风险策略仍会收窄。

工具的一次模型请求对应一个 `toolCallId`，内部每次真实 attempt 都消耗 Run 工具预算。只有只读、幂等、声明可重试且属于明确瞬时故障的工具允许有限重试。工具输出必须先统一脱敏和截断，再进入模型、SSE、日志和 Trace。详细语义见 `docs/核心逻辑详解/Agent工具治理.md`。

## Orchestrator 与专家

编排器相关代码在 `com.ai.agent.orchestrator`，专家在 `com.ai.agent.specialist`。当前有数据、知识、图表、报告、聊天、技能、ReAct 等专家类型。

配置位于 `app.orchestrator.*` 和 `app.agent.reasoning.*`。默认关闭 LLM 意图识别和 LLM 规划，优先使用确定性分类和规划，避免启动后必须依赖可用模型。

执行轨迹会写入 `agent_execution_trace`，质量和反馈相关数据写入 `agent_feedback` 等表。

## Skill 系统

Skill 配置存储在 `skill_config`。运行时由 `DataInitializer` 从数据库加载到 `SkillManager`。

重要约定：

- 启动时只注册内存 Skill，不刷新 Milvus 技能向量索引，避免向量库抖动影响应用启动。
- 创建、更新、启用 Skill 时才刷新向量索引。
- 删除或禁用 Skill 时会从运行时注册表移除并清理向量索引。
- `DynamicSkill` 支持 prompt template、steps、外部 API 调用、keywords 等配置。

## 文件、知识库和 RAG

文件上传、解析、入库、向量索引分属以下模块：

- `FileUploadController`
- `FileProcessingSubmissionService`
- `FileProcessingWorker`
- `FileParserService`
- `FileStorageService`
- `KnowledgeService`
- `KnowledgeVectorIndexService`

支持常见文本、Office、PDF、图片 OCR/解析扩展点。文件和知识库内容会进入 MySQL，同时由 Milvus 建向量索引。

RAG 相关代码在 `com.ai.rag`。全文召回固定使用 Elasticsearch BM25，向量召回使用 Milvus，两路通过 RRF 融合，再由外部 HTTP Cross-Encoder 对候选精排，最后执行父上下文解析、压缩和引用生成。Cross-Encoder 默认模型为 `BAAI/bge-reranker-v2-m3`；超时、限流、上游异常或响应契约错误时按 `RERANK_FAIL_OPEN` 决定是否降级到规则 Provider。真实黄金集对比尚未运行前，不得宣称模型精排已经提升指标。

## 记忆系统

记忆相关代码在 `com.ai.memory`，持久化表包括：

- `memory_entry`
- `user_profile`

记忆有 working、short-term、long-term 等层级，支持压缩、衰减、保留策略和用户画像刷新。模型压缩默认关闭：`MEMORY_MODEL_COMPRESSION_ENABLED=false`。

## 向量和 Embedding

向量代码在 `com.ai.vector`。

- Embedding 只通过外部 OpenAI-compatible 服务生成，JVM 不加载本地 Embedding 模型，也没有本地回退。
- `EMBEDDING_API_BASE_URL`、`EMBEDDING_MODEL_NAME` 和 `EMBEDDING_DIMENSION` 必须显式配置；内网无鉴权服务的 `EMBEDDING_API_KEY` 可以为空。
- `EMBEDDING_API_TIMEOUT` 必须为正数；文档批大小限定 `1..128`，最大逻辑尝试次数限定 `1..5`。
- `EMBEDDING_DIMENSION` 是 Profile 的期望返回维度；可选 `EMBEDDING_API_OUTPUT_DIMENSIONS` 仅在服务支持请求裁剪时发送，配置后必须等于期望维度。
- 查询和文档分别通过 `EMBEDDING_QUERY_PREFIX`、`EMBEDDING_DOCUMENT_PREFIX` 格式化；启动 probe 不使用业务前缀。
- `app.embedding.*` 形成固定 provider `api`、modelId、indexVersion、dimension、normalize、metric 的统一 Profile；启动时真实探测服务输出维度，每次向量化后再次校验。
- 知识和文件使用配置大小的串行批量 Embedding/Milvus 写入；查询仍是单条调用。批量响应数量或任一向量不兼容时，该批次在写 Milvus 前失败。
- Embedding 专用执行器仅重试 429、5xx 和明确瞬时网络异常，使用指数退避与 JVM 内 per-Profile 熔断；不会复用 Chat Model 重试器或切换模型。
- `milvus.*` 只保留连接、Collection 基础名称和索引类型；物理 Collection 名按 Embedding 身份自动版本化，维度和 Metric 只读取当前 Profile。
- `MilvusVectorStoreGateway` 启动阶段只初始化客户端和索引，`MilvusEmbeddingStore` 首次 add/search 时懒加载。

Embedding 服务负责生成向量，Milvus 负责向量存储和语义召回，Elasticsearch 负责 BM25 全文召回。修改模型、期望/请求维度、query/document 前缀、Metric、归一化或其他向量处理策略时必须提升 `EMBEDDING_INDEX_VERSION` 并全量重建，新旧 Collection 不会混写；新 API Collection 验证前不得删除旧 Collection。模板中的 timeout、batch、重试和熔断值均为未实测起点，不能宣称是最优配置。

## 数据库与租户

所有业务数据默认在 MySQL。新增查询必须考虑租户隔离，优先使用 `SecurityContextHelper.getCurrentTenantId()`。

常见租户字段：

- `tenant_id`
- `user_id`
- `created_by`

跨租户共享的默认数据通常使用 `tenant_id=default`。修改 list/detail 权限时要确认是否允许读取 default tenant 数据。

## 配置和密钥

环境变量入口定义在 `src/main/resources/application.yml`。生产或共享环境必须配置：

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`
- `APP_ENCRYPTION_KEY`
- `MILVUS_HOST`
- `MILVUS_PORT`
- 模型 API 相关变量或后台模型配置

`APP_ENCRYPTION_KEY` 用于数据库敏感字段加密。改这个值会影响已加密字段解密，不能随意轮换。

## 测试和验证

按当前项目决策，自动化开发 Agent 不主动运行单测、编译、打包、`verify` 或 Docker 构建；只有用户后续明确要求时才执行。供人工需要时使用的命令：

```bash
mvn -Dfrontend.skip=true validate
cd frontend && npm run build
```

不要并发跑多个 Maven 命令写同一个 `target/`，资源复制阶段可能互相踩文件。

## 开发注意事项

- 保持 JDK 17。
- 后端 public 类和 public 方法要写有意义的 Javadoc。
- 安全、租户隔离、异步、缓存、重试、熔断、向量索引、模型调用等逻辑要写简短原因注释。
- 不要在启动阶段做重型外部写入；启动初始化应尽量只做 schema/data repair 和内存注册。
- 不要在改模型配置后绕过 `ModelConfigService` 直接写缓存；否则模型缓存不会清理。
- 不要把 `/actuator/**` 全部放开，只放开 health/info。
- 不要把真实密钥提交到文档或配置模板。
- 不要假设 Milvus 中已有 collection；首次写入时可能由 LangChain4j 创建。
- 不要用手写字符串拼复杂 SQL 参数；Repository native query 要用命名参数，避免参数数量错位。
