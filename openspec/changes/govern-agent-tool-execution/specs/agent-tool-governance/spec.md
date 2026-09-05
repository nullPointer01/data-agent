## ADDED Requirements

### Requirement: Complete governed tool catalog
The system SHALL maintain one server-owned descriptor for every Agent tool and each descriptor MUST define the tool name, risk level, read-only flag, idempotency flag, retryability, timeout, maximum attempts, required permission and maximum result length. Registration MUST fail for duplicate names, missing policy metadata, non-positive limits, or a retryable tool that is not both read-only and idempotent.

#### Scenario: Existing tool registers with complete metadata
- **WHEN** application startup discovers a method carrying both the LangChain4j tool annotation and valid governance metadata
- **THEN** the registry exposes one descriptor and preserves the existing tool name, description and parameter schema

#### Scenario: New tool omits governance metadata
- **WHEN** startup discovers a model-callable tool without a governance descriptor
- **THEN** registration fails before that tool can be advertised or invoked

#### Scenario: Unsafe retry declaration is rejected
- **WHEN** a descriptor declares retryable behavior while either read-only or idempotent is false
- **THEN** registration fails with an actionable configuration error

### Requirement: Immutable Run principal authorization snapshot
Every accepted Agent Run SHALL resolve an immutable tool authorization snapshot from the authenticated user, tenant, enabled RBAC roles and permissions, and server-enabled tools. The snapshot MUST NOT contain credentials or accept permission expansion from the analysis request, model output or client events.

#### Scenario: Authenticated user starts a run
- **WHEN** the Coordinator creates an Agent Run for an authenticated tenant user
- **THEN** it resolves the user's currently enabled permission codes once and stores only the immutable authorization facts required by tool policy

#### Scenario: User and tenant do not match
- **WHEN** the persisted user does not belong to the authenticated tenant or is no longer enabled
- **THEN** the Run does not receive tool authorization and no tool implementation can execute

#### Scenario: Client submits governance-like fields
- **WHEN** an analysis request contains unknown fields claiming additional tools, permissions, risk exceptions or retry limits
- **THEN** the server ignores or rejects those fields and uses only the server-created authorization snapshot

### Requirement: Execution-time Agent tool allowlist
Every Agent tool invocation MUST carry an explicit server-created invocation context identifying the current executor and the exact set of tool names allowed for that execution. A tool implementation SHALL execute only when its name is registered, server-enabled, permitted by the Run principal, included in the invocation allowlist and accepted by risk policy.

#### Scenario: Model requests a tool omitted from its Profile
- **WHEN** a configured Agent returns a valid tool call whose name was not in the Profile-derived ToolSpecification set sent to that model
- **THEN** the invocation is rejected as unauthorized and the tool implementation attempt count remains zero

#### Scenario: Orchestrator precheck requests an unrelated tool
- **WHEN** a deterministic precheck tries to invoke a tool outside its fixed precheck allowlist
- **THEN** the policy rejects the request before budget consumption or implementation execution

#### Scenario: Empty legacy Profile tool list executes
- **WHEN** an existing Profile has an empty tool list under the current legacy meaning of all available tools
- **THEN** the invocation context uses the server catalog as its starting set and still intersects principal permissions and risk policy

#### Scenario: Authorized tool executes
- **WHEN** a tool is present in every required policy set and the Run is active
- **THEN** the request may proceed to budget admission and implementation execution

### Requirement: Untrusted argument validation
The system MUST parse model-provided tool arguments as structured JSON and validate them against the same registered ToolSpecification schema before any implementation attempt. It MUST reject malformed JSON, oversized input, missing required properties, unknown properties and incompatible scalar types without relying on ad hoc string parsing.

#### Scenario: Valid scalar arguments are accepted
- **WHEN** a request contains only the registered required scalar properties with compatible values
- **THEN** validation succeeds and passes the structured arguments to the next policy stage

#### Scenario: Required property is missing
- **WHEN** a model omits a required tool argument
- **THEN** the system returns `INVALID_ARGUMENTS` and performs zero implementation attempts

#### Scenario: Model injects an unknown argument
- **WHEN** a request includes a property absent from the registered tool schema
- **THEN** the system rejects the request before reflection execution and records only a safe field-name summary

#### Scenario: Arguments exceed the server limit
- **WHEN** the serialized argument payload exceeds the configured maximum length
- **THEN** validation rejects it without placing the full payload in logs, events or Trace

### Requirement: Stable structured tool result
Every logical tool invocation SHALL produce one structured result containing the `toolCallId`, tool name, status or stable error code, retryability, attempt count, duration, safe payload and sanitization/truncation flags. ReAct control flow MUST use structured status and error codes rather than localized message substring matching.

#### Scenario: Tool succeeds
- **WHEN** an authorized tool implementation returns normally
- **THEN** the result status is `SUCCESS` and carries the safe output plus actual attempts and duration

