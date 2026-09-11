# Data Agent: Java Agent Harness

这是一个以个人 Agent 为参考应用的 Java Agent Harness：用户定义 Agent，发起任务，Runtime 在统一权限、预算、上下文和生命周期边界内执行，最终返回结果与可核验运行证据。项目重点不是再做一个聊天壳，而是展示如何把不稳定的模型行为放进可治理、可评测、可恢复的服务端运行时。

Agent 不按“数据/知识/报告”预先分类，而是由模型、System Prompt、Capabilities 和 Chat/ReAct/Orchestrated 运行模式组合出实际能力。

```text
定义 Agent -> 发起任务 -> Harness 路由与治理 -> 结果 + 证据 -> Outcome / Eval
```

## 技术栈

- Java 17
- Spring Boot 3.2
- Spring Security + JWT
- Spring Data JPA
- MySQL
- LangChain4j Core / OpenAI 1.19.0
- LangChain4j Milvus 1.19.0-beta29（Milvus 适配器在 1.19 发布线上的官方 beta 版本）
- Milvus 2.3
- React + Vite
- Apache POI / PDFBox
- 可选 Redis
- Elasticsearch 8

## 本地启动

后端构建、测试和运行统一使用 JDK 17。macOS 当前终端切换方式：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -version
```

Maven Enforcer 只接受 JDK 17；JDK 8、11 或 21 会在编译前失败。

默认 profile 是 `local`，配置文件是 `src/main/resources/application-local.yml`。

local 默认连接：

- MySQL：`localhost:3306/data_agent`
- 用户名：`root`
- 密码：`zym190457`
- Milvus：`localhost:19530`
- 应用端口：`8080`

先确保 MySQL 数据库存在：

```bash
mysql -uroot -pzym190457 -e "CREATE DATABASE IF NOT EXISTS data_agent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -uroot -pzym190457 -hlocalhost data_agent < sql/init.sql
```

启动外部依赖：

```bash
docker-compose up -d
```

启动应用：

```bash
mvn spring-boot:run
```

只调后端、跳过前端构建：

```bash
mvn -Dfrontend.skip=true spring-boot:run
```

指定端口：

```bash
mvn -Dfrontend.skip=true spring-boot:run \
  -Dspring-boot.run.arguments='--server.port=18080'
```

访问：

```text
http://localhost:8080
```

## Profile

- `application.yml`：公共配置使用可直接阅读的固定值，不包含环境变量占位符。
- `local`：默认本地环境，使用 MySQL 和 Milvus；`application-local.yml` 同样只写固定值，审批恢复和 `hotel_rate_sandbox` 演示工具默认开启，不需要在 IDEA 配置环境变量。
- `prod`：线上环境，`application-prod.yml` 当前与 local 使用相同的固定配置和功能开关，不需要额外维护环境变量。

## 外部依赖

根目录的 `docker-compose.yml` 提供：

- Milvus standalone：`19530`
- Attu：`8000`
- Redis：`6379`
- Elasticsearch：`9200`
- Milvus 所需 etcd / MinIO

当前应用启动时会校验 Milvus 可达。Milvus 和 Elasticsearch 是 RAG 混合检索依赖；Redis 是否必需取决于启用的记忆和缓存能力。Embedding 不在 Compose 中运行，local profile 默认调用硅基流动的外部 API。Elasticsearch 不可用时不会回退到数据库模糊检索。

## 配置方式

local 和 prod 都直接读取仓库内的 YAML 固定值，不要求在 IDEA Run Configuration 或部署环境中维护同名环境变量。启动线上 Profile：

```bash
java -jar data-agent.jar --spring.profiles.active=prod
```

当前 `application-prod.yml` 与 `application-local.yml` 使用相同的连接、密钥和功能开关，只适合当前个人项目部署。模型 Key 优先通过 Control Plane 的模型目录维护；`app.encryption.key` 会影响数据库敏感字段解密，不能随意更换。

## 管理员和权限

认证接口：

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/bootstrap-admin`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`

RBAC 相关表：

- `sys_user`
- `sys_role`
- `sys_permission`
- `sys_user_role`
- `sys_role_permission`

默认初始化会确保 `USER`、`ADMIN`、`app:use`、`*:*` 存在。`BOOTSTRAP_ADMIN_USERS` 指定的已有用户会自动补管理员角色，local 默认是 `super`。

如果系统还没有任何管理员，可以调用 `/api/v1/auth/bootstrap-admin` 创建第一个管理员。系统不提供用户自助提权入口；已有管理员通过管理控制台的“用户”页面为账号分配角色。旧环境确认不再需要申请历史后，可手动执行 `sql/drop-admin-role-request.sql` 清理旧表。

## 模型配置

模型配置落表到 `model_config`，前端“模型”页面可以维护。系统按租户管理模型，并保证每个租户最多一个默认模型。服务端厂商目录是配置页唯一模板来源，当前包含 OpenAI/GPT、主流国产厂商、聚合平台和自定义 GPT/OpenAI-compatible 端点。

统一接入链路：

```text
ModelProviderCatalog
        -> ModelEndpointResolver
        -> ModelClientFactory
        -> Runtime Registry / Connection Probe
