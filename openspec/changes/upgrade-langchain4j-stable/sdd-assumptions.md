# SDD Assumptions

- **A001 Confirmed**：用户要求全部采用稳定版本，并在修改后使用 JDK 17 编译。
- **A002 Confirmed**：Maven Central 最新核心和 OpenAI 稳定版为 1.19.0，对应 Milvus 仅有 1.19.0-beta29。
- **A003 Confirmed**：三个目标 artifact 最后一个同版本且无预发布标记的组合为 0.36.2。
- **A004 Confirmed**：0.36.2 字节码目标为 Java 17；工具执行接口迁移到 `dev.langchain4j.service.tool`，构造器和执行方法签名不变。
- **A005 Confirmed**：按用户此前约束，本次不新增或运行测试，以 JDK 17 编译作为实现验证。
