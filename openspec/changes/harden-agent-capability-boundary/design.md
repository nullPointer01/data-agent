## Context

`AgentProfile` 当前同时承担 JPA 持久化、JSON 编解码和授权语义转换。legacy `tools` 解析失败时返回空列表，而运行时又把 legacy 空列表解释为全部已注册 Tool，导致损坏数据能够从解析异常变成扩权。统一字段 `capability_bindings` 已经用 `NULL` 和 `[]` 区分旧合同与零能力，但两套字段仍由 Entity helper 分散解析，Registry、保存服务、响应映射和 Runtime 容易形成不同解释。

本变更是架构优化顺序中的第一步：先建立能力配置的安全边界，再处理测试基线、Runtime 收敛和大类拆分。项目固定 JDK 17，当前没有 Java 测试依赖，数据库升级仍采用人工 SQL；这些现状不能在本变更中被隐式改变。

## Goals / Non-Goals

**Goals:**

- 让能力配置具有可枚举的状态，任何调用方都不再通过空集合猜测数据状态。
- 非法 legacy 或统一绑定 JSON 在目录、详情、保存和运行时均失败关闭，且不会回退到默认 Tool。
- JPA Entity 只暴露原始持久化字段，JSON 与能力身份校验集中在单一 Codec。
- 为现有数据提供只读审计和人工触发的迁移路径，迁移过程不在启动阶段运行。
- 保持合法 legacy Profile 可继续运行，并给出逐步迁移到统一绑定字段的办法。

**Non-Goals:**

- 不合并 Chat、ReAct、Orchestrated 执行器，不删除旧 Agent 分支。
- 不修改 Tool Registry、RBAC、审批、预算或风险策略的业务规则。
- 不增加数据库列，不引入 Flyway/Liquibase，不自动修改现有 Profile。
- 不修改模型、RAG、Embedding、前端和外部 API。

## Decisions

### 1. 使用类型化解码结果表达配置状态

新增无状态 `AgentCapabilityConfigurationCodec`，由 Spring 管理的 `ObjectMapper` 构造。Codec 返回不可变解码结果，至少包含 `state` 和规范化值；状态固定为：

| 字段 | 原始值 | 状态 | 运行时含义 |
|---|---|---|---|
| `capability_bindings` | `NULL` | `LEGACY` | 继续读取 legacy 字段 |
| `capability_bindings` | `[]` | `EXPLICIT_EMPTY` | 零能力 |
| `capability_bindings` | 有效非空数组 | `EXPLICIT_VALUES` | 精确能力白名单 |
| `capability_bindings` | 空串、非数组、非法身份或非法 JSON | `INVALID` | 拒绝，不回退 legacy |
| legacy `tools` | `NULL` | `LEGACY_UNCONFIGURED` | 保留“默认已启用 Tool”兼容行为 |
| legacy `tools` | `[]` | `LEGACY_EXPLICIT_EMPTY` | 零 Tool |
| legacy `tools` | 有效非空数组 | `LEGACY_VALUES` | 精确 Tool 白名单 |
| legacy `tools` | 空串、非数组、空名称、重复值或非法 JSON | `INVALID` | 拒绝，不返回默认 Tool |

选择显式状态而不是 `Optional<List<String>>`，因为 `null`、`[]` 和损坏数据有不同的授权含义。legacy `[]` 收紧为零 Tool；历史 API 的 `setToolList(empty)` 本来会保存为 `NULL`，因此合法的“默认全部”记录仍由 `NULL` 表达。

### 2. 解析异常使用领域异常并携带安全诊断

Codec 遇到损坏数据时抛出 `AgentCapabilityConfigurationException`，包含稳定原因码、字段名和 `agentId`，但不回显原始 JSON。API advice 将其转换为固定错误码；Runtime、Registry 和保存路径不捕获后降级。日志可以定位记录，不能包含完整能力配置或其他敏感字段。

选择领域异常而不是通用 `IllegalStateException`，是为了让调用方能区分“配置损坏”和“服务内部故障”，同时避免根据异常文本写控制流。

### 3. Entity 只负责原始持久化

从 `AgentProfile` 移除静态 `ObjectMapper`、`getToolList/setToolList`、`getCapabilityBindingList/setCapabilityBindingList`。`getTools/setTools` 和 `getCapabilityBindings/setCapabilityBindings` 保留为 JPA 原始字段访问器，`hasExplicitCapabilityBindings()` 可以保留为不解析 JSON 的字段状态判断。