```

厂商目录提供默认端点、推荐模型、鉴权要求和模型发现能力。`GET /models` 只是辅助发现：部分兼容端点不实现该接口，失败时仍可手动填写模型 ID、测试连接并保存。保存前可调用 `POST /api/v1/models/probe` 做一次真实 Chat 探测；探测最大输出 32 token、没有 SDK 或业务重试、不进入正式客户端缓存，也不会返回或持久化模型正文和 API Key。真实探测可能产生少量调用费用。

当前覆盖的是 OpenAI-compatible Chat 基线，不承诺不同厂商的 Tool Calling、JSON Schema、多模态和推理参数完全一致，也不包含 Claude Native、Bedrock、Vertex AI、华为盘古等专有协议。Custom 可以连接 OpenAI 官方、GPT 代理网关和无 Key 的自托管兼容服务，其他目录项要求 API Key。私网地址默认会被 `OutboundUrlGuard` 拦截，仅在可信部署环境显式配置：

```yaml
app:
  security:
    outbound:
      allow-private-network: true
```

运行时、发现和探测共享同一个端点解析与 SSRF 校验。同步 LangChain4j 客户端关闭 SDK 内置重试，正式调用失败后的业务重试仍由 `ModelRetryExecutor` 控制。

## 前端功能

前端源码在 `frontend/src`。普通用户 Reference App 包括：

- 我的 Agent
- 知识
- 上下文

管理员通过独立的“管理控制台”入口访问：

- 用户管理
- 角色权限
- 模型管理
- 技能管理
- Agent 模板
- 数据源与连接
- Agent 动作审批
- RAG 调试
- 统计
- 质量
- 执行追踪
- 反馈
- 审计

前端构建：

```bash
cd frontend
npm run build
```

构建产物输出到 `src/main/resources/static`，由 Spring Boot 托管。Maven 默认会在 `generate-resources` 阶段执行 `npm install` 和 `npm run build`，可用 `-Dfrontend.skip=true` 跳过。

## 后端模块

主要包结构：

```text
com.ai.controller       REST 接口
com.ai.security         安全上下文、用户、权限基础类
com.ai.security.auth    登录、JWT、SecurityFilterChain
com.ai.security.rbac    角色和权限管理
com.ai.agent            Agent 主流程
com.ai.agent.react      ReAct 循环
com.ai.agent.orchestrator ReAct 执行计划与并行预检
com.ai.agent.runtime     统一 Run、路由与执行策略
com.ai.agent.tool       Agent 工具
com.ai.skill            动态技能
com.ai.mcp              模型调用、缓存、重试、token 记录
com.ai.memory           记忆系统
com.ai.rag              RAG 检索
com.ai.vector           Embedding 与 Milvus
com.ai.service          应用服务
com.ai.repository       JPA Repository
```

## Agent Run Harness

入口：

- `POST /api/v1/analysis/analyze`
- `POST /api/v1/analysis/analyze/stream`

每次被接受的分析请求都会创建唯一 `runId`。`AgentRunCoordinator` 统一负责路线解析、状态机、deadline、模型/工具/Token 预算、SSE 事件、取消、会话记录和 Trace；同步与流式请求使用相同路线。

路由顺序：

1. 斜杠命令进入 Skill 命令执行。
2. 指定 `agentId` 时走自定义 Agent。
3. 指定 `skillId` 时走动态技能。
4. 未显式指定能力时，加载当前用户的默认个人 Agent。
5. 个人 Agent 的 AUTO 模式对简单问题走 Chat；复杂任务存在有效子 Agent 时走 Orchestrated，否则走 ReAct。显式 Chat、ReAct 和 Orchestrated 不会在执行层被折叠成其他模式。

普通 Run 状态为 `CREATED -> RUNNING -> COMPLETED | FAILED | CANCELLED | TIMED_OUT | BUDGET_EXHAUSTED`。启用持久化审批后，ReAct 还允许 `RUNNING -> WAITING_APPROVAL -> RESUMING`，拒绝和过期分别进入 `REJECTED`、`EXPIRED` 终态，恢复后可以再次等待或进入普通终态。每次状态变化都通过 MySQL 的期望状态、版本和租约条件更新竞争唯一结果。

人工等待不占用 active execution timeout：暂停时冻结剩余时间和模型、工具、迭代、Token 预算，恢复时延续而不扩容。审批 TTL 与 active timeout 分离，默认分别为 24 小时和原 Run 剩余时间；恢复 worker 默认领取 60 秒租约，每 15 秒扫描/续租。候选恢复提交到独立有界线程池，避免模型调用占住调度线程导致续租停摆；续租失败会取消当前节点的 Run。上述值只是保护性起点，需按真实 P95/P99 调整。

SSE 首个 Agent 事件为 `run_started`，审批时增加 `approval_required`，随后发送 `businessCompleted=false` 的 transport `done` 并关闭连接。审批后不复用旧 SSE；调用方通过 `GET /api/v1/agent-runs/{runId}` 查询恢复状态。管理员在“动作审批”页处理同租户审批，申请人不能审批自己的动作，恢复前还会重新校验双方当前权限、Tool Registry、Agent allowlist 与风险策略。

审批演示工具 `updateHotelPrice` 只写 `hotel_rate_sandbox`，local/prod 默认开启。只有同时开启 durable、sandbox flag，并将工具列入审批工具集合时才会暴露。`approvalId + toolCallId` 是沙箱唯一动作键，能证明本地重复恢复不重复写入；数据库租约本身不能保证外部系统 exactly-once，真实写连接器仍需下游幂等、业务唯一约束或补偿。

内存 Registry 仍只负责当前节点在途执行和快速取消，MySQL Run Store 才是启用 durable 后的权威状态。`APP_ENCRYPTION_KEY` 用于 Checkpoint 与原始工具请求的 AES-GCM 密文；直接更换单密钥会使等待中的 Run 无法恢复，生产轮换必须先设计多版本密钥迁移。完整设计见 [Agent 运行时与 Harness](docs/核心逻辑详解/Agent运行时与Harness.md)。

所有模型工具调用还会经过服务端 Tool Governance：Registry 元数据、Run RBAC 快照、当前 Agent 白名单和风险策略共同决定是否允许执行；随后统一应用参数 Schema 校验、预算、timeout、安全重试、脱敏、截断、Journal、事件与指标。模型只能建议调用，不能授予自己权限。详细合同见 [Agent 工具治理](docs/核心逻辑详解/Agent工具治理.md)。

Tool、Skill 和子 Agent 会投影为统一 Capability Descriptor，包含稳定身份、版本、输入/输出合同、租户/所有者、权限、风险、生命周期和运行时可用性。Profile 只使用 `tool:<name>`、`skill:<skillId>`、`agent:<agentId>` 组成的 `capabilityBindings`，可以保存 0 至 64 项，显式 `[]` 表示零能力。默认个人 Agent 首次创建时固化当时可见、可绑定、可用的 Tool 快照，后续新增 Tool 不会自动扩大已有 Agent 权限。旧库必须先运行能力审计和迁移，再运行收口脚本删除旧列。

个人 Agent 设置可以选择三类能力和 `Auto / Chat / ReAct / Orchestrated` 四种模式。Chat 不开放可调用能力；ReAct 使用 Tool 和 Skill；Orchestrated 额外允许委派已绑定子 Agent；Auto 根据任务复杂度和当前有效子 Agent 决定路线。Skill 和委派通过 Harness 内部适配 Tool 进入同一治理管道，仍消耗根 Run 的 Tool、模型、Token 与 deadline 预算。普通用户只读取权限过滤后的目录，保存与每次执行都会在服务端重新校验；Capability Registry 本身不持有执行器，MCP 或未来 Provider 也不能通过目录绕过 Tool Pipeline。

子 Agent 使用自己的模型、System Prompt 和能力快照，但继承同一个根 Run；运行时最多 3 层并拒绝活动路径环路。当前仍不是拖拽式 DAG 平台，也没有跨 Run 消息总线。组合执行已完成静态调用链与前端 fixture 验证，真实模型下的嵌套委派仍需在重启后的后端运行验收。

四条可重复演示路径见 [Agent Harness 求职演示手册](docs/interview/AGENT_HARNESS_DEMO.md)：知识证据、工具审批与恢复、预算终止与配置恢复、组合能力与模式边界。

## RAG、知识库和文件

知识库和文件内容以 MySQL 为事实源，同时写入 Milvus 向量索引和 Elasticsearch Chunk 全文索引。个人 Agent 的两条召回通道、父级上下文回查和资源管理接口都同时按 `tenantId + userId` 隔离；管理控制台的租户级视图不复用普通用户接口。RAG 全文召回固定使用 Elasticsearch BM25：

```bash
RAG_ELASTICSEARCH_BASE_URL=http://localhost:9200
RAG_ELASTICSEARCH_INDEX=data-agent-rag-v2
```

`data-agent-rag-v2` 使用显式 Mapping：租户、来源和 Chunk 标识为 `keyword`，正文、标题和章节为 `text`。旧的动态 Mapping 索引不会被自动删除，需要显式重建数据。

RRF 融合后的候选默认通过外部 Cross-Encoder 精排。首个配置使用硅基流动兼容接口和 `BAAI/bge-reranker-v2-m3`；真实密钥只放在本地 Shell 环境或部署平台 Secret：

```bash
RERANK_ENABLED=true
RERANK_API_BASE_URL=https://api.siliconflow.cn/v1/rerank
RERANK_API_KEY=your_siliconflow_api_key
RERANK_MODEL_NAME=BAAI/bge-reranker-v2-m3
RERANK_API_TIMEOUT=5s
RERANK_MAX_CANDIDATES=20
RERANK_MAX_DOCUMENT_CHARS=4000
RERANK_FAIL_OPEN=true
RERANK_MAX_ATTEMPTS=2
RERANK_INITIAL_BACKOFF=200ms
RERANK_CIRCUIT_FAILURE_THRESHOLD=5
RERANK_CIRCUIT_OPEN_DURATION=30s
```

Provider 返回的索引必须完整、唯一且不越界，相关性分数必须为有限值。外部服务异常时，`fail-open` 使用确定性规则重排继续请求，`fail-closed` 则直接失败；Trace 会记录实际模型、候选数、耗时、错误类别和 fallback，不记录查询或 Chunk 正文。

管理员可以使用固定黄金集运行分阶段检索评测：

```bash
RAG_BENCHMARK_DATASET_PATH=./config/rag-golden-dataset.json
RAG_BENCHMARK_MINIMUM_CASES=30
RAG_BENCHMARK_MAXIMUM_CASES=200
```

黄金集由真实业务标注提供，仓库不会内置虚假的30条数据。JSON结构如下：

```json
{
  "datasetId": "knowledge-v1-eval",
  "corpusVersion": "knowledge-v1",
  "cases": [
    {
      "caseId": "rag-001",
      "query": "退款审批需要哪些材料？",
      "expectedSourceIds": ["真实来源编号"],
      "expectedChunkIds": ["真实分块编号"],
      "relevanceGrades": {"真实分块编号": 2},
      "tags": ["流程", "同义改写"]
    }
  ]
}
```

数据集至少需要30条、`caseId`必须唯一，每条必须具有 query 以及 sourceId 或 chunkId 标注。`relevanceGrades` 中 `2` 表示直接回答问题的主片段，`1` 表示辅助片段。配置完成后，管理员调用 `POST /api/v1/rag/benchmark/run`，报告会同时输出 Vector、BM25、RRF 和当前 Reranker 四个阶段的 Recall@5/10/20、MRR@10、NDCG@10、Hit@6、P95 以及逐案排名，并记录配置的 Provider/模型和每条用例的 fallback 证据。接口实现本身不代表质量达标，只有相同 RRF 候选上的规则/Cross-Encoder 同集报告才能作为指标结论。

文件上传后会进入异步处理队列，解析文本后写入文件表和知识/向量索引。支持 Office、PDF、普通文本等解析能力。

## 向量和 Embedding

Embedding 统一通过外部 OpenAI-compatible 服务生成，不在 JVM 或本地 Compose 中加载模型。local profile 默认使用硅基流动 `BAAI/bge-m3`；真实 API Key 只从运行环境读取。自建且无鉴权的内网服务可以不配置 Key：

```bash
EMBEDDING_API_BASE_URL=https://api.siliconflow.cn/v1
EMBEDDING_API_KEY=your_siliconflow_api_key
EMBEDDING_MODEL_NAME=BAAI/bge-m3
EMBEDDING_API_TIMEOUT=30s
# 仅服务支持 dimensions 参数且需要请求裁剪时设置；否则留空。
EMBEDDING_API_OUTPUT_DIMENSIONS=
EMBEDDING_API_BATCH_SIZE=32
EMBEDDING_API_MAX_ATTEMPTS=3
EMBEDDING_API_INITIAL_BACKOFF=200ms
EMBEDDING_API_CIRCUIT_FAILURE_THRESHOLD=5
EMBEDDING_API_CIRCUIT_OPEN_DURATION=30s
EMBEDDING_QUERY_PREFIX=
EMBEDDING_DOCUMENT_PREFIX=
EMBEDDING_DIMENSION=1024
EMBEDDING_INDEX_VERSION=bge-m3-v1
EMBEDDING_NORMALIZE=true
EMBEDDING_METRIC=COSINE
```

`EMBEDDING_DIMENSION` 是 Profile 和 Milvus Schema 始终使用的期望返回维度；可选的 `EMBEDDING_API_OUTPUT_DIMENSIONS` 是发送给兼容服务的请求参数。后者留空时不会发送 `dimensions` 字段，配置时必须为正数且等于期望维度。应用启动时会真实调用一次 Embedding 服务校验输出维度，每次向量化后也会重复校验。服务不可用、响应非法或维度不一致都会明确失败，不会回退到本地模型。

知识库和文件按 `EMBEDDING_API_BATCH_SIZE` 串行批量生成向量并批量写入 Milvus，合法范围为 `1..128`；查询仍保持单条低延迟调用。一次批量响应必须与输入数量完全一致，且所有向量均通过非空和维度校验后才会写入 Milvus。逻辑调用最大尝试次数范围为 `1..5`，仅 HTTP 429、5xx 和明确的瞬时连接/超时异常会指数退避重试；参数、鉴权、路径和向量契约错误立即失败。连续逻辑调用失败达到阈值后，当前 JVM 内该 Profile 会在配置窗口内熔断，不会切换模型。

`EMBEDDING_QUERY_PREFIX` 与 `EMBEDDING_DOCUMENT_PREFIX` 用于适配 E5 等非对称模型，默认留空。修改模型、期望/请求维度、Metric、归一化策略或任一输入前缀都会改变向量契约，必须提升 `EMBEDDING_INDEX_VERSION` 并全量重建，不能复用旧 Collection。示例中的 batch size `32`、timeout `30s`、最大尝试 `3`、熔断阈值 `5` 和窗口 `30s` 都是未实测起点，需要根据真实服务限额、429 比例、失败率和 P95 调整。

运行指标包括 `data_agent_embedding_calls_total`、`data_agent_embedding_retries_total`、`data_agent_embedding_circuit_rejections_total`、`data_agent_embedding_call_duration` 和 `data_agent_embedding_batch_size`。标签只包含固定 operation/outcome 和规范化 modelId，不记录 API Key、完整输入、Chunk 正文或向量数组。

Embedding 服务只负责推理并返回向量；Milvus 负责向量存储和语义召回；Elasticsearch 负责 BM25 关键词召回，两路候选由 RRF 融合。Milvus 的物理 Collection 名由基础名称和固定 `api + modelId + dimension + metric + indexVersion` 自动生成。新 API Collection 验证前不得删除旧 Collection。`BAAI/bge-m3` 在当前配置中的期望维度是 `1024`，应用启动时仍会以实际服务输出做最终校验。

## 记忆系统

记忆表包括：

- `memory_entry`
- `user_profile`

支持工作记忆、短期记忆、长期记忆、用户画像、压缩、保留、衰减和晋升策略。模型压缩默认关闭：

```bash
MEMORY_MODEL_COMPRESSION_ENABLED=false
```

## 启动初始化

`DataInitializer` 做以下事情：

- 修复旧库中缺失的用户 token 配额字段
- 初始化 RBAC 角色和权限
- 迁移旧 `sys_user_roles` 到 `sys_user_role`
- 给 `BOOTSTRAP_ADMIN_USERS` 指定用户补管理员角色
- 初始化默认模型
- 初始化默认技能
- 把启用技能加载到运行时内存注册表

启动阶段不会刷新 Milvus 技能向量索引。技能创建、更新、启用、删除时才触发向量索引刷新或清理。

## 常用验证

```bash
mvn -Dfrontend.skip=true validate

