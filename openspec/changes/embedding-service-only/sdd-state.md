# SDD State

- Change: `embedding-service-only`
- Mode: Full SDD
- Current Stage: Migration cleanup pending
- Explore: Completed
- Proposal: Approved by the user
- Coding: Repository implementation complete; task 3.1 requires external runtime state (5/6 tasks complete)
- Verify: Approved static verification complete; runtime verification intentionally not run
- Archive: Not started

## Stage Gate

The user approved implementation. Repository tasks 1.1, 1.2, 2.1, 2.2 and 3.2 are complete. Task 3.1 remains gated on a real API Collection rebuild and verification.

## Static Verification

- Production source and dependency searches found no embedded AllMiniLm dependency, import, constructor, provider switch or local fallback.
- `EmbeddingGateway` has one `OpenAiEmbeddingModel` construction path and creates one Profile with provider fixed to `api`.
- `EMBEDDING_API_BASE_URL`, `EMBEDDING_MODEL_NAME` and `EMBEDDING_DIMENSION` are required; API key remains optional; index version, normalization and metric remain explicit.
- Milvus physical Collection naming still includes Profile identity and index version.
- `DefaultHybridRetriever` still obtains Milvus vector candidates and Elasticsearch BM25 candidates before calling `RrfFusionRanker`.
- POM XML, application YAML files and changed-file whitespace passed static structure checks.
- Per user constraint, no tests, compilation, packaging, application/service startup, Docker build or RAG benchmark was run.

## Deferred Runtime Verification

- Embedding API connectivity, authentication compatibility and actual returned dimension are not yet verified.
- The replacement `__api__` Collection has not been rebuilt or evaluated with the golden dataset.
- Recall, MRR, NDCG and latency have not been measured and no quality claim is made.
- No Milvus Collection was deleted. The historical `__local__` Collection remains recoverable until task 3.1 is explicitly completed after replacement verification.
- `ARCHITECTURE_PLAN.md` was already deleted in the incoming worktree and was not recreated; the migration contract is recorded in the active RAG knowledge documents instead.

## Constraints

- Do not add or run automated tests.
- Do not compile, package, start the application or run Docker builds.
- Do not delete any Milvus Collection before a replacement API Collection is rebuilt and verified.
- Local Collection deletion is authorized only for exact live names confirmed as the historical `__local__` Profile; never use wildcard/prefix deletion or remove API/unrelated Collections.
- Do not fabricate Embedding connectivity, dimension, retrieval quality or latency results.
- Keep Elasticsearch BM25, Milvus vector retrieval and RRF behavior in scope as preserved contracts, not refactor targets.
