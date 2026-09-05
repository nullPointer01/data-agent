## 1. Runtime Contract And Configuration

- [x] 1.1 Add explicit timeout, batch, retry, circuit, prefix and optional output-dimensions properties with field-specific validation
  - Acceptance: timeout/backoff/open-duration and numeric ranges match SC-1 through SC-5A; optional output dimensions are absent by default and must equal the Profile expected dimension when configured.
  - Verify: static binding comparison across Java properties, `application.yml` and `.env.example`; parse YAML without starting the application.
  - Files: `src/main/java/com/ai/vector/EmbeddingProperties.java`, `src/main/resources/application.yml`, `.env.example`
  - Covers: SC-1, SC-2, SC-3, SC-4, SC-5, SC-5A

- [x] 1.2 Introduce purpose-aware query/document/probe input formatting without leaking content into identity or logs
  - Acceptance: query and document prefixes are applied exactly once at the Gateway boundary; probe uses no business prefix; defaults preserve raw input.
  - Verify: static call-site and branch inspection only; no tests or model requests.
  - Files: `src/main/java/com/ai/vector/EmbeddingPurpose.java`, `src/main/java/com/ai/vector/EmbeddingInputFormatter.java`
  - Covers: SC-5, SC-6

### Checkpoint A

- Configuration has one documented source of truth and invalid values fail before an API call.
- Input purpose is explicit, while Profile and Collection migration rules remain intact.

## 2. Resilient And Observable API Calls

- [x] 2.1 Implement a dedicated Embedding call executor with classified retry, exponential backoff, per-Profile circuit state and safe Micrometer measurements
  - Acceptance: only 429/5xx/recognized transient I/O are retried; 400/401/403/404 and contract failures are not; circuit transitions follow the configured threshold/window; metrics use bounded labels and no payload data.
  - Verify: static exception-cause classification, attempt bounds, circuit state and logger/metric argument inspection; do not run tests.
  - Files: `src/main/java/com/ai/vector/EmbeddingCallExecutor.java`, `src/main/java/com/ai/vector/EmbeddingRuntimeMetrics.java`, `src/main/java/com/ai/vector/EmbeddingCallRejectedException.java`
  - Covers: SC-2, SC-3, SC-4, SC-6

- [x] 2.2 Upgrade EmbeddingGateway to use explicit timeout, one SDK attempt, optional requested dimensions, purpose-specific calls and validated batch responses
  - Acceptance: Gateway exposes probe/query/document/batch paths; batch count and every vector are validated before results leave the Gateway; normalization remains consistent; no local fallback exists.
  - Verify: `javap` dependency API comparison plus static Gateway control-flow/import inspection; do not compile or call a service.
  - Files: `src/main/java/com/ai/vector/EmbeddingGateway.java`, `src/main/java/com/ai/vector/EmbeddingCompatibilityValidator.java`
  - Covers: SC-1, SC-2, SC-3, SC-4, SC-5, SC-5A, SC-6, SC-7

### Checkpoint B

- All external calls pass through one policy executor and one safe metric boundary.
- SDK retry cannot multiply application retry.
- A malformed batch cannot reach Milvus.

## 3. Batch Indexing Integration

- [x] 3.1 Add ordered Milvus addAll and VectorDocumentIndexer indexAll paths with primary-key count validation before registry updates
  - Acceptance: embeddings, segments and returned primary keys have equal counts; registry records preserve input order; incompatible Embedding batches are rejected before Milvus submission.
  - Verify: static list construction, size guards, ordering and failure propagation inspection.
  - Files: `src/main/java/com/ai/vector/MilvusVectorStoreGateway.java`, `src/main/java/com/ai/vector/VectorDocumentIndexer.java`, `src/main/java/com/ai/vector/VectorIndexRegistry.java`
  - Covers: SC-1, SC-6, SC-7

- [x] 3.2 Route knowledge and file Chunk indexing through sequential configured batches and route vector searches through embedQuery
  - Acceptance: knowledge/file methods no longer issue one Embedding HTTP call per Chunk; a failed batch propagates instead of reporting the source fully indexed; query retrieval uses query-purpose formatting; conversation/skill/memory single-entry semantics remain unchanged.
  - Verify: static call graph and exception-boundary inspection from `VectorMemoryService` to Gateway, Indexer, Milvus and hybrid retrieval.
  - Files: `src/main/java/com/ai/service/VectorMemoryService.java`
  - Covers: SC-1, SC-5, SC-7

### Checkpoint C

- Document ingestion uses bounded sequential batches.
- Query latency path remains single-call.
- Milvus vector, Elasticsearch BM25 and RRF paths remain present.

## 4. Operations Documentation And Static Gate

- [x] 4.1 Document runtime parameters, failure semantics, model input contracts, batch tuning and the requirement to bump indexVersion when prefixes/output dimensions change
  - Acceptance: entry and RAG documents distinguish expected dimension from requested dimensions and explicitly state that defaults are unmeasured starting points.
  - Verify: terminology/configuration-name comparison with production properties; no fabricated quality, cost, throughput or latency claims.
  - Files: `README.md`, `CLAUDE.md`, `docs/核心逻辑详解/RAG检索链路.md`, `docs/演化/常见陷阱.md`
  - Covers: SC-1, SC-2, SC-3, SC-4, SC-5, SC-5A, SC-6, SC-7

- [x] 4.2 Perform the approved no-build static consistency and security audit
  - Acceptance: dependency APIs exist; all required config fields bind; active code has one external API path and zero local fallback; retry and circuit branches match the spec; no secret/text/vector logging is introduced; Milvus/ES/RRF wiring remains present; runtime unknowns are recorded.
  - Verify: `rg`, `javap`, structured XML/YAML inspection, `git diff --check` and OpenSpec task consistency only; do not add/run tests, compile, package, start services, build Docker or run benchmarks.
  - Files: `openspec/changes/embedding-runtime-productionization/sdd-state.md`
  - Covers: SC-1, SC-2, SC-3, SC-4, SC-5, SC-5A, SC-6, SC-7

### Checkpoint D

- Repository behavior is statically consistent with the productionization contract.
- Real provider compatibility, throughput, cost, P95 and retrieval quality remain explicitly pending until an Endpoint and credentials are supplied.
