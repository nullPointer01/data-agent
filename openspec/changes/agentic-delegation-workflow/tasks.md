# 实现任务

- [x] T1 通用提示词：AgentSystemPrompts.java、AgentProfileService.java；验证空角色、旧内置角色与自定义角色的行为。
- [x] T2 框架顺序工作流：pom.xml、新 agent/agentic/AgenticDelegationWorkflow.java、AgentDelegationToolService.java；真实框架负例和成功路径检查，保留根 Run、权限和循环边界。
- [x] T3 编译与回归：临时检查 /tmp/data-agent-agentic-rW9Tks；不向仓库新增测试类，不调用真实厂商、不写业务库。
- [x] T4 学习与交接：README.md、CLAUDE.md、docs/核心逻辑详解/框架集成与消息生命周期.md、目录索引.md、docs/interview/当前进度与验收交接.md、本变更 verification.md 与 sdd-state.md。解释 @Agent、sequenceBuilder、AgenticScope、框架与 Harness 分工。
