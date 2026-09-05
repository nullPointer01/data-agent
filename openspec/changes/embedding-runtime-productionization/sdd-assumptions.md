# SDD Assumptions

- **A001 Confirmed**：目标岗位是企业 Agent/AI 平台服务端，重点是 Embedding 工程接入与治理，不是训练模型或自研推理引擎。来源：用户对 Harness、企业 Agent 和职业方向的连续确认。
- **A002 Confirmed**：Embedding 保持 Harness 外部的 RAG/Memory 基础能力，本 Change 不修改 Chat/ReAct/Orchestrated Runtime。来源：用户问题与已对齐架构。
- **A003 Confirmed**：用户要求不新增或运行测试、不编译、不启动应用；只允许静态验证。来源：用户长期约束。
- **A004 High confidence**：LangChain4j 0.30 支持 `embedAll`、timeout、maxRetries、dimensions，EmbeddingStore 支持 `addAll`。验证：已对本机 Maven 依赖执行 `javap` 静态检查。
- **A005 Unverified**：未来选定的 OpenAI-compatible 服务支持数组 input 和可选 dimensions。验证：获得真实 Endpoint 后分别发送单条、两条批量和可选维度请求。
- **A006 Unverified**：batch size 32、timeout 30s、maximum attempts 3、failure threshold 5、open duration 30s 适合真实服务。验证：重建与固定查询集采集吞吐、429、失败率和 P95 后调参。
- **A007 Confirmed**：输入前缀或请求输出维度变化会改变向量契约，必须提升 indexVersion 并重建，不能复用旧 Collection。来源：现有 Embedding Profile/Collection 设计。
- **A008 Unverified**：真实服务的错误会以当前 LangChain4j/OpenAI4j 异常类型暴露。验证：真实 Endpoint 的 401、429、5xx 或可控故障演练；未验证前不宣称分类覆盖所有厂商。
