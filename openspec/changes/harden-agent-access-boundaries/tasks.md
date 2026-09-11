# Tasks

- [x] Task 1: 加固管理控制面 HTTP 路径。（实现任务）
  - Acceptance: 资源中心、租户 Token 管理、RAG 运维接口要求 ADMIN；个人 Token 与 RAG retrieve 保持普通用户可用。
  - Verify: 静态列出 `SecurityConfig` 匹配顺序并确认具体路径先于 `/api/v1/**`。
  - Files: `src/main/java/com/ai/security/auth/SecurityConfig.java`
  - Covers: SC-1, SC-7

- [x] Task 2: 提供普通用户安全模型目录。（实现任务）
  - Acceptance: 新接口只返回当前租户/共享租户的启用模型和五个非敏感字段。
  - Verify: DTO 字段扫描确认没有 apiKey/baseUrl；服务查询沿用现有租户回退规则。
  - Files: `src/main/java/com/ai/controller/PersonalModelController.java`, `src/main/java/com/ai/modelconfig/dto/PersonalModelOptionResponse.java`, `src/main/java/com/ai/modelconfig/dto/PersonalModelOptionsResponse.java`, `src/main/java/com/ai/service/ModelConfigService.java`
  - Covers: SC-2, SC-7

- [x] Task 3: 让 HTTP 认证使用数据库当前用户状态。（实现任务）
  - Acceptance: Filter 拒绝不存在或禁用用户，并使用数据库当前租户和角色；refresh 拒绝禁用用户。
  - Verify: 静态追踪 JWT subject -> SysUser -> enabled -> current roles -> TenantUser；确认 claims roles 不进入授权主体。
  - Files: `src/main/java/com/ai/security/auth/JwtAuthenticationFilter.java`, `src/main/java/com/ai/security/auth/AuthService.java`
  - Covers: SC-3

- [x] Task 4: 收紧普通注册的租户来源。（实现任务）
  - Acceptance: 服务端配置决定普通注册是否开放及注册租户；前端不再显示租户输入；生产默认关闭。
  - Verify: 搜索 `request.tenantId()` 确认普通注册不读取；配置模板无真实租户或密钥。
  - Files: `src/main/java/com/ai/security/auth/AuthService.java`, `src/main/resources/application.yml`, `src/main/resources/application-local.yml`, `frontend/src/pages/LoginScreen.jsx`
  - Covers: SC-4

- [x] Task 5: 补齐知识同步的个人所有权。（实现任务）
  - Acceptance: HTTP 发起的同步配置读写和手动同步只能操作当前用户创建的知识。
  - Verify: 静态追踪所有四个 Controller 路径到 `knowledgeId + tenantId + createdBy` 查询。
  - Files: `src/main/java/com/ai/service/knowledge/KnowledgeSyncSecurityService.java`, `src/main/java/com/ai/service/knowledge/KnowledgeSyncService.java`
  - Covers: SC-5

- [x] Task 6: 切换个人 Agent 的模型目录接口。（实现任务）
  - Acceptance: Chat 页面不再调用管理员 `/api/v1/models/list`；模型选择和当前模型展示字段兼容。
  - Verify: 前后端路径和响应字段静态对齐，普通用户页面不存在管理员模型 DTO 依赖。
  - Files: `frontend/src/pages/ChatPage.jsx`
  - Covers: SC-6, SC-7

- [x] Task 7: 更新权限边界文档并执行静态验收。（文档与验证任务）
  - Acceptance: 文档准确描述控制面路径、数据库实时主体、注册租户和剩余角色模型债务。
  - Verify: `git diff --check`、端点矩阵、敏感字段扫描、变更文件范围检查；不运行测试、编译或构建。
  - Files: `CLAUDE.md`, `docs/核心逻辑详解/Agent运行时与Harness.md`, `openspec/changes/harden-agent-access-boundaries/tasks.md`, `openspec/changes/harden-agent-access-boundaries/sdd-state.md`, `openspec/changes/harden-agent-access-boundaries/verify-report.md`
  - Covers: SC-1, SC-2, SC-3, SC-4, SC-5, SC-6, SC-7

## Checkpoints

- Task 1-3 后检查所有授权事实是否仍有 JWT claims 或前端角色分支旁路。
- Task 4-6 后检查注册、知识同步和模型目录的前后端契约。
- Task 7 完成后只报告静态结论，运行时鉴权结果明确标记为未实测。
