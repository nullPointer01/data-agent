# SDD Assumptions

## Confirmed

- [A001] OpenAI/GPT 必须作为明确的一级厂商展示，而不是藏在自定义兼容入口中。
  - 来源: 用户反馈与现有 UI 调查
  - 置信度: 高
  - 验证: 提案 SC-1 与 UI 规格

- [A002] 用户希望先优化模型接入和管理体验，再继续 RAG 或其他 Agent 能力。
  - 来源: 用户明确要求
  - 置信度: 高
  - 验证: 本 change Anti-Scope

- [A003] 默认不新增/运行测试或 Maven 编译；用户明确要求运行页面体现 UI 变更时，允许执行必要的前端生产构建。
  - 来源: 用户历史指令与本次页面整改要求
  - 置信度: 高
  - 验证: tasks Test Strategy Override

- [A004] 已有 `model_config` 表与存量记录必须原位兼容。
  - 来源: 当前运行链路与最小迁移原则
  - 置信度: 高
  - 验证: SC-8

## Unverified

- [A005] 当前目录中的 13 家国产厂商在用户实际账号和区域下均提供可用的 OpenAI-compatible Chat 端点。
  - 来源: 现有 Catalog 与厂商公开兼容路径
  - 置信度: 中
  - 验证: 用户主动提供凭据后逐厂商 Probe；目录不把静态声明当成运行保证

- [A006] 各厂商 `/models` 响应都使用标准 `data[].id` 结构。
  - 来源: OpenAI-compatible 常见实现
  - 置信度: 中低
  - 验证: Discovery 失败必须允许手填；后续按真实厂商响应补 Adapter

- [A007] 最大 32 输出 token 的最小 Chat 请求足以验证绝大多数普通与推理模型的基础连接。
  - 来源: 成本与兼容性的折中
  - 置信度: 中
  - 验证: Probe 只以调用成功和 finish reason 判断基础连通性，正文直接丢弃；高级能力需单独验证

- [A008] 第一阶段无需持久化 last probe status/latency。
  - 来源: 用户优先解决接入体验，避免数据库迁移
  - 置信度: 中高
  - 验证: 使用过程中若管理员需要跨会话健康状态，再创建独立 change

## Invalidated

- [A009] “引入全部厂商 SDK 才能支持国内所有模型”。
  - 原因: 现有目标厂商大多提供 OpenAI-compatible 端点，共享协议更能减少重复实现；专有协议另行扩展
