## 1. Verification Decision And Safety Contract

- [ ] 1.1 确认是否恢复最小 Java 测试与 Maven 执行权限，并把决定记录到 `sdd-state.md`；未获授权时不得修改 `pom.xml`、创建测试源码或运行 Maven。（决策门禁）
- [ ] 1.2 若测试获准，添加 test-scope `spring-boot-starter-test` 并先创建 Codec 状态矩阵测试，覆盖 unified/legacy 的 `null`、`[]`、有效数组、非法 JSON、非数组、空值和重复值；测试直接调用被测方法且不连接外部服务。（测试任务；Files: `pom.xml`, `src/test/java/com/ai/agent/capability/AgentCapabilityConfigurationCodecTest.java`）

## 2. Single Configuration Codec

- [ ] 2.1 新增能力配置领域异常与单一 Codec，返回显式状态、规范化不可变值和安全原因码；原始 JSON 不进入异常消息，编码时保留 unified `null`/`[]` 的差异。（实现任务；Files: `src/main/java/com/ai/agent/capability/AgentCapabilityConfigurationException.java`, `src/main/java/com/ai/agent/capability/AgentCapabilityConfigurationCodec.java`）
- [ ] 2.2 从 `AgentProfile` 移除静态 ObjectMapper 和四个 JSON helper，仅保留原始字段访问器及非解析型 `hasExplicitCapabilityBindings()`；静态扫描 Entity 不再依赖 Jackson 或 `AgentCapabilityBindingSet`。（实现任务；Files: `src/main/java/com/ai/model/AgentProfile.java`）

### Checkpoint A

- Codec 能独立区分所有配置状态，非法输入没有空集合降级出口。
- JPA Entity 不再拥有 JSON 或授权语义。

## 3. Consumer Integration

- [ ] 3.1 将 `AgentProfileService` 的保存、旧客户端合并和 Profile 响应映射接入 Codec；显式空能力保存为 `[]`，legacy 合同保留 `NULL`，已有损坏统一字段拒绝被旧请求静默覆盖。（实现任务；Files: `src/main/java/com/ai/service/AgentProfileService.java`, `src/main/java/com/ai/agent/dto/AgentProfileResponse.java`）
- [ ] 3.2 将 `AgentCapabilityService` 与 `AgentCapabilityRegistry` 接入同一 Codec；仅 legacy `tools=NULL` 展开默认 Tool，legacy `[]` 返回零 Tool，任一非法配置在构造 allowlist/风险快照前失败关闭。（实现任务；Files: `src/main/java/com/ai/agent/capability/AgentCapabilityService.java`, `src/main/java/com/ai/agent/capability/AgentCapabilityRegistry.java`）
- [ ] 3.3 为能力配置领域异常增加稳定 API 错误映射，只返回原因码、字段与 agentId，不返回原始 JSON；核对通用异常处理不再吞掉该诊断。（实现任务；Files: `src/main/java/com/ai/advice/GlobalExceptionHandler.java`, `src/main/java/com/ai/agent/capability/AgentCapabilityConfigurationException.java`）
- [ ] 3.4 若测试获准，创建 Runtime 失败关闭测试，证明非法 legacy/unified 配置不会得到 Tool、不会回退另一字段，并覆盖合法 legacy 默认、legacy 空集及统一空集。（测试任务；Files: `src/test/java/com/ai/agent/capability/AgentCapabilityServiceTest.java`, `src/test/java/com/ai/agent/capability/AgentCapabilityRegistryTest.java`）

### Checkpoint B

- Profile 保存、读取、目录、风险计算和运行时使用同一个配置合同。
- 全仓库不存在 `getToolList`、`setToolList`、`getCapabilityBindingList`、`setCapabilityBindingList` 调用或 Entity 内 ObjectMapper。
- 损坏数据最多导致当前 Profile 被拒绝，不会扩展为平台 Tool 集合。

## 4. Audit And Explicit Migration

- [ ] 4.1 新增只读 MySQL 审计脚本，分别统计并列出 unified 非法、legacy 非法、legacy `NULL`、legacy `[]` 和有效统一配置；确认脚本只包含 `SELECT`/CTE，不修改真实数据。（运维任务；Files: `sql/audit-agent-capability-bindings.sql`）
- [ ] 4.2 新增人工迁移模板与操作说明，只转换可无歧义映射的 legacy 非空 Tool/Skill；legacy `NULL` 必须先确认具体 Tool 快照，脚本不得由 `DataInitializer` 或启动 Runner 调用。（运维任务；Files: `sql/migrate-agent-capability-bindings.sql`, `docs/核心逻辑详解/Agent工具治理.md`）

## 5. Verification And Handoff

- [ ] 5.1 执行 OpenSpec strict、配置 helper/异常吞噬/启动迁移静态扫描和 `git diff --check`；若测试获准，再执行 JDK 17 定向测试与编译，并分别记录已验证证据和未执行项。（验证任务；Files: `openspec/changes/harden-agent-capability-boundary/verify-report.md`, `openspec/changes/harden-agent-capability-boundary/sdd-state.md`）
- [ ] 5.2 对照规格逐项确认失败关闭、兼容读取、API 安全诊断和人工迁移边界，并更新当前架构文档；不得把未运行的测试或迁移描述为已验证。（文档任务；Files: `docs/核心逻辑详解/Agent运行时与Harness.md`, `docs/演化/常见陷阱.md`, `openspec/changes/harden-agent-capability-boundary/verify-report.md`）
