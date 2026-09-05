# SDD State

- Change: `embedding-runtime-productionization`
- Mode: Full SDD
- Current Stage: Coding complete
- Explore: Completed
- Proposal: Complete, approved by user
- Coding: Completed (8/8 tasks complete)
- Verify: Not started
- Archive: Not started

## Stage Gate

The proposal, design, capability spec, implementation tasks and assumptions are complete. The user approved Apply on 2026-09-02.

## Scope Summary

- Purpose-aware query/document/probe Embedding inputs.
- Sequential batch Embedding and Milvus writes for knowledge/file indexing.
- Explicit timeout, classified retry and per-Profile circuit breaking without a new dependency.
- Safe Micrometer metrics and no payload/secret logging.
- Optional requested output dimensions kept distinct from the Profile expected dimension.
- No model-service deployment, vendor choice, cache, Harness refactor, retrieval algorithm change or Collection deletion.

## Verification Constraint

- Do not add or run tests.
- Do not compile, package, start the application, start model services, run Docker builds or execute the RAG benchmark.
- Use static API, configuration, control-flow, security-log, structured-file and diff inspection only.
- Do not claim provider compatibility, throughput, latency, cost or retrieval quality without a real run.

## Static Audit (2026-09-02)

- **PASS - Dependency API**: LangChain4j 0.30 exposes `EmbeddingModel.embedAll`, `EmbeddingStore.addAll`, `OpenAiEmbeddingModel.Builder.timeout`, `maxRetries` and `dimensions`; bytecode inspection confirms `maxRetries(1)` is one SDK attempt.
- **PASS - Configuration**: Java properties, `application.yml` and `.env.example` contain the same timeout, output-dimensions, batch, retry, circuit and prefix fields. YAML and Maven XML parse successfully.
- **PASS - External-only path**: production source contains one `OpenAiEmbeddingModel` construction in `EmbeddingGateway`; no local Embedding implementation, provider switch or fallback dependency remains active.
- **PASS - Retry and circuit**: the application retries only 429, 5xx and recognized transient connection/timeout failures; other HTTP, unknown runtime and vector-contract failures do not retry. Circuit state is isolated by Profile identity and permits one recovery call after the open window.
- **PASS - Batch contract**: Gateway validates response count and every vector before normalization; Milvus validates embedding/segment and returned-primary-key counts; Registry validates the complete batch before recording entries.
- **PASS - Safe observability**: new metrics have fixed operation/outcome plus normalized modelId labels; batch size is a distribution value. New Embedding logs do not include API Key, authorization headers, input text, Chunk bodies or vector arrays.
- **PASS - Retrieval preservation**: queries use `embedQuery`; Milvus vector candidates, Elasticsearch BM25 retrieval and `RrfFusionRanker` remain wired. No Harness or Agent routing code changed.
- **PASS - Static gates**: `git diff --check`, YAML/XML parsing and `openspec validate --strict` pass.
- **UNVERIFIED - Runtime**: real provider array-input and optional-dimensions compatibility, connectivity, retry/circuit behavior under faults, metrics emission, throughput, 429 rate, latency, cost and retrieval quality require a configured Endpoint and an approved runtime verification.
