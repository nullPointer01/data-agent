## Why

旧版 `AgentProfile.tools` 既承担 JSON 存储，又使用“空列表代表全部工具”的兼容语义；当前解析失败也返回空列表，因此损坏数据可能被解释为全部工具。这个问题已经越过普通代码整洁度，成为 Agent 能力授权的 fail-open 风险，必须在继续重构 Runtime 前先建立失败关闭的配置边界。

## What Changes

- 将 legacy Tool 配置区分为“未配置”“有效列表”“损坏数据”三种状态，损坏数据不得转换为空列表或全部工具。
- 把 legacy Tool 与统一 Capability Binding 的 JSON 编解码从 JPA 实体职责中移出，集中到可复用、可验证的配置编解码边界。
- 统一保存、读取、能力目录和运行时解析对 `null`、`[]`、有效列表及非法 JSON 的解释。
- 提供只读审计和显式迁移步骤，使遗留 Profile 可以逐步转换为 `capability_bindings`，不在应用启动时隐式改写用户配置。
- 为配置状态矩阵和运行时失败关闭行为补充最小自动化测试；是否恢复执行这些测试，由本提案确认时一并决定。

## Capabilities

### New Capabilities

- `agent-capability-configuration-safety`: 定义 Agent 能力配置的存储状态、失败关闭规则、兼容读取和显式迁移边界。

### Modified Capabilities

无。现有统一能力绑定规格仍位于尚未归档的 `productize-agent-harness-experience` change 中，本变更只补充独立的持久化安全合同，不复制其 Tool、Skill、子 Agent 执行要求。

## Impact

- 后端：`AgentProfile`、能力绑定编解码边界、`AgentCapabilityService`、`AgentCapabilityRegistry`。
- 数据：增加只读审计和显式迁移 SQL；不删除字段，不自动修改现有 Profile，不改变数据库表结构。
- API：合法配置的现有请求/响应保持兼容；损坏配置从“静默返回空列表”改为稳定错误，属于有意的安全收紧。
- 依赖：不新增第三方依赖，不拆 Maven 模块，不修改模型、RAG、Tool Pipeline 或前端。

## Verification Strategy

- 项目当前没有 JUnit 或 `spring-boot-starter-test` 依赖；建议仅新增 test-scope 的 `spring-boot-starter-test`，使用 JDK 17 直接覆盖 Codec 和能力解析，不复制生产分支。
- 状态矩阵至少覆盖：legacy `null`、legacy `[]`、有效 Tool 列表、非法 JSON、显式 `capability_bindings=[]`、有效统一绑定、非法统一绑定。
- 若恢复测试，关键失败关闭路径要求 100% 分支覆盖，本变更新增代码整体行覆盖率不少于 80%；不使用真实模型、Milvus、Elasticsearch 或付费 API。
- 用户此前要求“不新增/运行测试、不执行编译”；新增测试依赖、创建测试源码以及执行 Maven 都必须在进入 Coding 前获得显式确认。在此之前只执行 OpenSpec、静态调用链和 SQL 只读审计验证。

## Boundaries

- **Always**：非法或不可判定配置失败关闭；保留租户、RBAC、运行时策略和审批交集。
- **Ask first**：执行数据迁移、修改真实数据库、删除 legacy 字段、运行 Maven 编译或测试。
- **Never**：继续用空集合承载解析失败；在应用启动时静默扩权或自动重写用户能力配置。
