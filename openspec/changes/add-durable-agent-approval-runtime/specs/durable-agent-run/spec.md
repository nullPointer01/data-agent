## ADDED Requirements

### Requirement: Persistent Agent Run State
The system SHALL persist every durable Agent Run as a tenant-owned record with one authoritative status, version, execution mode, owner, session, budget usage and lifecycle timestamps. State changes MUST follow the declared transition graph and use an expected current state or version so concurrent writers cannot create two winning transitions.

#### Scenario: Online run starts
- **WHEN** a request eligible for durable execution enters the Coordinator
- **THEN** exactly one persistent Run record is created with the same `runId` as the in-memory context and transitions from `CREATED` to `RUNNING`

#### Scenario: Concurrent terminal transitions race
- **WHEN** completion, cancellation and timeout concurrently attempt to finish the same Run
- **THEN** only one conditionally valid transition succeeds and the persisted Run exposes one terminal status

#### Scenario: Existing non-durable behavior is used
- **WHEN** durable approval is disabled or no approval boundary is reached
- **THEN** existing Chat, ReAct and Orchestrated requests preserve their current API and tool behavior

### Requirement: Versioned Encrypted Checkpoint
The system MUST persist a versioned portable Checkpoint before a Run enters `WAITING_APPROVAL`. The Checkpoint SHALL contain the execution position, portable message history, pending action relationship, budget usage, remaining active timeout and server-created execution identity required for deterministic resume. Recoverable message and tool payload data MUST be encrypted at rest and MUST NOT be returned by list/detail APIs, logs, Audit or Trace.

#### Scenario: Tool execution is suspended
- **WHEN** an admitted tool action requires approval
- **THEN** the system atomically stores a complete encrypted Checkpoint and pending approval before exposing `WAITING_APPROVAL`

#### Scenario: Sensitive content is inspected through APIs
- **WHEN** an owner or reviewer reads the Run or approval detail
- **THEN** the response contains only safe summaries and never the Checkpoint ciphertext, decrypted model messages or raw tool arguments

#### Scenario: Unsupported Checkpoint version is loaded
- **WHEN** a Resume Worker encounters a schema version it cannot decode or migrate
- **THEN** no model or tool executes and the Run enters a safe failed state with correlated Audit evidence

### Requirement: Suspension Freezes Active Execution Budget
Human approval waiting time SHALL NOT consume the Run's active execution timeout. Entering `WAITING_APPROVAL` MUST preserve all iteration, model-call, tool-call and Token counters plus the remaining positive active timeout. Resume MUST restore those counters without resetting or expanding any original limit.

#### Scenario: Approval arrives after the original wall-clock deadline
- **WHEN** a Run paused with positive active time remaining is approved hours later but before approval expiry
- **THEN** it resumes with the saved remaining active timeout and original consumed budget rather than immediately timing out or receiving a fresh full budget

#### Scenario: No active time remains at suspension
- **WHEN** the active execution deadline has already won before Checkpoint commit
- **THEN** the Run terminates as timed out and no approval request becomes executable

### Requirement: Durable Resume Lease
Only a worker holding a current database lease SHALL resume a persisted Run. Lease acquisition MUST use tenant, Run status, expected version and lease expiry conditions; the worker MUST renew the lease while executing and MUST release or close it when suspending or terminating.

#### Scenario: Two workers claim an approved Run
- **WHEN** multiple nodes concurrently try to resume the same approved Run
- **THEN** exactly one conditional update obtains the active lease and only that holder may call the model or tool

#### Scenario: Worker crashes after claiming
- **WHEN** a worker stops renewing its lease before the Run reaches another stable state
- **THEN** another worker can claim the Run only after lease expiry and uses the same persisted Checkpoint and action identity

#### Scenario: Stale worker returns after losing lease
- **WHEN** an old worker attempts to persist progress after its lease is no longer current
- **THEN** its write is rejected and it cannot overwrite the new holder's state

### Requirement: Resume Reconstructs The Same Logical Run
Resume SHALL retain the original `runId`, pending `toolCallId`, execution mode, session, Agent identity and accumulated budgets. The system MUST reconstruct runtime-only objects from the portable Checkpoint and current server registry rather than deserializing framework Beans or trusting client-supplied continuation state.

#### Scenario: Service restarts while waiting
- **WHEN** the original process exits after persisting `WAITING_APPROVAL` and a later process receives approval
- **THEN** the later process reconstructs the Run from storage and continues under the same correlation IDs

#### Scenario: Client attempts to modify continuation
- **WHEN** an approval or resume request contains fields claiming a different node, tool, arguments, Agent, permission or budget
- **THEN** those fields are rejected or ignored and only the server Checkpoint determines continuation

### Requirement: Durable Run Query And Cancellation
The system SHALL expose an owner-scoped Run detail endpoint that returns safe status, timestamps, approval reference, budget summary and final result/error when available. Existing cancellation MUST work for both active in-memory Runs and persisted waiting Runs while enforcing tenant and owner boundaries.

#### Scenario: Owner checks a waiting Run after disconnect
- **WHEN** the owning tenant user queries the `runId` after the original SSE connection closed
- **THEN** the endpoint reports `WAITING_APPROVAL` and a safe approval reference without exposing encrypted state

#### Scenario: Different tenant queries or cancels a Run
- **WHEN** a user from another tenant supplies a valid `runId`
- **THEN** the system reveals no Run and performs no state transition

#### Scenario: Owner cancels while waiting
- **WHEN** the owner cancels a `WAITING_APPROVAL` Run
- **THEN** the Run becomes `CANCELLED`, its pending approval cannot be approved, and no tool implementation executes

### Requirement: Recoverable Run Observability
Every persistent transition, lease claim/loss, suspension and resume result SHALL be correlated by `runId`, use bounded low-cardinality metrics and write safe Trace/Audit evidence. The system MUST distinguish wall-clock duration, active execution duration and human waiting duration.

#### Scenario: Run completes after approval
- **WHEN** a suspended Run resumes and reaches a final answer
- **THEN** its Run detail, Trace and Audit expose one coherent transition history and duration breakdown without raw Checkpoint content

#### Scenario: Recovery repeatedly fails
- **WHEN** a Run cannot be resumed due to decode, authorization or lease errors
- **THEN** bounded counters identify the failure category and the Run reaches a safe state instead of retrying indefinitely
