# Data Agent

## 环境

项目提供三套 Spring Profile：

- `local`：默认本地环境。IDE 直接启动 `DataAgentApplication` 即可，使用 MySQL。
- `dev`：共享开发环境。使用 MySQL、Redis 等外部服务，通过环境变量配置。
- `prod`：生产环境。必须显式配置数据库、JWT、模型 API、加密密钥等凭据。

## 本地启动

当前本地默认连接你的 MySQL：

- 地址：`localhost:3306`
- 数据库：`data_agent`
- 用户：`root`
- 密码：`zym190457`

首次或表结构不完整时，先执行：

```bash
mysql -uroot -pzym190457 -hlocalhost data_agent < sql/init.sql
```

```bash
./mvnw17.sh spring-boot:run
```

默认会启用 `local` profile。也可以用 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 覆盖默认 MySQL 连接。
启动完成后访问：

```text
http://localhost:8080
```

前端使用 React + Vite 开发，源码位于：

```text
frontend/src
```

后端启动时 Maven 会自动执行前端依赖安装和构建，构建产物输出到 `src/main/resources/static`，由 Spring Boot 直接托管。

如果只调试后端，不想重新构建前端：

```bash
./mvnw17.sh -Dfrontend.skip=true spring-boot:run
```

如果要连接共享开发环境：

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw17.sh spring-boot:run
```

如果要连接生产环境：

```bash
SPRING_PROFILES_ACTIVE=prod ./mvnw17.sh spring-boot:run
```

## 配置文件

- `src/main/resources/application.yml`：通用配置，只放非环境敏感项。
- `src/main/resources/application-local.yml`：本地开发配置，默认连接 MySQL。
- `src/main/resources/application-dev.yml`：共享开发环境配置。
- `src/main/resources/application-prod.yml`：生产环境配置。

## RBAC 存储

角色和权限已经落到真实表，不再存内存：

- `sys_role`
- `sys_permission`
- `sys_user_role`
- `sys_role_permission`

用户主表仍然是 `sys_user`，`roles` 通过 `sys_user_role` 关联到 `sys_role`。

默认初始化会写入：

- `USER`、`ADMIN` 两个角色
- `app:use`、`*:*` 两条权限
- `BOOTSTRAP_ADMIN_USERS` 指定的账号会自动补上 `ADMIN` 角色

如果你当前数据库里角色表是空的，先让应用跑一次初始化，再查这四张表。

生产环境不要使用默认值启动，按 `.env.example` 配置真实环境变量。

## 当前启动依赖

- JDK 17。
- MySQL，默认库名 `data_agent`，所有业务表都存在 MySQL，不再使用 H2 存业务数据。
- Milvus，默认 `localhost:19530`，用于知识库、技能、记忆等向量索引；当前配置是强依赖，服务不可用会启动失败。
- 模型 API 配置，本地 profile 会使用占位 key，真实问答需要在后台模型配置或环境变量里配置可用的 `LANGCHAIN_API_KEY`、`LANGCHAIN_BASE_URL`、`LANGCHAIN_MODEL_NAME`。
- Kimi 有两套常见入口：Moonshot 开放平台 Key 使用 `https://api.moonshot.cn/v1`，模型名用 `kimi-k2.6` 等开放平台模型；Kimi Code / Kimi Coding Key 使用 `https://api.kimi.com/coding/v1`，模型名用 `kimi-for-coding`。
- Redis 目前作为依赖包存在，local profile 已关闭 Redis repository 扫描；当前主流程启动不要求 Redis 先起来。

本地管理员账号当前是 `super`，已绑定 `USER` 和 `ADMIN` 角色。管理员初始化接口是 `POST /api/v1/auth/bootstrap-admin`，但只有系统里还没有任何管理员时才允许使用；已有管理员后通过“管理员申请”流程审核。

## 代码注释规范

项目代码按阿里巴巴 Java 代码规范执行，新增和重构代码必须补充必要注释：

- 所有 `public` 类、接口、枚举、record 必须有 Javadoc，说明职责和边界。
- 所有 `public` 方法必须有 Javadoc，说明入参、返回值、异常或副作用。
- 涉及安全、租户隔离、事务、异步线程、缓存、重试、熔断、向量索引、模型调用的逻辑，必须在关键代码块前写简短注释说明原因。
- 私有方法如果包含复杂业务规则、非显而易见的分支或兼容逻辑，也要补注释。
- 禁止写无价值注释，例如“设置变量”“调用方法”这类重复代码字面含义的注释。
- 注释优先解释“为什么这样做”和“这里保护什么边界”，而不是逐行翻译代码。
