## ADDED Requirements

### Requirement: Unified provider catalog
The system SHALL expose one server-side provider catalog containing OpenAI/GPT, the existing mainstream domestic and aggregator providers, and a separate custom OpenAI-compatible entry. The catalog SHALL NOT expose an Ollama-specific entry. Each entry SHALL include grouping, protocol, endpoint defaults, recommended models, API-key requirement, and model-discovery metadata safe for client display.

#### Scenario: OpenAI is a first-class provider
- **WHEN** an administrator loads the provider catalog
- **THEN** the global group contains `OpenAI / GPT` as its first provider and does not label it as a custom endpoint

#### Scenario: Custom compatible endpoint remains available
- **WHEN** a provider is not in the built-in catalog but implements OpenAI-compatible Chat Completions
- **THEN** the administrator can select the custom provider and supply an explicit Base URL and model name

#### Scenario: Custom endpoint targets GPT
- **WHEN** an administrator selects Custom and supplies an OpenAI official or GPT-compatible gateway Base URL, API Key, and GPT model ID
- **THEN** runtime, discovery, and probe use the shared OpenAI-compatible client path

### Requirement: Single catalog source
The frontend SHALL derive provider choices, default endpoints, and recommended model choices from the provider catalog API and MUST NOT maintain a duplicate hardcoded provider template list.

#### Scenario: Catalog defaults change
- **WHEN** a backend provider default or recommended model is updated
- **THEN** the model management page reflects the change without a frontend source edit

### Requirement: Shared endpoint and client resolution
Runtime Chat, Streaming, model discovery, and connection probing SHALL share canonical provider and endpoint resolution rules. Every user-configurable outbound model endpoint MUST pass the existing SSRF guard before a request-capable client is used.

#### Scenario: Runtime custom endpoint points to a forbidden network
- **WHEN** private-network access is disabled and a saved custom Base URL resolves to a forbidden address
- **THEN** the system rejects the client construction before sending model data

#### Scenario: Private model is explicitly allowed
- **WHEN** private-network access is enabled and an administrator configures a self-hosted compatible endpoint
- **THEN** the endpoint can be resolved without weakening validation for other environments

### Requirement: Credential policy
The system SHALL require an API Key for catalog providers marked as authenticated and SHALL allow a missing API Key only for providers marked as optional-auth, including custom self-hosted endpoints. API responses and logs MUST NOT expose the resolved secret.

#### Scenario: Public provider has no API Key
- **WHEN** an administrator attempts discovery, probing, or saving for a provider that requires authentication without a reusable saved key
- **THEN** the operation fails before an outbound request with an actionable authentication message

#### Scenario: Custom self-hosted endpoint has no API Key
- **WHEN** an administrator configures an allowed self-hosted Custom endpoint without an API Key
- **THEN** discovery, probing, and saving do not fail solely because the key is empty

### Requirement: Optional model discovery
The system SHALL attempt OpenAI-compatible model discovery when requested and SHALL return a sorted unique list when available. Discovery failure MUST NOT prevent manual model-name entry or connection probing.

#### Scenario: Models endpoint is available
- **WHEN** the configured endpoint returns a valid OpenAI-compatible model list
- **THEN** the response contains sorted unique model IDs for selection

#### Scenario: Models endpoint is unavailable
- **WHEN** the provider rejects or does not implement `/models`
- **THEN** the UI receives an actionable failure and keeps manual model-name entry enabled

### Requirement: Real Chat connection probe
The system SHALL provide an authenticated administrator endpoint that creates a temporary uncached Chat client, sends a fixed minimal request with no retry and at most 32 output tokens, and returns a safe diagnostic result.

#### Scenario: Probe succeeds
- **WHEN** the endpoint, credential, and model accept a Chat request
- **THEN** the response reports success, elapsed milliseconds, canonical provider, resolved Base URL, resolved model name, and finish reason without returning generated content

#### Scenario: Probe fails
- **WHEN** Chat invocation fails
- **THEN** the response reports failure using a stable category including authentication, model-not-found, invalid-endpoint, rate-limit, timeout, network, unsupported, or unknown

#### Scenario: Editing with a masked key
- **WHEN** an administrator probes an existing model while the client submits the masked API Key and matching modelId
- **THEN** the service resolves the saved encrypted key only after tenant authorization and never returns it

### Requirement: Valid runnable configuration
The system SHALL validate both create and update requests against the resolved configuration. A runnable configuration MUST have a name, recognized or explicit-compatible provider, valid HTTP(S) Base URL, model name, temperature from 0 through 2 when supplied, and maxTokens from 1 through 1000000 when supplied.

#### Scenario: Invalid numeric parameters
- **WHEN** temperature or maxTokens is outside the allowed range
- **THEN** the server rejects the mutation with a field-specific message and does not persist partial changes

#### Scenario: Existing configuration remains compatible
- **WHEN** a pre-change model record is read or edited
- **THEN** it remains usable without a database migration and retains existing default, enabled, tenant, and secret-preservation semantics

### Requirement: OpenAI-compatible runtime baseline
The system SHALL use the LangChain4j 1.19 OpenAI-compatible implementation for OpenAI/GPT and cataloged compatible providers while preserving Chat, Streaming, Tool Calling, JSON output, token accounting, retry ownership, tenant cache isolation, and cache invalidation behavior.

#### Scenario: Agent uses a domestic compatible model
- **WHEN** an enabled tenant model selects a cataloged domestic provider and valid compatible endpoint
- **THEN** Agent runtime obtains its client through the same factory contract used for OpenAI without provider-specific branching in the Agent loop

#### Scenario: SDK and application retries do not multiply
- **WHEN** the factory creates a synchronous LangChain4j client
- **THEN** SDK maxRetries is zero and the existing application retry executor remains the only retry owner for formal runtime calls
