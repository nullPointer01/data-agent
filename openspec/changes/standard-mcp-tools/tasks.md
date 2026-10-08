# 任务与文件

- [x] T1 官方 MCP 依赖、配置、连接和固定工具映射：pom.xml、src/main/java/com/ai/integration/mcp/*、application.yml。
- [x] T2 目录/租户/工具执行治理接入：AgentToolRegistry、AgentToolInvoker、AgentToolAdmissionService、AgentToolPolicyEngine、AgentCapabilityRegistry、AgentCapabilityService；参数校验改用 Schema 库：AgentToolArgumentValidator。CapabilityPackPicker 增加外部工具逐项绑定，补齐已有个人/专家设置入口。
- [x] T3 /tmp 实际 MCP HTTP 协议和治理验证、既有回归；verification.md 记录测量和缺口。
- [x] T4 README、CLAUDE、package-info 和核心逻辑/交接文档更新，讲明标准 MCP 与旧 mcp 包的差别。

SC1 对应 T1/T3；SC2-4 对应 T2/T3；SC5 对应 T4。现有脏工作区仅归属本轮新增片段。
