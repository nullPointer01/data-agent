# Agent Harness 求职演示手册

这四条路径用于证明项目不是“套了聊天页面的大模型 Demo”，而是一个可配置 Agent 在受控 Runtime 中完成任务、返回结果并留下工程证据的 Java Agent Harness 参考实现。

## 演示前准备

1. 使用 JDK 17 启动当前代码，确认 MySQL、Milvus、Elasticsearch、Chat API 和 Embedding API 可用。
2. 对已有数据库执行本次变更需要的幂等升级 SQL；新库直接执行 `sql/init.sql`。
3. 准备普通用户和管理员两个不同账号。普通用户发起任务，管理员只处理控制面动作。
4. 在“我的 Agent -> 设置”确认模型可用，并检查 Tool、Skill、Sub Agent 的风险和可用状态。
5. 打开浏览器开发者工具 Network，保留一次 `/api/v1/analysis/analyze/stream`；同时准备“运行详情”和管理员 Trace 页面。

演示结论只引用当前页面、SSE、owner Run 详情、Trace 和数据库中的真实结果。没有跑出的指标或事件统一说“未验证”。

## 路径一：知识证据闭环

### 要证明什么

Agent 不只是生成一段看似正确的文字。它先在当前用户知识范围内检索，回答旁能看到引用，Run Inspector 能把检索证据与同一个 `runId/traceId` 关联起来。

### 准备

在普通用户“知识”页面新增一条带唯一事实的资料，例如：

```text
蓝鲸登录策略 K-2026-0906：同一账号十分钟内连续输错五次密码，锁定十五分钟。
```

等待索引完成后，先用 Swagger 的 `POST /api/v1/rag/retrieve` 查询“蓝鲸登录策略”，确认能返回该来源。这个步骤只验证检索，不代表 Agent 已完成任务。

### 发起任务

在“我的 Agent”提问：

```text
蓝鲸登录策略中，连续输错几次会锁定？锁多久？请给出依据。
```

可展开“成功标准”，增加答案包含“五次”和“十五分钟”的确定性标准。

### 预期证据

- 回答旁显示“参考了你的知识库”，展开后能看到来源名、引用编号和片段。
- Run Inspector 中 `runId` 与 `traceId` 一致，时间线包含知识检索和终态。
- 技术状态 `COMPLETED` 与任务结果 `ACHIEVED/NOT_ACHIEVED/NOT_EVALUATED` 分开展示。
- 刷新并重新打开会话后，带 `runId` 的历史回答仍能读取 owner-safe 运行摘要；历史事件不完整时明确标记。

### 故障注入

停用或删除这条个人知识后重新提问。预期不再引用旧来源；如果 Elasticsearch 不可用，系统不得把 SQL LIKE 冒充 BM25 降级。恢复依赖后再次检索，引用应来自重新可用的真实索引。

### 不能宣称

- 一次命中不能证明 RAG 召回率已经提升。
- 没有在同一黄金集上比较 Vector、BM25、RRF 和 Reranker 前，不能声称 Cross-Encoder 一定更好。
- 引用存在只证明找到了证据，不等于最终任务必然达成。

## 路径二：高风险 Tool 审批与恢复

### 要证明什么

模型只能建议动作，不能给自己授权。高风险 Tool 在真正执行前暂停，管理员批准后由持久化 Run 恢复；恢复时重新读取用户、审批人、Tool 和策略的当前权限。

### 准备

`local` 和 `prod` Profile 均已显式开启 durable、sandbox tool 和 `updateHotelPrice`，确认使用目标 Profile 后重启即可。

`APP_ENCRYPTION_KEY` 必须使用稳定的部署 Secret，不能写入仓库。管理员角色需要 `agent:approval:review`，普通用户需要 `app:use`。在个人 Agent 能力目录确认 `updateHotelPrice` 显示为高风险、需要审批且当前可用。

### 发起任务

普通用户输入：

```text
把演示酒店 H-100 的大床房在 2026-09-20 的价格改成 699 元。
```

### 预期证据

- SSE 返回 `approval_required`，Run 进入 `WAITING_APPROVAL`，旧流关闭但不把等待误报为业务完成。
- 管理员在 Control Plane 的“动作审批”看到同租户申请；发起人不能审批自己的动作。
- 管理员批准后 worker 领取 Run 为 `RESUMING`，通过原 Tool Pipeline 执行，再进入终态。
- `GET /api/v1/agent-runs/{runId}` 能看到暂停、领取、恢复完成或失败事件，不返回 Checkpoint 密文和原始敏感参数。
- `hotel_rate_sandbox` 只有一条对应动作；重复恢复读取原结果，不重复改价。

### 故障注入

Run 等待审批时，先禁用发起账号或撤掉其 `app:use`，再批准。预期恢复前重授权失败并给出 `AUTHORIZATION_REVOKED` 等安全原因，Tool 实现尝试数为零。演示后恢复账号权限。

### 不能宣称

- `updateHotelPrice` 只写隔离沙箱，不连接真实酒店系统。
- 数据库租约不是跨系统 exactly-once；真实支付、发布或改价仍需要下游幂等键、对账和补偿。
- 当前只有单级审批，没有多级会签、转交或通用图节点回放。

