# Design: Agent Access Boundary Hardening

## Context

项目已经将前端拆分为个人工作区和管理控制台，并在 Spring Security 中保护了大部分管理路径。个人会话、Agent、文件、知识、记忆和两路 RAG 检索普遍使用 `tenantId + userId`。当前缺口集中在边界不一致，而不是缺少完整认证框架。

已确认的问题：

1. `/api/v1/resources/**`、部分 `/api/v1/token/**` 和部分 RAG 运维接口只要求登录，但页面只对管理员展示。
2. 个人 Agent 页面调用 `/api/v1/models/list`，该路径整体受 `ADMIN` 保护。
3. `JwtAuthenticationFilter` 直接相信访问令牌中的角色和租户；管理员禁用用户或修改角色后，旧访问令牌仍能建立认证。
4. 普通注册 DTO 接受 `tenantId`，客户端可以选择组织边界。
5. 知识常规 CRUD 已按用户隔离，但自动同步服务只按租户检查。

## Goals

- 用最小改动补齐控制面和数据所有权边界。
- 保持个人 Agent 页面不感知管理员模型配置中的敏感字段。
- 让禁用和撤权对下一次 HTTP 请求生效。
- 保持现有 Harness、Tool Governance 和 Durable Run 行为不变。

## Non-Goals

- 不解决平台管理员与租户管理员的角色拆分。
- 不实现资源共享 ACL。
- 不实现用户侧高风险动作确认。
- 不为当前只读 RBAC 页面增加角色编辑能力。

## Decisions

### 1. 管理端点使用明确路径匹配

扩展 `SecurityConfig` 的管理员匹配规则：资源中心全路径、租户 Token 聚合/明细/重置、RAG 设置/健康/评估要求 `ADMIN`。`POST /api/v1/rag/retrieve` 保持所有已登录用户可用，因为它在服务层按当前 `tenantId + userId` 检索个人资料。

Token 的个人汇总和剩余额度继续允许普通用户访问。`reset` 保持兼容 URL，但只允许管理员；将 GET 改 POST 属于外部 API 变更，本阶段不做。

### 2. 新增最小化的个人模型目录

新增 `/api/v1/my/models` 只读端点和专用响应 DTO。服务端复用 `ModelConfigService` 的当前租户 + `default` 共享模型解析规则，只返回：`modelId`、展示名称、模型名、厂商、是否默认。只返回启用项，绝不复用包含 Base URL 或掩码密钥的管理员 DTO。

个人 Agent 页面切换到新接口；管理员模型页面继续使用 `/api/v1/models/**`。

### 3. 每个请求从数据库重建主体

JWT 签名、有效期和 subject 校验保持不变。Filter 解析 subject 后读取 `SysUser`，要求用户存在、启用，并以数据库当前 `tenantId` 和当前启用角色构建 `TenantUser`。令牌内 username/tenant/roles 不再作为授权事实。

这是每请求一次用户查询的安全优先实现。后续若性能数据证明有必要，可增加短 TTL、主动失效的认证缓存，但不在本阶段引入缓存一致性复杂度。

刷新令牌同样检查用户启用状态。角色和租户变化后，新访问令牌使用数据库当前值。

### 4. 普通注册由服务端分配租户

增加 `app.security.registration.enabled` 和 `app.security.registration.tenant-id`。普通注册忽略/移除客户端 tenantId，统一使用服务端配置。local 可显式开启以保留演示注册；prod 默认关闭。Bootstrap Admin 和管理员创建用户仍可显式设置 tenantId，因为它们承担受控初始化和治理职责。

为兼容旧客户端，第一版可暂时保留 `RegisterRequest.tenantId` 字段但不读取，并在前端移除租户输入框；后续版本再删除请求字段。

### 5. 知识同步统一个人所有权

`KnowledgeSyncSecurityService` 增加当前用户检查，并通过 `findByKnowledgeIdAndTenantIdAndCreatedBy` 读取条目。后台异步写回继续使用持久化配置中的租户和知识编号，不依赖 HTTP SecurityContext；它处理的是已经通过所有权验证创建的配置。

## Risks And Trade-offs

- 每请求查用户表会增加数据库读负载，但能让禁用和撤权立即生效；当前项目规模下收益高于成本。
- 保留注册 DTO 的 tenantId 字段但忽略它，短期兼容性更好，但 API 语义不够纯净；前端移除后应在后续主版本删除。
- `ADMIN` 目前仍可跨租户管理用户，这是已知问题。本阶段不暗中改变管理员作用域，避免锁死已有管理员；下一阶段需明确平台管理员和租户管理员。
- `/api/v1/token/reset` 继续使用 GET 是历史兼容债务，只通过管理员鉴权降低风险。
