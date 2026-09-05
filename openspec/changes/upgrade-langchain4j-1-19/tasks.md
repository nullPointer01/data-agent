## 1. Dependency Baseline

- [x] 1.1 Configure core/OpenAI 1.19.0 and Milvus `1.19.0-beta29` with separate Maven properties.
- [x] 1.2 Update current project documentation to describe the 1.19 version line and accepted Milvus beta status.

## 2. Token And Model API Migration

- [x] 2.1 Replace `Tokenizer` and `OpenAiTokenizer` with `TokenCountEstimator` and `OpenAiTokenCountEstimator` in shared memory and prompt budgeting.
- [x] 2.2 Replace model bean and registry types with `ChatModel` and `StreamingChatModel`.
- [x] 2.3 Migrate `McpModelService` to `ChatRequest`, `ChatResponse`, and `StreamingChatResponseHandler` while preserving retry, tool and usage accounting behavior.

## 3. ReAct Compatibility

- [x] 3.1 Migrate ReAct model response handling from `Response<AiMessage>` to `ChatResponse`.
- [x] 3.2 Compile all production sources on JDK 17 and fix remaining LangChain4j 1.19 compatibility errors.

## 4. Verification

- [x] 4.1 Verify resolved dependency versions, removed API references, XML/OpenSpec validity and final diff integrity.
