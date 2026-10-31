# MyBatis 主库持久化

## 当前结构

业务主库仍只有一个 MySQL `data_agent`。MyBatis 负责用户、权限、模型配置、Skill、Agent、会话消息、知识正文、文件元数据、记忆、统计、Trace、审批、运行状态、评测和持久索引任务。Milvus 存向量，Elasticsearch 提供 BM25，Redis 提供可选缓存；这些存储继续使用各自 SDK。

调用链为 `Controller → Service → @Mapper 仓储接口 → Mapper XML → MySQL`。保留 `Repository` 名称表达领域仓储职责，接口本身是标准 MyBatis Mapper，不再继承 JpaRepository。`MyBatisConfig` 只扫描显式 `@Mapper`，没有运行时解析方法名、反射拼 SQL 或另一套 ORM。

## 从哪里读代码

| 要了解的行为 | Java 入口 | SQL 或配置 |
|---|---|---|
| 一条知识如何保存 | `KnowledgeEntryRepository.save` | `mappers/KnowledgeEntryRepository.xml` |
| 会话历史如何读取 | `ConversationMessageRepository.findRecentBySessionId` | 同名 XML 的排序和 LIMIT/OFFSET |
| 用户、角色、权限如何关联 | `SysUserRepository`、`SysRoleRepository` | 嵌套 collection 查询及关系表 SQL |
| 模型 API Key 如何加密 | `EncryptedStringTypeHandler`、`CryptoUtil` | `ModelConfigRepository.xml` 显式指定 handler |
| 审批、恢复如何防重复 | `AgentToolApprovalRepository`、`AgentRunStateRepository` | 期望状态、version、租约 owner 条件 |
| 索引任务如何同事务提交 | `IndexTaskStore.enqueue` | `IndexTaskMapper.xml` 的合并版本与租约 SQL |
| Eval 结果如何保存 | `AgentEvalService`、`AgentEvalMapper` | `AgentEvalMapper.xml` |
| 启动时如何建表 | Spring Boot Flyway 自动配置 | `db/migration/V1__business_schema.sql`、V2 |

上表资源路径均相对 `src/main/resources`，Java 类位于 `src/main/java/com/ai`。

## 字段映射和集合参数

普通实体查询使用 `resultType`，由全局 `map-underscore-to-camel-case: true` 将 `memory_id` 等列自动映射为 `memoryId`，不逐字段重复声明 `resultMap`。枚举和时间类型使用 MyBatis 自带处理器。

只有模型 API Key、数据源密码的加密处理，以及用户→角色→权限的关联查询保留精简的 `resultMap`，同时显式启用 `autoMapping="true"`。索引任务是不可变 record，构造参数与列有异名，因此保留构造器映射；这些属于实际转换合同。

批量 ID 查询统一接收 `@Param("ids") Collection<...>`，XML 使用 `ids != null and !ids.isEmpty()` 与官方 `<foreach>` 展开绑定参数。空集合和 null 返回空结果，避免生成空 `IN` 或遗漏过滤条件而读全表。`Iterable` 的迭代器探测已删除；IDE 参数标红需与框架实际绑定异常区分，不能仅凭颜色认定 SQL 运行失败。

## 保存、事务和并发

MyBatis 不会自动检测对象字段变化。仓储 `save` 明确选择 insert/update，执行 ID、创建/更新时间准备；自增主键通过 `useGeneratedKeys` 回填。Service 修改从数据库读取的对象后必须显式保存，Agent Eval 原来的 Hibernate 脏检查也已改为显式 update。

`@Transactional` 仍由 Spring 管理，同一主库的数据修改和索引任务一起提交或回滚。用户保存与角色关联重写也在同一事务，任一关系写入失败会回滚主行。索引 enqueue 保留 MANDATORY，缺少来源事务就拒绝执行。

审批和 Run 更新保留原 SQL 的状态、版本、owner 条件；版本冲突不能当作成功。会话写入和 Eval 抢占保留 `SELECT ... FOR UPDATE`。MyBatis 本地缓存范围为 STATEMENT，避免同一事务中重新读取 CAS 状态时拿到旧版本。

分页 SQL 显式使用参数化 `LIMIT/OFFSET`，Token 分页用独立 count 组装 Page。保留 Spring Data 的分页 DTO，不代表保留 JPA；它来自项目现有的 Spring Data Commons。索引维护继续使用主键游标。

## 加密与数据兼容

模型 `api_key` 和数据源 `password` 在 XML 显式选择加密 TypeHandler；普通 String 保持标准处理。密文继续使用原 AES-GCM 的 `ENC:` 格式和相同密钥，既有密文无需重写，旧明文读取仍兼容。登录密码仍为 BCrypt，不能改成可逆加密。

Checkpoint 和索引任务载荷继续在业务边界显式加密，避免重复加密。已经删除旧 `EncryptedStringConverter` 和静态注入入口。

## 建表与后续改表

MyBatis 不会根据 Java 类自动建表。启动时 Flyway 使用同一个 MySQL 连接执行版本脚本，新增 `flyway_schema_history` 管理记录，不引入第二个业务数据库。

新库由 V1 创建现有全部表。已有非空库先按版本 0 建基线，再执行 V1 的 `CREATE TABLE IF NOT EXISTS`，保留已存在的表；V2 兼容缺失的每日配额/文件哈希列，以及旧 Run mode ENUM。当前业务库已做字段只读对照；更早或人工改过的库需另外核对历史升级脚本。

后续新增字段：同步修改模型、Mapper XML，并新增 `V3__说明.sql` 等脚本。不要修改已在目标环境执行过的 V1/V2，也不要恢复 `ddl-auto=update`。迁移失败会阻止启动，避免带着不完整结构继续服务。`DataInitializer` 只负责默认 RBAC 数据，不再临时修表。

外部数据分析连接器的 JDBC/Hikari 仍用于访问用户配置的异构数据库；它属于工具连接能力，不是主库持久化旁路。

## 验收与限制

本轮验证证据见 [迁移验证记录](../../openspec/changes/mybatis-persistence/verification.md)。先从登录、会话恢复和知识入库验收，再验证审批恢复及索引任务状态。临时验证未调用收费聊天模型，不能替代模型输出或浏览器验收。

## 更新记录

- 2026-10-09：主库统一为标准 MyBatis Mapper/XML，Flyway 接管结构，移除 JPA/Hibernate、EntityManager、JdbcTemplate 主库访问和索引任务空实体。
- 2026-10-09：删除 359 条重复字段映射，普通查询改用自动映射，统一批量 ID 的 Collection 合同和空集合防护。
