# 实施任务

- [x] T1：请求规划纳入同一 Run，覆盖取消、预算、事件和持久状态。范围 runtime、durable、mcp、事件消费方。
- [x] T2：会话保存失败传播，索引转持久后台任务。范围会话服务、记录器、持久任务及相关 DTO。
- [x] T3：知识索引任务、幂等补偿、版本与状态查询。范围 knowledge、vector、rag、repository、model、sql 及必要前端状态展示。
- [x] T4：统一 Token 计数与按预算选择历史，保留完整会话轮次和工具组。范围 mcp、config、context、ConversationSession。
- [x] T5：运行临时合同检查、构建、静态一致性检查，更新文档。未验证真实服务单列。
