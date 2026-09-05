## 1. Dependency Baseline

- [x] 1.1 Add one `langchain4j.version` Maven property and resolve the core, OpenAI, and Milvus direct dependencies to stable version 0.36.2.
- [x] 1.2 Update current project documentation that still declares LangChain4j 0.30 as the active version while preserving historical OpenSpec evidence.

## 2. API Compatibility

- [x] 2.1 Migrate `AgentToolInvoker` to the 0.36.2 service tool executor package without changing tool invocation behavior.
- [x] 2.2 Compile production sources with Maven on JDK 17 and fix every LangChain4j compatibility error required for a successful build.

## 3. Verification

- [x] 3.1 Verify the effective direct dependency versions, validate `pom.xml` and this OpenSpec change, and run whitespace/error checks on the final diff.
