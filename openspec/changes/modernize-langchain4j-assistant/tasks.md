# 实施任务

- [x] 1. 升级并核对 LangChain4j 1.20.2 / Milvus beta30 依赖。
- [x] 2. 统一模型请求、异常、预算和流式完成语义。
- [x] 3. 普通聊天接入 AiServices / TokenStream；历史以原生消息装配，删除被替代实现。
- [x] 4. 静态核对 ReAct、Skill、路由与记忆提取调用兼容性，协议合同执行检查通过。
- [x] 5. RAG 融合排序与证据注入改用框架，核对分块身份、引用与通道保留。
- [x] 6. 复核记忆持久化与 Function Calling，说明保留业务适配层的原因并验证真实工具执行器。
- [x] 7. 生产源码 clean compile、43 项临时检查和静态审查；更新学习及交接文档。

## 第二批：用户已授权的深化改造

用户在上一轮四项建议后明确“可以的，继续搞”，据此继续实施，未新增审批门禁。基线沿用本 change；开始时已有的脏文件均为上一轮本会话改动。

- [x] 8. 结构化任务：Router/语义记忆接入 AiServices 类型返回、Schema 生成与严格 OutputGuardrail，删除重复 Schema/解析器。
- [x] 9. RAG：使用 DefaultRetrievalAugmentor 的转换、检索、聚合扩展点，保留身份过滤与单次检索证据，替换手工阶段编排。
- [x] 10. 故障治理：引入 Resilience4j 统一重试与熔断，迁移模型、Embedding、Reranker 的状态机，保留配置和业务分类。
- [x] 11. 流式生命周期：使用异步完成阶段贯通普通聊天到 SSE，跨线程恢复身份/Run，取消和终态只提交一次；可恢复工具循环仍保留同步协议边界。
- [x] 12. 553 个生产源码 clean compile 与 108 项临时合同检查通过；文档已更新，真实服务验收边界单列。

文件范围：上述任务涉及 mcp、memory/SemanticMemoryExtractor、agent/runtime/planning、rag、vector/EmbeddingCallExecutor、config/AsyncConfig 与上下文传播、Agent/Runtime/SSE 调用链及依赖配置。审批存储、数据库表和前端协议不变。

## 真实服务验收

实现和局部兼容检查已完成；以下依赖真实服务的结果尚未验证，不能因上面的任务完成而视为通过：

- [ ] 真实配置模型网关下的多轮聊天、登录恢复和流式取消。
- [ ] 同一黄金集下 Milvus/BM25/RRF/精排排名指标及用户隔离复验。
- [ ] 工具与子 Agent 委派、高风险审批跨进程恢复、Agent Eval 重验。
