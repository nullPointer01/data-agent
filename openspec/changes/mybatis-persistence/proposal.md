# MyBatis 持久层替换

用户于 2026-10-09 明确要求全部换成 MyBatis，本次直接完成实现和验证。

将主业务 MySQL 的 JPA/Hibernate、EntityManager 和 JdbcTemplate 业务 SQL 统一迁移为标准 MyBatis Mapper + XML；保留现有表、字段、API 和业务行为。外部动态数据源工具继续通过 JDBC 协议访问用户配置的数据源，Milvus/ES/Redis 客户端不属于关系型 ORM 替换范围。

验收：无 JPA/Hibernate 依赖或注解；全部 Mapper XML 可加载；既有库只读兼容检查通过；隔离 MySQL 验证 CRUD、关联、加密、事务回滚、审批/租约 CAS、分页和评测显式保存；JDK17 编译及启动验证通过。验证程序只放临时目录，不添加项目测试类。

不删除用户数据、不重建业务数据库、不改 Agent/RAG 架构、不调用付费模型。用 Flyway 版本迁移替代 Hibernate 自动更新表；历史库保留已有数据并记录迁移历史。
