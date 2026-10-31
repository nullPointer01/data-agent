# MyBatis 主库迁移验证

日期：2026-10-09。结论：实现完成；主库集成检查通过，浏览器与真实 Agent 模型链路仍需重启后验收。

## 验收反馈：自动映射与集合参数

用户指出普通字段的手工映射重复，且 IDE 将 foreach 的 ids 标红。本轮删除 21 个纯标量 resultMap 和 359 条重复列映射；普通查询使用 resultType + 全局驼峰自动映射。加密和 RBAC 仅保留必要映射并开启 autoMapping，异名 record 保留构造器合同。

21 个批量 ID 方法统一为 @Param("ids") Collection，XML 不再调用 iterator().hasNext()，使用 null/isEmpty 守卫与 foreach。截图不是服务器异常证据；原 @Param 名称本已匹配，未将 IDE 标红描述成已证实的运行故障，也未实际读取 IDE 当前检查结果。

验证最新源目录中的 23 个 XML：在现有 30 表结构的隔离副本上实际执行，852 项断言通过，新增普通实体所有标量字段的回读对照，以及 null/空 ID 集合检查。验证覆盖布尔默认模型标志、枚举、时间、加密、RBAC、事务、CAS、分页、Eval 和索引任务。临时库已删除，未写业务数据。

静态核对全部 XML statement ID 与 Java 方法、21 个 @Param + Collection 合同及现有两处调用方集合类型，git diff --check 通过。本轮遵守项目不主动构建约定，未重新全量编译项目；临时集成程序读取最新 XML，Java 运行类复用前轮编译产物，因此 Collection 签名调整本轮仅做源码合同检查。下方编译、完整启动和风格报告属于前轮迁移结果，不是本轮重复执行的结果。

## 实现范围

- 移除 Spring Data JPA/Hibernate 依赖、全部 JPA 元数据、EntityManager 和主库 JdbcTemplate SQL。
- 21 个原仓储改为标准 MyBatis Mapper/XML；另外新增 AgentEvalMapper、IndexTaskMapper，共 23 个 XML。
- 显式保留 ID 生成、生命周期时间戳、自增键回填、RBAC 关系、事务、分页、聚合、加密、状态/版本/租约条件。
- Eval 抢占、计数和完成都显式保存，不再依赖 ORM 脏检查。
- Flyway V1/V2 管理新库建表和已知旧结构兼容；DataInitializer 只写默认 RBAC。
- 删除 EncryptedStringConverter、静态转换器注入、只用于旧 ORM 建表的 IndexTaskEntity，以及 flush/saveAndFlush 调用。
- 保留此前 Ollama local 配置和 ICU Docker 部署成果，没有重建真实索引。

## 环境与隔离

Java 17.0.9、Maven 3.8.8、MySQL 8.0；MyBatis 3.5.14 / Starter 3.0.3；Flyway 9.22.3。

业务库 `data_agent` 只读取 information_schema 和 SHOW CREATE TABLE，核对全部插入字段。新建唯一命名的临时库验证空库启动，并将现有 30 张表的结构复制到另一临时库验证实际旧库结构；不复制业务行。

临时程序位于 `/tmp/data-agent-mybatis-verify-h7X55L`，没有新增仓库测试类。每次运行 finally 删除本次临时数据库，成功和失败尝试均已清理。未清空、迁移或写入业务库；Flyway 会在用户下次正常启动时接管该库。

## 已通过的检查

