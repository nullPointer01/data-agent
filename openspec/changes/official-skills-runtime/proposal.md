# 官方 Skills 运行时迁移

用户已明确要求“按照你说的进行迁移 skill 模块”。沿用框架优先、删除替代实现、不新增仓库测试类的约定，直接实现已授权范围。

旧 Skill 以独立模型调用执行 Prompt，且自动访问配置的 HTTP API；新 Skill 使用匹配现有 SDK 的 `langchain4j-skills:1.20.2-beta30`，按需激活指令和读取参考资料，当前 Agent 继续推理并调用受治理工具。

保留数据库 CRUD、版本回滚、租户及 Agent 稳定 ID 绑定。旧 HTTP 配置保留供人工识别和清理，有旧 API 的技能标记待迁移，禁止隐藏请求；外部执行交给 Tool/MCP。

成功标准（由本轮实现者在本机验证）：

- SDK 生成并执行 activate_skill/read_skill_resource；同步及 Flux 多轮对话能读指令、资料后调用业务工具。临时 Java 检查，不访问付费模型。
- 未绑定、跨租户、停用、已删除、旧 API 技能不能读取；无作用域拒绝执行。临时检查及源代码审查。
- 激活不额外发起独立 Skill 模型调用，不授予工具权限；审批恢复仍使用同一 Registry、白名单、预算与消息续跑。临时检查及调用链审查。
- 删除旧 Skill 接口、DynamicSkill、SkillManager、list/use 适配服务及孤立的 McpContext/McpContextManager 和模型入口；Java 编译、定向 Checkstyle、前端构建通过。

非目标：升级全部 SDK、Skill 自动授权业务工具、动态执行 Skill 内脚本、生产端到端验收、数据库破坏性清理。
