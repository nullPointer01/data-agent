# Data Agent 应用说明

这是 Data Agent 的主工程，包含 Spring Boot 后端和 React/Vite 前端。当前项目目标是提供一个可配置的数据分析智能体平台，支持对话、文件、知识库、RAG、记忆、多 Agent 编排、动态技能、模型管理、权限管理、审计和质量反馈。

## 技术栈

- Java 17
- Spring Boot 3.2
- Spring Security + JWT
- Spring Data JPA
- MySQL
- LangChain4j 0.30
- Milvus 2.3
- React + Vite
- Apache POI / PDFBox
- 可选 Redis
- 可选 Elasticsearch

## 本地启动

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
cd ..
docker-compose up -d
```

启动应用：

```bash
cd data-agent
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

- `local`：默认本地环境，使用 MySQL 和 Milvus。可以用 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 覆盖数据库连接。
- `dev`：共享开发环境，所有敏感配置通过环境变量注入。
- `prod`：生产环境，要求显式配置数据库、JWT、加密密钥、模型 API、Milvus 等。

H2 仅用于测试依赖，不再作为本地业务存储。

## 外部依赖

`../docker-compose.yml` 提供：

- Milvus standalone：`19530`
- Attu：`8000`
- Redis：`6379`
- Elasticsearch：`9200`
- Milvus 所需 etcd / MinIO

当前应用启动时会校验 Milvus 可达。Milvus 是强依赖；Redis 和 Elasticsearch 是能力依赖，默认主流程不要求它们必须可用。

## 环境变量

参考 `.env.example`。核心变量：

```bash
SPRING_PROFILES_ACTIVE=local

DB_URL=jdbc:mysql://localhost:3306/data_agent?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
DB_USERNAME=root
DB_PASSWORD=zym190457

MILVUS_HOST=localhost
MILVUS_PORT=19530

JWT_SECRET=your_random_256bit_jwt_secret_at_least_32_chars
APP_ENCRYPTION_KEY=your_random_encryption_key_at_least_32_chars

LANGCHAIN_API_KEY=your_llm_api_key
LANGCHAIN_BASE_URL=https://api.openai.com/v1
LANGCHAIN_MODEL_NAME=qwen-plus
```

生产环境不要使用 local 默认密钥。`APP_ENCRYPTION_KEY` 会影响数据库敏感字段解密，不能随意更换。

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
- `admin_role_request`

默认初始化会确保 `USER`、`ADMIN`、`app:use`、`*:*` 存在。`BOOTSTRAP_ADMIN_USERS` 指定的已有用户会自动补管理员角色，local 默认是 `super`。

如果系统还没有任何管理员，可以调用 `/api/v1/auth/bootstrap-admin` 创建第一个管理员。已有管理员后，新用户通过前端“管理员申请”提交申请，由管理员审核。

## 模型配置

模型配置落表到 `model_config`，前端“模型”页面可以维护。系统按租户管理模型，并保证每个租户最多一个默认模型。

常见 Kimi 配置：

```text
Moonshot 开放平台:
baseUrl=https://api.moonshot.cn/v1
modelName=kimi-k2.6

Kimi Coding:
baseUrl=https://api.kimi.com/coding/v1
modelName=kimi-for-coding
```

如果使用 Kimi Coding，系统会把 `kimi-2.6`、`kimi-k2.6` 等别名归一到 `kimi-for-coding`。401/403 通常表示 API Key 与 Base URL 不匹配；404 通常表示路径或模型名不对。

模型调用失败时，401/403/404 不会重试；429 和 5xx 会重试并参与熔断。

## 前端功能

前端源码在 `frontend/src`，导航功能包括：

- 工作台
- 智能对话
- 资料中心
- 记忆中心
- 知识库
- 文件
- 管理员申请
- 用户管理
- 角色权限
- 模型管理
- 技能管理
- Agent 管理
- 数据源管理
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
com.ai.security.rbac    角色权限和管理员申请
com.ai.agent            Agent 主流程
com.ai.agent.react      ReAct 循环
com.ai.agent.orchestrator 多专家编排
com.ai.agent.specialist 专家实现
com.ai.agent.tool       Agent 工具
com.ai.skill            动态技能
com.ai.mcp              模型调用、缓存、重试、token 记录
com.ai.memory           记忆系统
com.ai.rag              RAG 检索
com.ai.vector           Embedding 与 Milvus
com.ai.service          应用服务
com.ai.repository       JPA Repository
```

## 分析执行链路

入口：

- `POST /api/v1/analysis/analyze`
- `POST /api/v1/analysis/analyze/stream`

路由顺序：

1. 斜杠命令进入 Skill 命令执行。
2. 指定 `agentId` 时走自定义 Agent。
3. 指定 `skillId` 时走动态技能。
4. 默认走 Orchestrator 多专家编排。
5. Orchestrator 不可用时回退 ReAct。

SSE 输出由 `AnalysisStreamService` 负责，会输出 start、thinking steps、result/error、done 等事件。

## RAG、知识库和文件

知识库、文件、记忆等内容会写入 MySQL，并根据类型写入 Milvus 向量索引。RAG 默认全文检索 provider 是 JPA，可切换到 Elasticsearch：

```bash
RAG_FULL_TEXT_PROVIDER=elasticsearch
RAG_ELASTICSEARCH_BASE_URL=http://localhost:9200
```

文件上传后会进入异步处理队列，解析文本后写入文件表和知识/向量索引。支持 Office、PDF、普通文本等解析能力。

## 向量和 Embedding

默认 embedding provider：

```bash
EMBEDDING_PROVIDER=local
EMBEDDING_DIMENSION=384
```

可切换 OpenAI 兼容 embedding API：

```bash
EMBEDDING_PROVIDER=api
EMBEDDING_API_BASE_URL=http://localhost:11434/v1
EMBEDDING_API_KEY=
EMBEDDING_MODEL_NAME=bge-large-zh-v1.5
```

修改 embedding 维度时，必须同步处理 Milvus collection 维度和历史向量数据，否则会写入失败。

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
mvn -Dfrontend.skip=true -DskipTests compile
mvn -Dfrontend.skip=true test

cd frontend
npm run build
```

最近巡检时后端测试约 403 个。不要并发执行多个 Maven 命令写同一个 `target/`，否则资源复制可能互相冲突。

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
