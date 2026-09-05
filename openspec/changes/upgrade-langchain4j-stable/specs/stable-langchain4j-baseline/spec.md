## ADDED Requirements

### Requirement: LangChain4j modules use one stable version
The Maven project SHALL resolve `langchain4j`, `langchain4j-open-ai`, and `langchain4j-milvus` to the same version, and that version MUST NOT contain an alpha, beta, RC, or snapshot qualifier.

#### Scenario: Resolve direct LangChain4j dependencies
- **WHEN** Maven resolves the project's direct LangChain4j dependencies
- **THEN** all three target artifacts resolve to version 0.36.2 without a pre-release qualifier

### Requirement: Stable baseline compiles on JDK 17
The project SHALL compile its production Java sources with Maven running on JDK 17 after the dependency upgrade.

#### Scenario: Compile production sources
- **WHEN** `mvn -Dfrontend.skip=true -DskipTests compile` runs with `JAVA_HOME` set to JDK 17
- **THEN** Maven completes successfully without Java compilation errors

### Requirement: Upgrade preserves runtime behavior
The dependency migration SHALL limit code changes to API compatibility and MUST NOT intentionally alter Agent, RAG, Embedding, or Milvus business behavior.

#### Scenario: Adapt moved tool executor API
- **WHEN** the tool invoker is compiled against LangChain4j 0.36.2
- **THEN** it uses the stable version's tool executor package while preserving tool discovery, argument binding, execution, and error handling behavior
