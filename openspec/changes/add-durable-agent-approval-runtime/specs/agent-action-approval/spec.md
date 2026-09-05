## ADDED Requirements

### Requirement: Explicit Tool Approval Policy
Every approval-gated tool SHALL declare approval as server-owned metadata separate from risk level, together with the permission required to review it. Registry construction MUST reject incomplete or contradictory approval metadata. Model descriptions, prompts and client fields MUST NOT enable, disable or bypass approval.

#### Scenario: Approval tool registers
- **WHEN** a tool declares valid risk, execution policy, `approvalRequired=true` and a non-blank approval permission
- **THEN** its Descriptor exposes the immutable approval contract to the governance pipeline

#### Scenario: Approval metadata is incomplete
- **WHEN** an approval-required tool omits its reviewer permission or declares unsafe automatic retry behavior
- **THEN** application registration fails before the tool can be advertised or invoked

#### Scenario: High risk does not implicitly define approval
- **WHEN** policy evaluates a tool
- **THEN** it uses explicit approval metadata rather than guessing from the tool name, description or risk label alone

### Requirement: Approval Interrupt Before Implementation
An admitted action requiring approval MUST persist its Checkpoint and approval request before any implementation attempt or tool budget consumption. The logical result SHALL identify `APPROVAL_REQUIRED` and cause the current Run to suspend rather than fail, complete or continue to another model iteration.

#### Scenario: Model proposes an approval-gated action
- **WHEN** registry, Schema, principal, tenant, Agent allowlist and risk checks pass but no valid approval grant exists
- **THEN** the Run becomes `WAITING_APPROVAL`, attempts remain zero and exactly one pending approval is correlated to the `toolCallId`

#### Scenario: Admission fails before approval
- **WHEN** the tool is unknown, arguments are invalid, or current policy denies the caller
- **THEN** no approval is created and the existing structured rejection result is returned

#### Scenario: Duplicate suspension is retried
- **WHEN** the same `runId + toolCallId` reaches persistence more than once
- **THEN** the unique action identity reuses the existing approval instead of creating duplicate pending actions

### Requirement: Tenant-Isolated Four-Eyes Decision
Only an enabled reviewer in the same tenant who holds the configured review permission and the relevant tool permission SHALL approve or reject a pending action. The reviewer MUST differ from the Run owner. Approval MUST NOT grant the owner a permission they did not already possess.

#### Scenario: Authorized reviewer approves
- **WHEN** a different enabled user in the same tenant has all required review/tool permissions and the approval remains pending and unexpired
- **THEN** exactly one `PENDING -> APPROVED` decision is committed with reviewer identity, comment and timestamp

#### Scenario: Run owner self-approves
- **WHEN** the Run owner attempts to approve their own action
- **THEN** the request is denied, approval remains pending and no resume is scheduled

#### Scenario: Cross-tenant reviewer submits a decision
- **WHEN** a reviewer from another tenant supplies a valid approval ID
- **THEN** the system reveals no approval and changes no state

#### Scenario: Reviewer lacks current tool permission
- **WHEN** a reviewer has generic review permission but cannot currently execute or authorize the requested tool
- **THEN** approval is rejected and no continuation grant is produced

### Requirement: Single Immutable Approval Decision
An approval decision SHALL be immutable and conditionally transition exactly once from `PENDING` to `APPROVED`, `REJECTED` or `EXPIRED`. Decision requests MUST accept only the decision and a bounded comment; they MUST NOT modify the tool, arguments, Run state, requester, expiry or budget.

#### Scenario: Concurrent approval and rejection race
- **WHEN** approve and reject requests target the same pending approval concurrently
- **THEN** one transition succeeds and the loser receives the already-decided state without overwriting it

#### Scenario: Approval expires
- **WHEN** the configured approval deadline passes while the decision remains pending
- **THEN** the approval and Run become expired through an idempotent transition and later decisions cannot revive them

#### Scenario: Client submits modified arguments with approval
- **WHEN** a decision request includes governance or tool payload fields
- **THEN** those fields are rejected and the encrypted server-owned pending action remains unchanged

### Requirement: Reauthorization Before Resume
An approved action MUST pass current authorization again before execution. Reauthorization SHALL include Run owner and reviewer enabled state, tenant membership, registered/enabled Tool, original Agent allowlist, required permissions, risk ceiling, approval identity and expiry. Failure MUST consume zero implementation attempts and close the action safely.

#### Scenario: Owner permission is revoked while waiting
- **WHEN** the original Run owner loses the tool permission before resume
- **THEN** the action does not execute and Audit records a reauthorization denial

#### Scenario: Tool is disabled after approval
- **WHEN** configuration disables the pending tool before a worker resumes it
- **THEN** no implementation executes even though the approval decision remains historically approved

#### Scenario: Valid approved action resumes
- **WHEN** owner, reviewer, tenant, Tool, allowlist, risk and approval remain valid
- **THEN** the existing governance pipeline may consume budget and execute under the original logical `toolCallId`

### Requirement: Idempotent Approved Action Execution
The system MUST associate the approved action with a stable `approvalId + toolCallId` idempotency identity. Database claiming SHALL prevent more than one active executor, and the sandbox write tool MUST use a unique business action key so retry after an unknown commit result returns the original outcome rather than applying the change twice.

#### Scenario: Approval endpoint is called repeatedly
- **WHEN** the same approval request is submitted multiple times
- **THEN** only the first valid decision changes state and at most one resumable action is produced

#### Scenario: Worker crashes after sandbox commit
- **WHEN** the sandbox write commits but the worker stops before recording tool success
- **THEN** a later lease holder finds the unique action result and returns it without changing the price again

#### Scenario: External connector lacks idempotency support
- **WHEN** a future approval-gated tool cannot provide a durable idempotency contract
- **THEN** it MUST NOT be enabled for automatic resume merely because the Run lease is unique

### Requirement: Approval API, Events And UI
The system SHALL provide tenant-scoped approval list/detail/approve/reject APIs and an administrator approval center. A streaming Run that suspends MUST emit one additive `approval_required` process event with safe identifiers and summary, then close the transport without reporting the Run as completed or failed.

#### Scenario: Reviewer opens approval center
- **WHEN** an authorized reviewer loads pending approvals
- **THEN** the UI displays tool, risk, requester, safe argument summary, request time and expiry with approve/reject controls

#### Scenario: Streaming Run suspends
- **WHEN** a tool action enters `WAITING_APPROVAL`
- **THEN** the client receives one approval-required event and a transport completion marker carrying the non-terminal Run status

#### Scenario: Unauthorized user opens approval APIs
- **WHEN** a user lacks the review permission
- **THEN** the server denies access and returns no approval metadata

### Requirement: Correlated Approval Audit
Every approval request, decision, expiry, resume claim, reauthorization outcome and final execution result SHALL write safe Audit/Trace evidence correlated by `runId`, `toolCallId` and `approvalId`. Raw arguments, encrypted payloads, credentials and unbounded reviewer comments MUST NOT be logged or exposed as metric tags.

#### Scenario: Approved sandbox action completes
- **WHEN** the price update succeeds after approval
- **THEN** Audit identifies requester and reviewer, Trace identifies the governed execution, and both reference the same three correlation IDs

#### Scenario: Approval is denied by policy
- **WHEN** self-approval, tenant mismatch or missing permission blocks a decision
- **THEN** a bounded safe denial reason is audited without exposing the pending raw action
