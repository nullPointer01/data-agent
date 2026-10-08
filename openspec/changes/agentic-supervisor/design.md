# Supervisor 可切换学习实现

用户授权：为了学习接入 Supervisor，对比现有编排与框架的利弊。仅实现本变更，不归属既有脏工作区的改动。

## 设计

- ORCHESTRATED 且本次候选包含 delegateToAgent 时，可选 TOOL_CALLING / SUPERVISOR。local 默认 Supervisor，公共/生产保留 Tool Calling；嵌套专家沿用原执行方式，避免递归创建管理者。
- 使用官方 AgenticServices.supervisorBuilder；每次请求创建独立 Supervisor。通过公开 sequenceBuilder 给已授权专家创建具名适配器，稳定 ID 由服务端捕获，模型只生成 task。
- 同时提供父助手执行节点处理原有本地工具/Skill；排除 delegateToAgent 防止重复编排。历史、知识和文件上下文沿用已有装配。
- 所有专家调用经 AgentToolInvoker → Pipeline → AgentDelegationToolService → sequence，继续复用 RBAC、租户、预算、取消、防环路和工具 Journal。
- Supervisor 模型沿用当前 GPT 配置及受治理模型适配器。每次决策计入根 Run 迭代，不采用框架次数耗尽后返回最后结果的默认行为。
- Supervisor 同步决策/执行，发布阶段事件；收集执行结果后用现有 AiServices Flux 流式汇总。结果是证据，不是系统指令。无真实执行结果则失败，不伪造完成。
- 本轮不实现整个 Supervisor 图的持久化恢复。Supervisor 内遇到审批必须在暂停/落库前明确拒绝，提示使用原 Tool Calling 路径，不能写入只有叶节点现场的错误 Checkpoint。该边界随任务快照传播，退出后清理。
- 响应元数据记录实际引擎、选择顺序、阶段调用数/Token/耗时；现有 Run 总指标仍为准。禁用框架包含原始任务参数的 INFO 日志。

## 验收

真实框架配脚本模型验证多专家顺序、未知专家、错误/预算/取消短路、上下文隔离、结果流式输出；验证两种引擎切换和无绑定/无候选不走 Supervisor。复用既有治理与 Flux 回归。无仓库测试类，无真实业务数据写入；真实 GPT 行为和性能留待用户运行验收。
