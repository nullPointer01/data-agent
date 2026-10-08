# 任务
- [x] T1：注解提示词和官方 Flux 助手接入；文件 pom.xml、AgentChatAssistant、AgentChatService、McpModelService、GovernedToolSession。
- [x] T2：Flux SSE 生命周期、MVC 异步配置；文件 AnalysisController、AnalysisStreamService、AsyncConfig、MvcStreamingConfig。
- [x] T3：临时行为检查、实际 MVC HTTP SSE 检查、编译及文档；文件 /tmp/data-agent-flux-UQAPq4/*、CLAUDE.md、README.md、docs/核心逻辑详解/框架集成与消息生命周期.md、docs/核心逻辑详解/目录索引.md、docs/interview/当前进度与验收交接.md、本变更目录。
成功标准：系统消息唯一且动态隔离；真实 AiServices 同步/Flux、工具预检/暂停/恢复和预算通过；HTTP SSE 逐段到达而非完成后聚合；订阅取消/超时/溢出取消 Run。实现代理在本地 JDK17、脚本模型和嵌入式 MVC 服务验证，真实厂商/数据库审批联调另列待验。
