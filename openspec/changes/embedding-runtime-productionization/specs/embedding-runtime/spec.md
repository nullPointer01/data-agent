## ADDED Requirements

### Requirement: Purpose-Aware Embedding Input

The runtime MUST distinguish query, document and startup-probe embedding operations. It SHALL apply the configured query prefix only to queries, the configured document prefix only to indexed documents, and no business prefix to the startup probe.

#### Scenario: Embed a retrieval query

- **WHEN** vector retrieval embeds a user query
- **THEN** the runtime prepends the configured query prefix exactly once
- **AND** it calls the configured external Embedding service

#### Scenario: Embed an indexed document

- **WHEN** a knowledge, file or other document segment is embedded for indexing
- **THEN** the runtime prepends the configured document prefix exactly once

#### Scenario: Prefix configuration changes

- **WHEN** the query or document prefix changes from the contract used by an existing Collection
- **THEN** operators MUST use a new index version and rebuild the vector index

### Requirement: Explicit Embedding Runtime Configuration

The runtime MUST require a positive request timeout and MUST validate batch size, maximum attempts, backoff, circuit failure threshold and circuit open duration before issuing a service request.

#### Scenario: Runtime configuration is valid

- **WHEN** timeout and resilience values are within their declared ranges
- **THEN** the OpenAI-compatible client initializes with the explicit timeout
- **AND** SDK built-in retry is limited so that only the application retry policy controls repeated calls

#### Scenario: Runtime configuration is invalid

- **WHEN** timeout is not positive, batch size is outside `1..128`, maximum attempts is outside `1..5`, or a circuit value is not positive
- **THEN** initialization fails with the invalid configuration field name
- **AND** no Embedding request is issued

#### Scenario: Optional output dimensions are absent

- **WHEN** `output-dimensions` is not configured
- **THEN** the client does not send the optional `dimensions` request field
- **AND** it still validates the returned vector against the Profile expected dimension

#### Scenario: Optional output dimensions are configured

- **WHEN** `output-dimensions` is configured as a positive value equal to the Profile expected dimension
- **THEN** the client sends that value to the compatible service
- **AND** it still validates every returned vector

### Requirement: Ordered Batch Embedding

The runtime SHALL embed document segments in configurable sequential batches and MUST preserve the input-to-vector association before writing vectors to Milvus.

#### Scenario: Batch response is compatible

- **WHEN** the service returns one non-null vector of the expected dimension for every input segment
- **THEN** the runtime validates and normalizes the entire batch
- **AND** it writes the embeddings and corresponding segments to Milvus in the same order

#### Scenario: Batch response count differs

- **WHEN** the service returns a vector count different from the input segment count
- **THEN** the entire batch fails before any vector from that batch is submitted to Milvus
- **AND** the error contains operation, model and expected/actual count without containing input text

#### Scenario: One vector in a batch is incompatible

- **WHEN** any returned vector is null or has a dimension different from the active Profile
- **THEN** the entire batch fails before any vector from that batch is submitted to Milvus

#### Scenario: Knowledge or file batch fails

- **WHEN** a knowledge or file indexing batch cannot complete
- **THEN** the indexing failure is propagated to its caller
- **AND** the source is not reported as fully indexed by that operation

### Requirement: Classified Retry And Circuit Breaking

The runtime MUST retry only explicitly transient Embedding failures, MUST apply the configured maximum attempts and backoff, and MUST reject calls while its per-Profile circuit is open.

#### Scenario: Service is rate limited or unavailable

- **WHEN** an Embedding request fails with HTTP 429, HTTP 5xx or a recognized transient I/O failure
- **THEN** the runtime retries within the configured maximum attempts using exponential backoff
- **AND** a successful retry resets the consecutive logical-call failure count

#### Scenario: Request is invalid or unauthorized

- **WHEN** an Embedding request fails with HTTP 400, 401, 403 or 404
- **THEN** the runtime fails immediately with zero retry attempts

#### Scenario: Returned vector violates the contract

- **WHEN** response count, nullability or dimension validation fails
- **THEN** the runtime fails immediately with zero retry attempts
- **AND** it does not fall back to another model

#### Scenario: Circuit threshold is reached

- **WHEN** consecutive logical Embedding calls fail to the configured threshold
- **THEN** the circuit opens for the configured duration
- **AND** new calls are rejected without contacting the service during that window

#### Scenario: Circuit recovers

- **WHEN** the open duration expires and the next permitted call succeeds
- **THEN** the circuit closes and the failure count resets

### Requirement: Safe Embedding Observability

The runtime SHALL publish bounded Micrometer measurements for Embedding calls, retries, circuit rejection, duration and batch size without exposing sensitive or high-cardinality payload data.

#### Scenario: Call succeeds

- **WHEN** a probe, query, document or batch call succeeds
- **THEN** call count and duration are recorded with bounded operation, outcome and model identity labels
- **AND** a batch call records its batch size as a distribution value rather than a metric label

#### Scenario: Call fails or retries

- **WHEN** a call fails, retries or is rejected by the circuit
- **THEN** the corresponding failure, retry or circuit measurement is incremented

#### Scenario: Observability data is inspected

- **WHEN** application logs and Embedding metrics are emitted
- **THEN** they contain no API key, authorization header, complete input text, vector array, tenant identifier, user identifier or Chunk body

### Requirement: Existing Retrieval Contract Is Preserved

Embedding runtime productionization MUST NOT remove the existing vector, full-text or fusion stages and MUST NOT introduce a JVM-local model fallback.

#### Scenario: Execute hybrid RAG retrieval

- **WHEN** a RAG query is executed after this change
- **THEN** the query uses the purpose-aware external Embedding path for Milvus vector retrieval
- **AND** Elasticsearch still provides BM25 candidates
- **AND** RRF still fuses both candidate lists

#### Scenario: External Embedding service fails

- **WHEN** retries are exhausted or the circuit is open
- **THEN** the Embedding operation fails explicitly
- **AND** no local model or alternate vector space is used
