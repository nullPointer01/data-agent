## 1. API-only Embedding Core

- [x] 1.1 Remove the embedded AllMiniLm dependency and local provider branch; make `EmbeddingGateway` always build the OpenAI-compatible client and keep Profile provider fixed as `api`
  - Acceptance: production code has no `AllMiniLmL6V2EmbeddingModel`, provider switch or local fallback; Base URL/model/dimension validation identifies the invalid field.
  - Verify: static imports, dependency and control-flow inspection only; do not compile or run tests.
  - Files: `pom.xml`, `src/main/java/com/ai/vector/EmbeddingGateway.java`, `src/main/java/com/ai/vector/EmbeddingProperties.java`
  - Covers: SC-1, SC-2, SC-3, SC-4, SC-6

- [x] 1.2 Make service identity explicit in runtime configuration and environment template
  - Acceptance: `EMBEDDING_PROVIDER` no longer exists; Base URL/model/dimension have no misleading local defaults; API key remains optional; index version, normalization and metric remain explicit.
  - Verify: static configuration binding comparison against `EmbeddingProperties`; do not start the application.
  - Files: `src/main/resources/application.yml`, `.env.example`
  - Covers: SC-2, SC-4, SC-6

### Checkpoint A

- JVM no longer loads Embedding model weights.
- Missing service configuration fails before Milvus writes.
- Milvus Collection identity remains versioned and Elasticsearch is untouched.

## 2. Documentation And Migration Contract

- [x] 2.1 Update project entry documentation to describe Embedding service, required variables and API-only failure semantics
  - Acceptance: root/project README and project instructions consistently distinguish model inference, Milvus vector storage and Elasticsearch BM25.
  - Verify: static terminology and configuration-name search; no unmeasured availability or quality claims.
  - Files: `README.md`, `CLAUDE.md`
  - Covers: SC-1, SC-5, SC-6

- [x] 2.2 Update RAG architecture and operational pitfalls with the new Collection migration boundary
  - Acceptance: knowledge docs explain fixed `api` Profile identity, startup probe, new indexVersion rebuild and prohibition on automatic deletion/copying of local vectors.
  - Verify: compare documentation flow and configuration names with production code.
  - Files: `docs/核心逻辑详解/RAG检索链路.md`, `docs/演化/常见陷阱.md`, `ARCHITECTURE_PLAN.md`
  - Covers: SC-4, SC-5, SC-6

## 3. Migration Cleanup And Static Engineering Gate

- [ ] 3.1 After a real API Collection is rebuilt and verified, enumerate Milvus Collections and delete only the exact confirmed historical `__local__` Collection targets
  - Acceptance: the replacement API Collection is confirmed ready before deletion; every deleted name is recorded and belongs to the old local Profile; no API or unrelated Collection is deleted.
  - Verify: read-only Collection listing before deletion and exact-name existence check after deletion; no wildcard or prefix deletion.
  - Files: no repository file changes expected; record exact deleted names and recoverability in `openspec/changes/embedding-service-only/sdd-state.md`
  - Covers: SC-7

- [x] 3.2 Perform the approved no-build static consistency audit across dependencies, imports, provider branches, configuration keys, Collection identity, Milvus/ES retrieval wiring and documentation
  - Acceptance: 0 active local-model paths remain; all required fields match configuration binding; no Milvus/ES/RRF path is removed; unresolved real-service validation is recorded rather than claimed complete.
  - Verify: `rg`, structured YAML/text inspection and `git diff`; do not compile, test, package, start services or run the benchmark.
  - Files: no production file changes expected; record outcome in `openspec/changes/embedding-service-only/sdd-state.md`
  - Covers: SC-1, SC-2, SC-3, SC-4, SC-5, SC-6, SC-7

### Checkpoint B

- Code, configuration and documentation expose one Embedding service path.
- Old local Collection cleanup is performed only after the replacement API Collection is ready, using exact confirmed names.
- Real connectivity, dimension and retrieval quality remain pending until a concrete service is configured.
