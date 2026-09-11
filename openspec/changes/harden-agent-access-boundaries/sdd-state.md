# SDD State

- Change: `harden-agent-access-boundaries`
- Project: `data-agent`
- Mode: Full SDD
- Java baseline: 17
- Framework: Spring Boot 3.2 + Spring Security + React/Vite
- Phase: Coding complete; static verification complete
- Scope: 管理端点保护、个人模型目录、数据库实时认证主体、服务端注册租户、知识同步个人所有权
- Anti-scope: 新角色体系、资源 ACL、用户确认流程、子 Agent/Tool/Skill 开放、RAG 与模型协议变更
- Verification strategy: 静态端点矩阵、调用链、敏感字段和 diff 检查；不测试、不编译、不构建
- Status: 7/7 tasks complete
