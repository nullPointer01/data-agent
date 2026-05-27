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

Maven 工程在 `data-agent/` 子目录。仓库根目录包含 `docker-compose.yml`，用于启动 Milvus、Redis、Elasticsearch 等本地依赖。

## 常用命令

所有 Maven 命令在 `data-agent/` 子目录执行：

```bash
cd data-agent

# 后端启动，默认 local profile
mvn spring-boot:run

# 后端启动但跳过前端构建，适合日常 Java 调试
mvn -Dfrontend.skip=true spring-boot:run

# 指定端口启动
mvn -Dfrontend.skip=true spring-boot:run \
  -Dspring-boot.run.arguments='--server.port=18080'

# 编译和测试
mvn -Dfrontend.skip=true -DskipTests compile
mvn -Dfrontend.skip=true test

# 前端单独构建
cd frontend
npm run build
```

外部依赖在仓库根目录启动：

```bash
docker-compose up -d
```

项目使用标准 Maven 命令运行。请确保本机 Maven 可用，并且 `JAVA_HOME` 指向 JDK 17。

## 当前运行依赖

local profile 默认配置在 `data-agent/src/main/resources/application-local.yml`。

- MySQL 是业务主库，默认连接 `jdbc:mysql://localhost:3306/data_agent`，用户名 `root`。
- Milvus 是强依赖，默认 `localhost:19530`，collection 为 `data_agent_vectors`。应用启动会校验 Milvus 可达；向量写入/检索使用懒加载的 embedding store，避免启动阶段被 collection load 卡住。
- Redis 依赖存在，但 local profile 关闭 Redis repository 扫描；当前主链路不要求 Redis 必须先启动。
- Elasticsearch 是 RAG 全文检索的可选 provider。默认 `RAG_FULL_TEXT_PROVIDER=jpa`，不需要 ES；切到 `elasticsearch` 时需要启动 ES。
- 模型 API 需要在后台模型配置或环境变量中配置。local profile 里的默认 key 是占位值，只能用于启动，不能保证真实模型调用成功。

本地 MySQL 初始化：

```bash
mysql -uroot -pzym190457 -hlocalhost data_agent < sql/init.sql
```

## Profile 约定

- `local`：默认 profile，本地 MySQL + Milvus。适合 IDE 直接启动。
- `dev`：共享开发环境，数据库、Redis、Milvus、JWT、模型等通过环境变量注入。
- `prod`：生产环境，`JWT_SECRET`、`APP_ENCRYPTION_KEY`、数据库和模型凭据必须显式配置。

不要再把 H2 当成本地业务库使用。H2 仅作为测试依赖存在。

## 前端

前端源码在 `data-agent/frontend/src`，Vite 构建产物输出到 `data-agent/src/main/resources/static`，由 Spring Boot 托管。

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
cd data-agent/frontend
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

模型配置在 `model_config` 表，后台模型管理页面可维护。`ModelConfigService` 会保证同一租户只有一个默认模型。模型变更会发布 `ModelConfigChangeEvent`，`McpModelService` 监听后清理缓存。

OpenAI 兼容 HTTP 调用在 `com.ai.mcp.ModelHttpClient`。当前已支持 Kimi 两类常见配置：

- Moonshot 开放平台：`https://api.moonshot.cn/v1`，模型名如 `kimi-k2.6`
- Kimi Coding：`https://api.kimi.com/coding/v1`，模型名 `kimi-for-coding`

Kimi Coding 场景下，`kimi-2.6`、`kimi-k2.6` 等别名会被归一为 `kimi-for-coding`。401/403 通常是 API Key 与 Base URL 不匹配，404 通常是模型名或接口路径错误。

模型调用有重试和熔断：`ModelRetryExecutor` 不会重试 401/403/404，避免无意义重试；429 和 5xx 会重试。

## Agent 执行链路

主入口：

- `POST /api/v1/analysis/analyze`
- `POST /api/v1/analysis/analyze/stream`

核心路由在 `AgentRuntimeService`：

1. 斜杠命令：`SkillManager.processWithCommand`
2. 指定 `agentId`：`MultiAgentRuntimeService`
3. 指定 `skillId`：`SkillExecutionService`
4. 默认优先 `OrchestratorAgent`
5. Orchestrator 不可用时回退 `ReActAgent`

SSE 由 `AnalysisStreamService` 协调。它不是底层模型 token 直通，而是执行完成后把 thinking steps、result、done 等事件结构化发给前端。

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

新增工具时先看现有 service 的职责边界，通常需要同步更新工具定义、调用分发、测试和前端展示。

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

RAG 相关代码在 `com.ai.rag`，默认全文检索走 JPA。可选 Elasticsearch provider。RAG 会融合全文、向量、父上下文、重排、压缩和质量评估。

## 记忆系统

记忆相关代码在 `com.ai.memory`，持久化表包括：

- `memory_entry`
- `user_profile`

记忆有 working、short-term、long-term 等层级，支持压缩、衰减、保留策略和用户画像刷新。模型压缩默认关闭：`MEMORY_MODEL_COMPRESSION_ENABLED=false`。

## 向量和 Embedding

向量代码在 `com.ai.vector`。

- 默认 embedding provider 是 `local`，使用本地 AllMiniLmL6V2，维度 384。
- 可切换 `EMBEDDING_PROVIDER=api`，走 OpenAI 兼容 embedding API。
- Milvus collection、dimension、index、metric 在 `application.yml` 的 `milvus.*` 配置。
- `MilvusVectorStoreGateway` 启动阶段只初始化客户端和索引，`MilvusEmbeddingStore` 首次 add/search 时懒加载。

如果修改 embedding 维度，必须同时处理 Milvus collection 维度和历史数据，否则会写入失败。

## 数据库与租户

所有业务数据默认在 MySQL。新增查询必须考虑租户隔离，优先使用 `SecurityContextHelper.getCurrentTenantId()`。

常见租户字段：

- `tenant_id`
- `user_id`
- `created_by`

跨租户共享的默认数据通常使用 `tenant_id=default`。修改 list/detail 权限时要确认是否允许读取 default tenant 数据。

## 配置和密钥

`.env.example` 是环境变量模板。生产或共享环境必须配置：

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

常用验证：

```bash
cd data-agent
mvn -Dfrontend.skip=true test
mvn -Dfrontend.skip=true -DskipTests compile
cd frontend && npm run build
```

最近一次巡检时后端测试数约为 403 个。不要并发跑多个 Maven 命令写同一个 `target/`，资源复制阶段可能互相踩文件。

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
