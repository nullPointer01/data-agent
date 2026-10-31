# 迁移任务

- [x] T1：完整扫描和隔离验证基线、替换依赖、MyBatis/Flyway 配置。
- [x] T2：所有仓储与实体迁移，分页、关联、加密、ID/时间戳、CAS。
- [x] T3：EntityManager 评测和 JdbcTemplate 索引任务迁移，移除死代码。
- [x] T4：隔离 MySQL 行为验证、实际配置兼容性和启动验证。
- [x] T5：更新项目学习文档，检查旧依赖/旧路径残留并记录验证限制。
- [x] T6：按用户验收反馈删除重复列映射，统一 Collection 参数，检查最新 XML 自动映射与实际 SQL 行为。

涉及范围：pom.xml、src/main/java 全部主库持久层与必要调用方、src/main/resources MyBatis/Flyway 配置和 SQL、sql/init.sql、CLAUDE/README 及直接受影响的项目架构文档。保留既有 Ollama local 配置变更。

验证结果与范围见 [verification.md](./verification.md)。用户要求不添加项目测试类，检查程序仅放临时目录；没有伪造覆盖率或正式评审批准。