1. JDK17 `mvn -B -Dfrontend.skip=true -DskipTests clean compile`，555 个生产源码编译通过。
2. Maven dependency tree 确认运行依赖没有 Hibernate、Spring Data JPA 或 Jakarta Persistence；MyBatis/Flyway 依赖存在。
3. 23 个 Mapper XML 在真实 Spring 容器加载；普通 String 默认处理器保持标准 StringTypeHandler，缓存范围为 STATEMENT。
4. 21 个仓储新增、读取、更新、生成 ID、创建时间、自增键回填与空 ID 集合处理通过。实际执行 194 个 SELECT 方法与 39 个更新/删除方法；部分方法使用不存在的 ID 验证 SQL，不能解释成每一种业务效果都已端到端验收。
5. AES-GCM 密文写入、解密读取、旧明文读取、普通列不误加密和数据源密码回读通过；测试只使用合成密钥文本。
6. 用户→角色→权限嵌套映射通过；关系表写入失败会同时回滚用户主行修改及原角色关系。
7. 审批同版本重复决策只成功一次；两个线程竞争同一 Run 租约仅一个成功，错误 owner 不能续租，过期实体保存拒绝，跨租户查询为空。
8. Token 分页 count/offset、SUM 和分组统计通过。
9. 索引任务要求外层事务；认领唯一、续租、旧版本完成后重新排队、最新版本完成、来源身份过滤、游标投影和事务回滚通过。
10. AgentEvalMapper 的结果写入、版本更新和租户过滤通过；AgentEvalService 使用真实 Mapper、事务与计算器，模型入口为临时替身，运行从 PENDING 抢占到 COMPLETED，样本计数和报告确实保存。没有调用真实模型。
11. 沙箱 INSERT IGNORE 同一动作重复执行不重复写入。
12. Flyway 空库迁移、重复 migrate、非空旧库 baseline=0，以及模拟缺少 daily_token_limit/content_hash、旧 mode ENUM 的升级通过，已有合成用户保留。最终镜像结构运行共 536 项断言，包含字段兼容性检查；前一轮空库运行 535 项断言通过。
13. 真实 Spring Boot 使用隔离 MySQL 和随机端口完整启动；MyBatis/Flyway 生效、默认 RBAC 初始化成功、没有 entityManagerFactory。真实 HTTP 注册 201、登录 200、重复注册 400；应用正常关闭，临时库已删除。
14. 本轮全部修改/新增 Java 的 Checkstyle 问题为 0；全项目仍有 99 项既有风格问题，未以 Maven 的成功退出冒充全项目风格全绿。
15. `git diff --check` 通过；生产代码无 JPA/Hibernate/EntityManager/JdbcTemplate/flush/saveAndFlush 残留，配置无 spring.jpa/ddl-auto。

## 验证发现并修复

- 原手工初始化脚本漏 content_hash，过去由 Hibernate 补列；已补入 V1/手工脚本，并给 V2 加守卫升级。
- SkillConfig 同时暴露 Boolean getEnabled 和 boolean isEnabled，MyBatis 无法确定同名属性；删除未被调用的 getEnabled，保留当前业务使用的 isEnabled。
- DataSourceConfig、KnowledgeSyncConfig、SkillPromptHistory 的旧回调名称为 prePersist/preUpdate；统一为显式 onCreate/onUpdate，仓储创建时调用，补回 UUID/时间戳初始化。
- XML 集合条件为空时返回空结果或不更新，不生成非法 IN SQL。

## 实际限制与下一步

没有新增持久测试类或覆盖率报告，没有完成正式 sdd-verify 流程。上述是本次实现的集成验收证据。

完整启动使用本地 Ollama Embedding 与现有 Milvus 可达性检查；聊天端点使用不可达临时地址，未请求收费模型，未执行知识上传后的真实 Milvus/ES 双写，也未进行浏览器和审批整链路验收。

用户正常重启后按顺序检查：已有账号登录与原会话恢复；新建/改名会话；知识或文件入库后的索引任务状态；个人记忆保存；高风险工具审批与恢复；统计和 Eval 报告。重启时无需手工运行本次 SQL，Flyway 使用同一个业务库记录版本。

更早或人工改过的数据库不保证被两个初始迁移完全覆盖；当前实际库结构已单独验证。后续结构变化新增迁移版本，不修改已经执行的版本脚本。
