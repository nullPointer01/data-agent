# 设计

- 使用标准 mybatis-spring-boot-starter，普通字段使用 resultType 和全局驼峰自动映射，仅加密、关联与异名 record 构造保留必要 resultMap；XML 明确条件、排序与参数。保留 Repository 作为领域仓储边界，接口本身为 MyBatis Mapper。
- 通用查询保持现有调用合同，分页使用参数化 LIMIT/OFFSET；Token Page 在仓储默认方法中组装，聚合转换为稳定结果。
- 批量 ID 使用 @Param("ids") Collection 合同和 foreach，不探测 Iterable 的迭代器；null/空集合明确返回空结果。
- 实体去除 JPA 元数据。保存时明确区分插入/更新，调用时间戳与 ID 准备逻辑；自增键使用 JDBC generated keys。版本字段使用条件更新并拒绝过期保存。
- RBAC 关联使用 XML 嵌套结果查询和事务性关系表写入；加密字段使用依赖注入的 TypeHandler，保持既有 AES-GCM 数据兼容。
- Agent 评测的 managed entity 脏检查全部改为明确 save/update；抢占保留 SELECT FOR UPDATE。审批/恢复继续使用原状态、版本、租约条件 SQL。
- 索引持久任务和重建来源查询搬入 XML，来源表选择仅支持固定枚举，禁止 SQL 文本接受用户表名。
- Flyway 提供幂等初始结构脚本，已有库 baseline=0 后执行 CREATE IF NOT EXISTS，未来改表添加新版本。MyBatis 不负责自动推断表结构。
