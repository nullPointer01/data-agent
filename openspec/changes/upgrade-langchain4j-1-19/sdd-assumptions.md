# SDD Assumptions

- **A001 Confirmed**：用户明确要求升级至 1.19.0，并在得知 Milvus 只有 beta29 后明确接受 beta。
- **A002 Confirmed**：Maven Central 提供核心/OpenAI 1.19.0 与 Milvus `1.19.0-beta29`，不存在无后缀的 Milvus 1.19.0。
- **A003 Confirmed**：目标核心与 Milvus JAR 的 class-file major version 均为 61，可由 JDK 17 加载。
- **A004 Confirmed**：1.19 已移除旧 ChatLanguageModel、StreamingChatLanguageModel、Tokenizer 和 OpenAiTokenizer API。
- **A005 Confirmed**：遵循用户此前约束，不新增或运行测试，使用 JDK 17 编译作为本次验证门禁。
