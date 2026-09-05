## ADDED Requirements

### Requirement: Project resolves the LangChain4j 1.19 release line
The Maven project SHALL resolve LangChain4j core and OpenAI modules to 1.19.0 and MUST resolve the explicitly accepted Milvus integration to `1.19.0-beta29`.

#### Scenario: Resolve LangChain4j dependencies
- **WHEN** Maven builds the dependency tree
- **THEN** core and OpenAI artifacts resolve to 1.19.0 and the Milvus artifact resolves to `1.19.0-beta29`

### Requirement: Model calls use LangChain4j 1.x APIs
The application SHALL use `ChatModel`, `StreamingChatModel`, `ChatResponse`, and `StreamingChatResponseHandler` without references to the removed 0.x model interfaces.

#### Scenario: Compile model runtime
- **WHEN** the model runtime compiles against LangChain4j 1.19.0
- **THEN** synchronous and streaming model calls use the 1.x request and response contracts

### Requirement: Tool specifications survive the migration
The application MUST include configured tool specifications in both synchronous and streaming `ChatRequest` objects whenever tools are available.

#### Scenario: Invoke a model with tools
- **WHEN** ReAct supplies a non-empty tool specification list
- **THEN** the outgoing `ChatRequest` contains the same tool specifications

### Requirement: Token budgets use the 1.x estimator contract
The application SHALL use `TokenCountEstimator` and `OpenAiTokenCountEstimator` for chat memory and prompt token budgets.

#### Scenario: Estimate a prompt budget
- **WHEN** the runtime estimates text or message token counts
- **THEN** it calls the corresponding `TokenCountEstimator` method and preserves the configured token limits

### Requirement: The migration compiles on JDK 17
The project SHALL compile all production Java sources using Maven running on JDK 17.

#### Scenario: Compile production sources
- **WHEN** `mvn -Dfrontend.skip=true -DskipTests compile` runs under JDK 17
- **THEN** Maven completes successfully without compilation errors
