# Supervisor 接入验证

日期：2026-09-30。结论：PASS_WITH_NOTES（实现与临时确定性验证完成，真实 GPT / 浏览器验收未进行）。

## 已完成

- JDK 17 Maven `-Dfrontend.skip=true -DskipTests compile` 通过。
- 前端 Vite 构建通过，输出到 `/tmp/data-agent-supervisor-c7HhKm/frontend`，未覆盖工作区已有静态构建产物。使用后端静态页面时需按原部署流程重新构建前端；Vite 开发模式直接读取更新后的源码。
- `SupervisorCheck`：最终 366 项断言。使用真实 Agentic Supervisor / sequence / AiServices / Flux 与脚本模型；覆盖两专家选择顺序、稳定 ID 映射、历史 AI 角色保留、全部结果汇总、过程/Token 事件、实际阶段成本、未知/未绑定节点、缺参/额外参数/陈旧 task、提前 done、工具失败不重试、取消、迭代与模型预算、必需工具约束、审批前拒绝及原 Pipeline 安全原因分类、线程作用域恢复与 12 个并发调用。
- `ConfiguredRoutingCheck`：8 项断言。直接调用生产 `ConfigurableAgentExecutor`，验证同步/流式入口在两引擎间切换、实际 engine 元数据和 REACT 仍走原路径。
- `AgenticWorkflowCheck`：97 项既有工作流/委派入口断言通过，覆盖租户/绑定/启用、防环路/深度、计划传参、取消和共享预算。
- `GovernedToolsCheck`：172 项既有真实 AiServices/Run/工具 Pipeline/Checkpoint/原生续跑断言通过。
- `PromptFluxCheck`：19 项提示词与框架 Flux 断言通过。
- `FluxLifecycleCheck`：24 项流生命周期与嵌入式 MVC HTTP 断言通过，包含 UTF-8、取消与流增量输出。
- `git diff --check` 通过。没有新增仓库测试类、没有真实模型调用、没有业务数据库写入、没有 Git 提交。

临时源码与产物：`/tmp/data-agent-supervisor-c7HhKm`；复用 `/tmp/data-agent-agentic-rW9Tks`、`/tmp/data-agent-flux-UQAPq4`。临时脚本不作为长期项目功能提交。断言数量包含每次调用/并发重复的边界断言，不等于独立业务场景数。

## 边界与待验

- Agentic 仍为 beta。按精确版本源码核对，未导入框架 internal API。
- Supervisor 决策/协作是同步调用，最后汇总使用真实框架 Flux。流式汇总额外消耗一次业务模型调用（底层有限重试若发生仍按原治理记账）。不能宣传整条 Supervisor 是非阻塞 TokenStream。
- 真实 GPT 的 JSON Object 兼容性、专家选择正确率、平均 Token 与 P95 未测。脚本模型测试只能证明调度与边界，不能证明生成质量或性能收益。
- 未实现整图持久化恢复；需要审批的动作在暂停、写 Checkpoint 和真实执行前拒绝。已有步骤可能已经完成，不能盲目重跑有副作用的完整任务。未自动回退另一个引擎重放动作。
- 本轮没有迁移数据库或复制一套旧 ReAct 实现。现有 Tool Calling 路径也使用 AiServices，比较范围是编排分工。
- 实际操作、输入和断点见 `docs/核心逻辑详解/Supervisor与原编排对照.md`。
