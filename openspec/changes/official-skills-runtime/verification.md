# 验证记录：官方 Skills 运行时

日期：2026-10-08。结论：本机实现验收 PASS_WITH_NOTES，真实模型/业务数据库/浏览器验收未执行。本轮没有新增仓库测试类，没有业务数据写入、收费模型调用或 Git 提交。

## 基线与范围

基线 `9d0a625f4d731edf2bc0d1e08bc2bfa41a62c5bc`。开始时已有官方 Hook 与 MCP 修改，清单在 sdd-state.md。保留这些改动，McpModelService 本轮仅移除旧 Skill 专用上下文依赖、独立模型入口及其无用内部 skillId 参数；已有文档只增补/纠正 Skill 链路。

新增 SkillCatalog 和匹配现有 SDK 的 Skills 依赖；迁移能力目录/候选路由/系统指令/受治理 ToolProvider；移除运行时注册、Skill 列表缓存和旧凭据响应。管理页明确指令/资料用途、旧 API 清理及失败提示。旧 API 类型不静默执行或忽略，标记不可用，需人工迁移 Tool/MCP。

实际删除六个源文件：`skill/Skill.java`、`skill/DynamicSkill.java`、`skill/SkillManager.java`、`agent/tool/AgentSkillToolService.java`、`mcp/McpContext.java`、`mcp/McpContextManager.java`。源码与干净编译输出中均无旧执行类或 listAvailableSkills/useSkill/callModelWithContext 调用。源码删除可以通过 Git 恢复，不删除数据库 Skill 记录或旧配置列。前端构建替换受版本管理的旧哈希入口资源。

## 本机检查

由实现者在本机 JDK 17.0.9、Maven 3.8.8 执行：

- `mvn -Dfrontend.skip=true -DskipTests clean compile` 成功，553 个 Java 源文件；最终变更后再次 compile 成功，包含最新前端资源。
- `mvn checkstyle:check -Dcheckstyle.failOnViolation=true`，通过 checkstyle.includes 限定本轮变更 Java 路径，零违规。包括 SkillCatalog、Registry/Session/路由、CRUD、CacheNames/RedisConfig 及模型网关。
- `frontend/npm run build` 成功，1734 modules transformed；入口资源已更新。
- `git diff --check` 通过。
- 临时程序 `/tmp/data-agent-official-skills-wGajYP/OfficialSkillsCheck.java`：66 项检查通过，仓库无测试类。

临时程序调用真实的官方 Skills、AiServices、消息窗口、Reactor Flux、Registry/准入/Schema 校验、Pipeline、ContextPropagatingTaskDecorator、Checkpoint 消息映射器和 DurableToolContinuation。使用脚本模型、内存仓储及审批存储替身，计算工具真实执行，演示写动作由计数替身记录。因此验证的是 SDK 与项目治理的集成，不能视为真实数据库审批或厂商质量验收。

覆盖结果：

- 目录只含已绑定、当前租户可见的名称/用途，不含完整指令、资料或其他租户技能。
- 官方 activate_skill 按稳定 ID 返回指令；read_skill_resource 精确读取 references/steps.md；缺参数、其他技能和路径穿越拒绝，错误原文不送模型。
- 缺能力作用域、CHAT、无租户/绑定、停用/删除/跨租户/旧 API 配置均不能读取（无租户由源码强制校验；其余由临时程序执行）。平台 default 技能仍须明确绑定。
- 激活不会获得 calculate 授权；零请求白名单拒绝；单独读取技能的模型调用数为零。
- 同步与异步 Flux 实际经历激活→资料→真实 calculate→最终回答，分别恰好 4 个模型轮次/3 次工具调用，共用根 Run 预算，结果原生回填，调用者上下文不泄露。
- 审批整批预检暂停，同行计算和写动作零执行；Checkpoint 保留已激活指令；批准替身执行一次，原生恢复继续读资料并结束，不重放写动作；重复调用 ID 不执行工具。
- CRUD 创建、修改、改名后稳定绑定、指令/步骤回滚、启停、旧 API 启用拒绝、显式清理 URL/Headers，以及 Router 补齐资源工具和拒绝旧工具名通过。

SDK 实测发现的接口差异已修复：ToolProviderRequest 需要 invocationContext/userMessage；占位只用于查询目录，不进入模型对话。Skill ToolExecutor 必须 executeWithContext，旧 execute 会直接失败。使用 aiServiceTools 而非已弃用 tools 访问结果。官方 ToolArgumentsException 与资源权限异常映射到稳定的 INVALID_ARGUMENTS/UNAUTHORIZED，不送原始异常。

## 保留限制与后续操作

- 官方 Skills 模块实验性，版本与现有 Core 配套，不升级全栈或声称所有官方能力已用到。
- 本轮只有现有数据库指令与步骤资源，没有任意脚本执行、附件目录或文件系统 Skill 导入。
- 不使用 skill-scoped ToolProvider 自动授予工具，因此不需要激活属性跨持久化恢复；已有安全工具结果文本足以保留指令。未来若引入动态工具，须补激活元数据和恢复授权设计，不能直接接原始 Provider 绕过治理。
- 旧 API 能力须先迁为 Tool/MCP 并绑定，再清空旧 API 配置。数据库列和旧缓存键未破坏性删除，旧缓存不再读取，按 TTL 过期。
- 激活没有独立模型费用，Token 消耗归当前 Agent/Run；旧 Skill 独立调用的历史账单可继续查看。
- 真实意图路由、收费端点、MySQL 事务/审批租约/加密及浏览器状态需要重启后验收；操作输入见 Skill官方框架迁移.md。既有 Supervisor 整图持久化限制未改动，审批验收使用 TOOL_CALLING。