#### Scenario: Policy rejects a tool
- **WHEN** authorization or risk policy denies an invocation
- **THEN** the result uses `UNAUTHORIZED` or `POLICY_DENIED`, is non-retriable and contains no implementation exception or sensitive policy details

#### Scenario: ReAct receives a recoverable argument error
- **WHEN** a tool result has `INVALID_ARGUMENTS`
- **THEN** ReAct can generate a parameter-correction observation from that code without parsing a Chinese error prefix

#### Scenario: Legacy model observation is generated
- **WHEN** a current ReAct loop needs to send the result back through LangChain4j `ToolExecutionResultMessage`
- **THEN** it receives a compatible safe text representation derived from the structured result

### Requirement: Bounded timeout and retry policy
Each logical tool invocation SHALL use one stable `toolCallId`, execute through a bounded context-propagating executor, and perform no more than the smaller of the descriptor and server maximum attempts. Every actual attempt MUST recheck Run cancellation and deadline and MUST consume one Run tool-call budget before implementation execution.

#### Scenario: Retriable read-only tool has a transient failure
- **WHEN** a tool is read-only, idempotent and retryable and a classified transient error occurs while Run budget and deadline remain
- **THEN** the pipeline retries with bounded backoff under the same `toolCallId` and records each actual attempt

#### Scenario: Non-idempotent tool fails
- **WHEN** a tool is not both read-only and idempotent
- **THEN** the pipeline performs at most one implementation attempt regardless of the returned error

#### Scenario: Authorization fails
- **WHEN** a request is unknown, invalid or unauthorized
- **THEN** no retry occurs and no Run tool-call budget is consumed

#### Scenario: Tool-specific timeout expires
- **WHEN** an implementation does not complete before the smaller of its descriptor timeout and the Run remaining deadline
- **THEN** the Future is interrupted, late output is discarded and the structured result reports `TIMEOUT`

#### Scenario: Run terminates before a retry
- **WHEN** cancellation, deadline or budget exhaustion wins after an earlier attempt
- **THEN** no additional attempt starts and the result reflects the winning Run termination safely

### Requirement: Safe tool inputs and outputs
Raw tool arguments and raw results MUST NOT be written to Agent events, Trace or structured logs. Before content reaches the model or any observable sink, the system SHALL redact API keys, Bearer tokens, password/secret/token fields and connection-string credentials, then truncate to the lower of descriptor and server output limits.

#### Scenario: Tool returns a credential-like value
- **WHEN** a result contains an API key, authorization header or connection-string password
- **THEN** every model observation, event, log and Trace representation replaces the sensitive portion and marks the result as sanitized

#### Scenario: Tool returns oversized content
- **WHEN** a result exceeds its effective maximum length
- **THEN** the safe payload is deterministically truncated, marked as truncated and no full copy is emitted or persisted

#### Scenario: Tool parameters are logged
- **WHEN** the pipeline records an invocation
- **THEN** it records only safe metadata such as parameter field names, lengths or hashes and never the complete parameter values

### Requirement: Correlated tool observability
The system SHALL correlate every tool decision and logical execution with the existing `runId` and one `toolCallId`. It SHALL emit one compatible typed `tool_call` process event per logical invocation, append a bounded safe record to the Run's tool journal, expose aggregate Trace evidence, and record low-cardinality invocation, attempt and duration metrics.

#### Scenario: Streaming tool succeeds after retry
- **WHEN** a streaming Run completes a tool call on its second permitted attempt
- **THEN** its event and final Trace use the same `runId` and `toolCallId` and report two attempts without duplicating the user-facing logical tool call

#### Scenario: Tool is denied
- **WHEN** policy denies an invocation before implementation
- **THEN** the journal, safe event and counters identify the denied outcome while excluding raw arguments and internal authorization details

#### Scenario: Metrics are recorded
- **WHEN** any governed invocation completes or is denied
- **THEN** counters and timers use bounded tags such as registered tool, status and risk and do not use runId, userId or exception text as tags

#### Scenario: Journal limit is reached
- **WHEN** a Run produces more tool records than the configured journal capacity
- **THEN** the journal remains bounded and exposes an overflow count instead of growing without limit

### Requirement: Existing tool integration compatibility
The change SHALL preserve existing analysis endpoints, AgentProfile `tools` records, tool names, model-visible parameter schemas, ReAct and Orchestrator behavior, tenant-aware business services and database schema. Non-Agent code MUST NOT gain an implicit Agent Run or bypass Agent policy by calling a raw model-facing tool executor.

#### Scenario: Existing configured Agent executes an allowed tool
- **WHEN** an existing Profile invokes one of its currently configured tool names after upgrade
- **THEN** the model sees the same name and parameter schema and receives a compatible safe observation after governed execution

#### Scenario: Existing default Agent uses tools
- **WHEN** the built-in ReAct path runs without a persisted AgentProfile
- **THEN** it receives an explicit server-defined invocation allowlist and uses the same governed pipeline

#### Scenario: Database upgrade is evaluated
- **WHEN** this capability is deployed over an existing project database
- **THEN** no new table, column or data migration is required for tool governance

