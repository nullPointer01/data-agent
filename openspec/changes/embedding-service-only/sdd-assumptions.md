# Assumptions

## A001 API-only means inference service separation

- Assumption: removing `local` means removing the JVM-embedded AllMiniLm model, not removing Milvus vector storage.
- Source: user clarification and current code architecture.
- Confidence: high.
- Validation: proposal review; production dependency and Gateway branch inspection.

## A002 Elasticsearch remains required

- Assumption: Elasticsearch remains the only BM25/full-text path because exact identifiers and keywords complement Milvus semantic retrieval.
- Source: user asked whether ES is necessary and accepted the recommended hybrid architecture.
- Confidence: high.
- Validation: static retrieval wiring check and later golden-set stage comparison.

## A003 Embedding protocol is OpenAI-compatible

- Assumption: the eventual service accepts the OpenAI-compatible Embeddings request/response used by LangChain4j.
- Source: current API implementation and user approval of provider-neutral service configuration.
- Confidence: medium.
- Validation: later curl probe against the selected service before deployment.

## A004 Service identity is not yet selected

- Assumption: Base URL, model name, dimension and API key cannot be safely invented and must remain deployment inputs.
- Source: no concrete Embedding vendor/model was specified.
- Confidence: high.
- Validation: user or deployment environment supplies real values before runtime verification.

## A005 Old local Collection deletion is authorized after cutover

- Assumption: the user authorizes deletion of old local-model Collections after the replacement API Collection has been rebuilt and verified, but does not authorize automatic, wildcard or unrelated Collection deletion.
- Source: explicit user statement that the old `local` Collection can be deleted, plus destructive-operation safeguards.
- Confidence: high.
- Validation: enumerate exact live Collection names before deletion; delete only confirmed `__local__` targets after cutover; record targets and verify API Collections remain.

## A006 No automated build verification

- Assumption: the standing user instruction not to add/run tests or compile remains active.
- Source: explicit user instruction and `CLAUDE.md` project constraint.
- Confidence: high.
- Validation: implementation uses only static checks; real runtime claims remain pending.

## A007 Agent Harness is a later change

- Assumption: unified Chat/ReAct/Orchestrated Runtime should be designed after Embedding service migration and must not be mixed into this Change.
- Source: user requested step-by-step execution and deferred experiments.
- Confidence: high.
- Validation: files and tasks in this Change remain limited to Embedding/RAG configuration and documentation.