## 路径三：预算终止与配置恢复

### 要证明什么

ReAct 不是无限循环。模型调用、工具调用、迭代、Token 和 deadline 都由服务端 Run Control 限制；故障恢复是修改受控配置后发起新 Run，而不是篡改原 Run 的终态证据。

### 准备

记录当前配置，然后在演示环境临时设置并重启：

```bash
AGENT_RUNTIME_MAX_MODEL_CALLS=1
```

在管理员 Agent 配置中选择一个显式 `react` 模式的测试 Agent，并确保它能使用 `queryHotelOccupancy`。这样第一次模型调用选择 Tool 后，观察结果回填所需的第二次模型调用会被预算拒绝。

### 发起任务

通过 Swagger 调用 `POST /api/v1/analysis/analyze/stream`，传入测试 Agent 的真实编号：

```json
{
  "question": "必须先查询杭州上周的酒店经营指标，再判断是否应该调价。",
  "agentId": "替换为测试 Agent 的真实 agentId"
}
```

### 预期证据

- 首次模型调用和 `queryHotelOccupancy` Tool 证据仍保留。
- 第二次模型准入被拒绝，Run 进入 `BUDGET_EXHAUSTED`，`terminationReason=MODEL_CALL_LIMIT`。
- Run Inspector 展示模型调用已用量、工具调用已用量、耗时和终止原因；不会把技术终止包装成任务成功。

### 恢复步骤

还原原 `AGENT_RUNTIME_MAX_MODEL_CALLS` 并重启，再用同一输入创建新 Run。比较两个不同 `runId` 的终态和预算证据。原失败 Run 保持不可变，新 Run 正常完成才说明配置恢复有效。

### 故障注入替代项

也可以降低 `AGENT_RUNTIME_MAX_ITERATIONS` 观察 `ITERATION_LIMIT`，或降低 Context Governor 的模型窗口触发显式上下文拒绝。是否稳定触发取决于模型是否选择工具，未观察到目标原因码时不能硬说演示成功。

### 不能宣称

- timeout 和取消是协作式的，不能强杀忽略线程中断的第三方请求。
- 单次模型响应返回后才知道准确 Token，可能有一次在途 overshoot。
- 当前无效循环指标只权威识别 `ITERATION_LIMIT`，尚未实现重复参数语义指纹。

## 路径四：组合能力与模式边界

### 要证明什么

Agent 不是代码里写死的一组工具。Profile 可以组合模型、System Prompt、Tool、Skill 和子 Agent，而 Harness 会在保存和每次 Run 时重新校验能力，并让父子 Agent 共享同一个根 Run 的预算与证据。

### 准备

1. 创建一个启用的子 Agent，为它配置独立模型、角色 Prompt 和只读 Tool。
2. 在“我的 Agent -> 设置”绑定至少一个 Tool、一个 Skill 和该子 Agent，并选择 `Orchestrated`。
3. 保存后重新打开设置，确认三类稳定身份和数量无损回显；不要把目录中不可用的能力当作可执行能力。

### 发起任务

输入一个确实可拆分的任务，例如：

```text
先根据我的知识总结登录限制，再委派代码审查 Agent 检查下面的修复方案是否遗漏并发边界，最后合并结论和依据。
```

### 预期证据

- Run 模式为 `ORCHESTRATED`，时间线出现 `delegateToAgent` 的受治理 Tool 事件。
- 子 Agent 使用自己的模型和 Prompt，但 `runId` 不分裂；根 Run 的模型、Tool、Token 与 deadline 用量继续累计。
- 将根 Agent 改为 `react` 后，同一子 Agent 绑定仍保留但不会暴露委派能力；改为 `chat` 后三类可调用能力都不暴露。
- 改回 `auto` 后，简单问候走 Chat；复杂任务只有在存在当前有效子 Agent 时才进入 Orchestrated，否则走 ReAct。

### 故障注入

父 Run 开始前停用或解绑子 Agent，再执行同一任务。预期 Runtime 重新解析能力后不允许委派。也可以构造 A 绑定 B、B 再绑定 A，保存期应拒绝已知引用环；运行时仍有最多 3 层和活动路径环路保护。

### 不能宣称

- 当前是模型在受控能力集合内选择下一步，不是拖拽式工作流或任意 DAG 引擎。
- 静态调用链只能证明治理代码存在；没有真实父子 Run 的事件和预算数据时，不能声称委派效果已经通过运行验收。
- 独立委派线程池解决的是线程池饥饿风险，不等于容器级隔离或可强制终止第三方调用。

## 面试讲法

用同一结构讲四条路径：问题是什么、Harness 在哪个边界拦截、留下了什么可验证证据、还有什么边界。核心句可以保持为：

> Agent 负责理解任务和选择下一步，Harness 负责让每一步有权限、有预算、可暂停、可恢复、可追踪。我的项目展示的不是模型有多聪明，而是如何把不稳定的模型行为放进一个可治理的 Java 运行时。
