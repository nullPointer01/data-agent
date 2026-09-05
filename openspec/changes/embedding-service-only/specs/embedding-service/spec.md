## ADDED Requirements

### Requirement: External Embedding Service Only

The application SHALL generate every document and query embedding through one configured OpenAI-compatible Embedding service and SHALL NOT load an embedding model inside the JVM.

#### Scenario: Generate a document embedding

- **WHEN** the application indexes a document Chunk
- **THEN** it calls the configured Embedding service
- **AND** writes the validated returned vector to Milvus

#### Scenario: Generate a query embedding

- **WHEN** the application performs vector retrieval for a user query
- **THEN** it calls the same configured Embedding service and model identity used for indexing

### Requirement: Explicit Service Configuration

The application MUST require a non-empty Base URL, a non-empty model name, and a positive expected dimension before the Embedding Gateway becomes usable. The API key MAY be empty for an authorized internal service.

#### Scenario: Required configuration is missing

- **WHEN** Base URL or model name is blank, or dimension is not positive
- **THEN** application initialization fails before any vector is written
- **AND** the error identifies the invalid configuration field without exposing credentials

#### Scenario: Internal service has no API key

- **WHEN** Base URL, model name and dimension are valid but API key is empty
- **THEN** the client can initialize for an internal no-key compatible endpoint

### Requirement: Embedding Compatibility Validation

The application MUST probe the configured service during startup and MUST validate every returned vector against the active Embedding Profile before using it.

#### Scenario: Actual dimension differs from configuration

- **WHEN** the service returns a vector whose dimension differs from the configured dimension
- **THEN** startup or the active embedding operation fails with model and dimension context
- **AND** the vector is not written to Milvus

#### Scenario: Service call fails

- **WHEN** the configured Embedding service is unavailable or returns an invalid response
- **THEN** the operation fails explicitly
- **AND** the application does not fall back to a local model

### Requirement: Versioned Vector Identity

The active Embedding Profile SHALL identify provider `api`, model name, index version, dimension, normalization policy and metric. The Milvus physical Collection name MUST isolate incompatible model identities.

#### Scenario: Replace local model with API model

- **WHEN** an API model profile replaces a historical local model profile
- **THEN** it resolves to a different physical Collection name
- **AND** historical local vectors are not reused as API-model vectors

#### Scenario: Model configuration changes

- **WHEN** model, dimension, metric or vector processing policy changes
- **THEN** operators use a new index version and rebuild vectors into an isolated Collection

### Requirement: Controlled Local Collection Retirement

Operators MAY delete historical local-model Collections only after the replacement API-model Collection has been rebuilt and verified. Cleanup MUST use an explicitly enumerated Collection name and MUST NOT delete API-model or unrelated Collections.

#### Scenario: Retire a verified local Collection

- **WHEN** the replacement API-model Collection has completed rebuild and availability verification
- **AND** an operator confirms the exact historical `__local__` Collection name
- **THEN** that exact local Collection can be deleted
- **AND** the deleted target is recorded

#### Scenario: Replacement Collection is not ready

- **WHEN** the replacement API-model Collection has not completed rebuild or availability verification
- **THEN** the historical local Collection is not deleted

#### Scenario: Cleanup target is not an exact local Collection

- **WHEN** a cleanup target is an API-model Collection, an unrelated Collection, or a non-exact pattern
- **THEN** cleanup is rejected without deleting the target

### Requirement: Hybrid Retrieval Preservation

Embedding service migration SHALL NOT remove or replace either Milvus vector retrieval or Elasticsearch BM25 full-text retrieval.

#### Scenario: Execute RAG retrieval

- **WHEN** a RAG query is evaluated after migration
- **THEN** Milvus provides vector candidates
- **AND** Elasticsearch provides BM25 candidates
- **AND** both candidate lists remain available to RRF fusion
