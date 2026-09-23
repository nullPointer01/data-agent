# Verify Report

## Result

`modernize-agent-context-routing` 已完成实现与静态验证。

以下自动化结果是删除测试源码前完成的验收记录。验收完成后，用户按项目既定约束要求删除全部 `src/test` 测试源码与测试专属依赖；生产实现和真实运行验收结果不受影响。

## Automated Checks

- JDK 17 全量后端测试：51 tests，0 failures，0 errors，0 skipped。
- 请求规划黄金集：32 条样本逐条执行真实 `PersonalAgentRequestPlanner` 并断言 intent、mode 和四类 Context Plan。
- 前端生产构建：Vite 1734 modules transformed，构建成功。
- `git diff --check`：通过。
- 旧 `ragRequired/memoryRequired/historyRequired`：仅保留在旧 Trace 读取兼容层，新 Run 不再写入旧合同。

## Covered Behavior

- 四类上下文的 `REQUIRED / OPTIONAL / OFF` 三态计划。
- Session 历史与语义记忆、用户画像、知识库独立装配。
- 高确定性规则与受限 JSON Mode Router 的混合路由。
- standalone retrieval query 与原始回答问题分离。
- OPTIONAL 检索只通过已授权工具进入 ReAct。
- `CHAT + OPTIONAL` 非法组合的规划器规范化与计划不变量保护。
- Trace 的 Context Plan、Router 来源、规划耗时和预算范围证据。
- Agent Eval 的期望/观察 intent、mode、Context Plan 及三项准确率。

## Runtime Verification

2026-09-21 使用隔离 QA 用户、真实模型、MySQL、Milvus、Elasticsearch 和 Redis 完成端到端验证：

1. 确定性通用知识请求生成 `GENERAL_KNOWLEDGE + CHAT`，根 Run 完成，模型调用 1 次。
2. 同一会话的模糊请求“找一下相关资料再回答”调用 JSON Mode Router；Trace 记录 Router 耗时 8955ms、
   `includedInRunBudget=false`、`budgetScope=USER_MODEL_QUOTA`，并生成补全后的 standalone query。
3. Router 生成 `REACT + knowledgeBase=OPTIONAL`，候选工具只有 `searchKnowledge`；实际 Tool Journal 记录
   `searchKnowledge / SUCCESS / authorized=true`，根 Run 为 2 次模型调用、1 次工具调用。
4. 明确知识请求生成 `PERSONAL_KNOWLEDGE + CHAT + knowledgeBase=REQUIRED`，Router 未调用、工具调用为 0；
   模型在一次业务调用中依据预加载证据返回唯一验收码和 `[R1]` 引用。
5. 单样本 Agent Eval 真实运行完成，样本为 `ACHIEVED`；任务完成率、意图准确率、模式准确率和 Context Plan
   准确率均为 100%，期望与观察证据可在报告中下钻。
6. 应用启动、前端入口和 `/actuator/health` 均返回 200；健康状态为 `UP`。

验收后已按精确 ID 删除隔离知识及其检索索引、记忆、会话、Trace、Run、Eval 数据集、临时角色和 QA 用户；
原有 `super/testuser1/testuser2` 未修改。已有数据库部署时仍可按顺序执行
`sql/upgrade-agent-eval.sql` 和 `sql/upgrade-agent-context-routing.sql`。