`AgentProfileService` 负责通过 Codec 编码保存值并构造响应；`AgentCapabilityService` 和 `AgentCapabilityRegistry` 通过同一 Codec 解码。这样 Entity 不依赖 Agent capability 包，持久化模型也不再吞解析异常。

### 4. 权威字段优先级保持不变，但失败不回退

只要 `capability_bindings` 非 `NULL`，它就是权威字段：`[]` 为零能力，有效数组为精确列表，非法值直接拒绝。只有该字段为 `NULL` 时才解析 legacy `tools` 和 `skill_id`。legacy `tools=NULL` 继续展开为当前已启用 Tool，`tools=[]` 不展开。

保存新版请求时始终编码统一字段，并同步投影 legacy `tools` 供旧客户端读取。旧客户端更新已迁移 Profile 时，先安全解码现有统一字段再合并其能表达的 Tool/单 Skill；现有统一字段损坏时拒绝保存，不能用旧请求覆盖或修复。

### 5. 审计只读，迁移必须显式执行

新增 SQL 审计脚本，按租户和配置状态统计并列出异常记录，只使用 `SELECT` 和 MySQL JSON 函数。另提供显式迁移脚本或清晰的人工迁移模板：仅转换能无歧义映射的有效 legacy 列表；`tools=NULL` 的动态默认集合必须由操作者结合当前 Registry 确认后再物化。脚本不接入 `DataInitializer`，应用启动不执行迁移。

迁移前备份、真实数据库执行和删除 legacy 字段均不属于本变更的自动动作。

### 6. 测试恢复是独立人工门禁

项目当前不存在 Java 测试栈。推荐增加 test-scope 的 `spring-boot-starter-test`，只为 Codec 和 Runtime 绑定解析建立最小单元测试，不启动完整 Spring Context，不连接数据库、模型、Milvus 或 Elasticsearch。由于用户此前明确要求不测试、不编译，Coding 前必须确认是否恢复该依赖和 Maven 执行；若不恢复，则测试任务跳过并明确记录残余风险。

## Risks / Trade-offs

- [Risk] 数据库里已有 legacy `tools=[]` 的记录会从“默认全部”变成“零 Tool” -> 先运行只读审计列出数量与 ID；这是有意的失败关闭收紧，不自动改写。
- [Risk] 多个调用方改用 Codec 会扩大一次改动面 -> 限定在 Profile 保存/响应、Capability Service 和 Registry，逐个扫描清除 Entity helper 引用。
- [Risk] 详情接口遇到损坏记录会返回错误，管理员暂时无法从 UI 修复 -> 错误包含安全的 agentId 和原因码，使用审计 SQL 定位并通过显式数据修复流程处理。
- [Risk] legacy `tools=NULL` 仍保留动态“全部已启用 Tool”语义 -> 仅为兼容保留并在审计中单独标识；后续逐步迁移为显式绑定，不把它作为新建 Profile 的默认合同。
- [Risk] 不恢复自动化测试会让安全回归只能靠静态推演 -> 在任务中保留独立测试门禁并如实记录未验证项，不用“已验证”措辞代替运行证据。

## Migration Plan

1. 在任何生产改动前运行只读审计，统计 `capability_bindings` 非法、legacy `tools` 非法、legacy `NULL` 和 legacy `[]`。
2. 部署 Codec 与失败关闭调用链；不自动更新任何 Profile。
3. 对有效 legacy 非空 Tool 列表生成显式 `tool:<name>` 绑定，并合并合法 `skill_id`；每批迁移前后核对数量。
4. 对 legacy `tools=NULL` 记录，由操作者确认该 Agent 应绑定的当前 Tool 快照后显式写入；不根据未来 Registry 动态扩权。
5. 保留 legacy 字段作为兼容投影。删除字段和引入正式迁移框架放入后续独立变更。
6. 回滚应用代码时不需要回滚数据库；已写入的合法统一字段仍能被现有版本读取。单条迁移回滚依赖执行前备份，不提供启动期自动回滚。

## Open Questions

- Coding 前需要用户确认：是否允许新增 test-scope 的 `spring-boot-starter-test`、创建最小测试并执行 JDK 17 Maven 测试/编译。
- 真实环境 legacy `tools=[]` 与非法 JSON 的记录数量未知，必须先通过只读审计确认。