cd frontend
npm run build
```

不要并发执行多个 Maven 命令写同一个 `target/`，否则资源复制可能互相冲突。

构建应用镜像时，构建阶段和运行阶段都固定为 Java 17：

```bash
docker build -t data-agent:local .
```

## 常见问题

### No static resource favicon.ico

这是浏览器自动请求 `/favicon.ico`。项目已通过 `FaviconController` 返回 `204 No Content`，避免缺失静态资源异常污染日志。

### Milvus collection not loaded / channel not found

通常是 Milvus collection load 或 standalone 状态不稳定。当前网关会对部分可恢复状态刷新 collection 后重试一次。启动阶段不会再重建技能向量，避免把该问题放大成启动故障。

### Kimi 401

优先检查 API Key 类型和 Base URL 是否匹配：

- Moonshot Key 用 `https://api.moonshot.cn/v1`
- Kimi Coding Key 用 `https://api.kimi.com/coding/v1`

### Spring Security generated password

当前已提供数据库用户版 `SysUserDetailsService`。如果再次出现该警告，检查是否有安全配置类未生效或包扫描异常。

## 开发约定

- Java 使用 JDK 17。
- 新增 public 类和 public 方法要有 Javadoc。
- 涉及安全、租户、异步、缓存、模型、向量、重试、熔断的代码要说明设计原因。
- Repository 查询必须考虑 `tenant_id`。
- 管理端接口需要 ADMIN 权限。
- 不要把真实密钥写进仓库。
- 不要在启动阶段引入重型外部写入。
