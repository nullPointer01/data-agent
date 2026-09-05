# Delta Spec: RAG Retrieval Quality

## Requirement: JDK 17 Build Baseline

The backend shall compile, test and run with JDK 17 across local version declarations, Maven, CI and the application container. Maven shall reject JDK versions lower or higher than 17 before compilation.

### Scenario: Unsupported Java runtime

- Given Maven is running on JDK 8, JDK 11 or JDK 21
- When the build reaches the validate phase
- Then the build fails with a message that JDK 17 is required
- And Java source compilation does not begin

### Scenario: Clean JDK 17 verification

- Given Maven is running on JDK 17
- When the backend executes a clean verify build
- Then production sources compile with `release=17`
- And the package lifecycle completes successfully

## Requirement: Elasticsearch-only Full-text Retrieval

When RAG is enabled, the system shall use Elasticsearch as the only full-text retrieval and indexing implementation. The system shall not query MySQL business tables as a full-text fallback.

### Scenario: Elasticsearch unavailable

- Given RAG is enabled
- When Elasticsearch cannot be reached
- Then the RAG health state reports the dependency failure
- And the system does not silently execute JPA keyword retrieval

## Requirement: Embedding Compatibility Contract

The system shall identify every vector index by provider, model, index version, dimension and metric, and shall validate the actual embedding dimension before serving traffic.

### Scenario: Configured dimension differs from model output

- Given the configured dimension is 384
- And the model returns a 1024-dimensional vector
- When the application validates the Embedding Profile
- Then startup fails with a clear compatibility error
- And no vector is written to Milvus

### Scenario: Index version changes

- Given index versions `v1` and `v2`
- When their Collection names are resolved
- Then they resolve to different physical Collection names
- And vectors from the two versions cannot be mixed

## Requirement: Reproducible Retrieval Benchmark

The system shall execute a batch of labelled retrieval cases and calculate deterministic ranking and latency metrics with the active model and retrieval configuration attached to the report.

### Scenario: Benchmark execution

- Given at least 30 labelled cases
- When the benchmark runs with K values 5, 10 and 20
- Then it reports Recall@5, Recall@10, Recall@20, MRR@10, NDCG@10 and P95 latency
- And each failed case includes the actual ranked source and Chunk identifiers

## Requirement: Cross-Encoder Reranking

The system shall support a real Cross-Encoder Provider that scores query-document pairs and shall expose whether the final result used that Provider or a fallback.

### Scenario: Reranker succeeds

- Given RRF produced 20 candidates
- When the Cross-Encoder returns valid scores
- Then the final candidates are ordered by model score
- And the top 6 are passed to parent-context resolution

### Scenario: Reranker fails in fail-open mode

- Given the Cross-Encoder times out
- When fail-open is enabled
- Then candidates use the deterministic heuristic fallback
- And the retrieval trace records the timeout and fallback Provider

## Requirement: External AI Service Environment Isolation

The system shall run Embedding and Reranker inference through configured external HTTP services, shall keep credentials outside repository files, and shall isolate local, test and production vector/full-text indexes.

### Scenario: Local development with managed inference

- Given the developer configured a valid external API Key
- And local Elasticsearch and Milvus are available
- When the application starts with the local profile
- Then `BAAI/bge-m3` returns a 1024-dimensional probe vector
- And no local Ollama, TEI or in-process Embedding model is required

### Scenario: Test and production isolation

- Given local, test and production use the same model contract
- When documents are indexed in each environment
- Then each environment uses separate Elasticsearch indexes, Milvus Collections and credentials
- And no test execution reads or modifies a production index

### Scenario: Credential handling

- Given an external Embedding or Reranker service requires authentication
- When its client is configured
- Then the API Key is obtained from an environment variable or deployment secret
- And the key is absent from source files, logs, traces and benchmark reports
