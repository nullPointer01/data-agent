## ADDED Requirements

### Requirement: Unified Agent Run identity
The system SHALL create exactly one server-generated `runId` for every accepted analysis execution and SHALL use that identifier throughout routing, events, logs, responses, cancellation, and trace persistence. Run governance fields MUST be derived from server configuration and MUST NOT trust client-supplied counters or terminal status.

#### Scenario: Synchronous run receives one identity
- **WHEN** a valid request enters the synchronous analysis endpoint
- **THEN** the final response contains the single `runId` created before execution and all trace metadata for that request uses the same identifier

#### Scenario: Streaming run exposes identity before work events
- **WHEN** a valid request enters the streaming analysis endpoint
- **THEN** the first Agent event identifies the `runId` before any model token, tool call, orchestration, error, or terminal event

### Requirement: Controlled run lifecycle
The system SHALL model a run using `CREATED`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`, `TIMED_OUT`, and `BUDGET_EXHAUSTED`. A run MUST transition only from `CREATED` to `RUNNING` and from `RUNNING` to exactly one terminal state, and concurrent completion attempts MUST NOT replace the first terminal state.

#### Scenario: Successful run completes once
- **WHEN** an execution strategy returns a successful response while the run is still active
- **THEN** the run transitions from `RUNNING` to `COMPLETED` and rejects any later terminal transition

#### Scenario: Timeout races with successful completion
- **WHEN** deadline detection and strategy completion attempt terminal transitions concurrently
- **THEN** exactly one transition wins and all response, event, log, and trace consumers observe the winning state

#### Scenario: Exception fails an active run
- **WHEN** execution raises an unhandled exception before another terminal condition wins
- **THEN** the run transitions to `FAILED` with a safe reason and still performs scope and registry cleanup

### Requirement: Transport-independent execution routing
The system SHALL resolve the execution target and `CHAT`, `REACT`, or `ORCHESTRATED` mode before selecting synchronous or streaming output mechanics. The route resolver MUST NOT receive or inspect the transport mode, and a streaming request MUST NOT bypass Orchestrator solely because it is streaming.

#### Scenario: Default request with Orchestrator available
- **WHEN** the same default request is submitted through synchronous and streaming analysis endpoints while Orchestrator is available
- **THEN** both runs resolve to `ORCHESTRATED`

#### Scenario: Default request without Orchestrator
- **WHEN** the same default request is submitted through synchronous and streaming analysis endpoints while Orchestrator is unavailable
- **THEN** both runs resolve to `REACT`

#### Scenario: Configured Agent chooses mode
- **WHEN** an enabled AgentProfile specifies the existing `chat` or `react` execution mode
- **THEN** both transports use the corresponding execution strategy without changing the stored profile schema

#### Scenario: Command and explicit Skill preserve priority
- **WHEN** a request is a recognized slash command or explicitly identifies a Skill
- **THEN** the existing shortcut priority is preserved while its execution still receives the common run identity, lifecycle, limits, events, and trace

### Requirement: Server-controlled run limits
Every run SHALL receive positive server-controlled limits for total duration, iterations, model calls, tool calls, and consumed tokens. Iteration, model-call, and tool-call admissions MUST be atomic so concurrent Orchestrator tasks cannot exceed their respective count limits.

#### Scenario: ReAct reaches iteration limit
- **WHEN** the next ReAct iteration would exceed `maxIterations`
- **THEN** the iteration is not started and the active run transitions to `BUDGET_EXHAUSTED` with `ITERATION_LIMIT` as its safe reason

#### Scenario: Parallel tasks compete for model-call budget
- **WHEN** concurrent Orchestrator tasks request model calls near `maxModelCalls`
- **THEN** at most the configured number obtain admission and all rejected calls observe the same exhausted run state

#### Scenario: Tool-call limit is exhausted
- **WHEN** the next tool invocation would exceed `maxToolCalls`
- **THEN** the tool implementation is not invoked and the run transitions to `BUDGET_EXHAUSTED` with `TOOL_CALL_LIMIT`

#### Scenario: Token budget is exhausted after a call
- **WHEN** actual provider TokenUsage or the fallback estimate reaches or exceeds `maxTokens`
- **THEN** the usage is recorded, the run is marked `BUDGET_EXHAUSTED`, and no later model or tool call is admitted

#### Scenario: Client attempts to enlarge limits
- **WHEN** an analysis request contains unknown or future fields representing larger runtime limits
- **THEN** the server ignores or rejects those fields and uses limits bounded by server configuration

### Requirement: Model and tool boundary governance
All model and tool invocations made while an Agent Run is in scope MUST check active status, thread interruption, cancellation, deadline, and the corresponding budget before initiating the external or tool operation. Non-Agent callers outside a Run Scope SHALL retain their existing model and tool behavior.

#### Scenario: Model call is admitted
- **WHEN** an active run is within deadline and has remaining model and Token budget
- **THEN** the model-call count is atomically consumed before the provider request and returned TokenUsage is added to the same run

#### Scenario: Model provider omits TokenUsage
- **WHEN** a model call succeeds without provider TokenUsage
- **THEN** the system uses the existing token estimator and marks the resulting usage as estimated in the run snapshot

#### Scenario: Tool call is rejected after cancellation
- **WHEN** cancellation wins before `AgentToolInvoker` begins a tool implementation
- **THEN** the tool-call count and tool implementation invocation do not increase

#### Scenario: Non-Agent model operation runs without scope
- **WHEN** an administrative or background model operation calls the model service without an active Agent Run
- **THEN** existing user quota, retry, token accounting, and error behavior continue without creating an implicit run

### Requirement: Deadline and cooperative cancellation
The system SHALL support deadline expiration, thread interruption, SSE disconnect cancellation, and authorized explicit cancellation for active in-process runs. Cancellation SHALL be cooperative: an already in-flight provider or tool call may finish, but the system MUST prevent subsequent calls and non-terminal business events after observing the terminal condition.

#### Scenario: Deadline expires before next operation
- **WHEN** current time is at or after the run deadline before an iteration, model call, or tool call
- **THEN** the operation is not started and the run transitions to `TIMED_OUT`

#### Scenario: Streaming client disconnects
- **WHEN** the SSE emitter reports timeout or connection error for an active run
- **THEN** the stream task is interrupted, the run receives a cancellation request, and subsequent execution boundaries stop work

#### Scenario: Owner explicitly cancels a run
- **WHEN** the authenticated tenant and user cancel their currently active `runId`
- **THEN** the run transitions to `CANCELLED` or reports the already-winning terminal state without leaking another user's run details

#### Scenario: Unauthorized user attempts cancellation
- **WHEN** a different tenant or user requests cancellation for a `runId`
- **THEN** the system does not cancel that run and returns a non-disclosing not-found or forbidden result

#### Scenario: Cancelled provider request returns late
- **WHEN** a model request was already in flight when cancellation won and later returns content
- **THEN** its accounting may be settled but no new model call, tool call, or successful completion replaces `CANCELLED`

### Requirement: Run Scope propagation and cleanup
The system SHALL make the active run available to deep model and tool gateways and SHALL propagate it into decorated Orchestrator worker tasks. Scope binding MUST restore or remove the previous value in a `finally` block for request threads and pooled worker threads.

#### Scenario: Parallel specialist inherits run
- **WHEN** Orchestrator submits a specialist through the configured decorated executor
- **THEN** the specialist's model and tool gateways consume the parent run's shared atomic limits and use the same `runId`

#### Scenario: Worker thread is reused
- **WHEN** a pooled thread finishes one Agent task and later executes unrelated work
- **THEN** the unrelated work cannot observe the previous Agent Run Scope

#### Scenario: Nested scope restores parent
- **WHEN** a structured scope is temporarily nested inside another bound scope
- **THEN** leaving the nested scope restores the previous context instead of clearing it incorrectly

### Requirement: Unified typed events
Internal Agent execution SHALL publish typed events through a transport-independent sink. Every event MUST include `runId`, execution mode, event time, and type; only the run coordinator SHALL publish start and terminal events. A sink MUST reject process events after accepting a terminal event.

#### Scenario: Streaming ReAct emits process events
- **WHEN** a ReAct strategy produces model output and invokes a tool
- **THEN** the sink receives typed model-token and tool-call events associated with the same run before one terminal event

#### Scenario: Orchestrated stream preserves selected mode
- **WHEN** an Orchestrated strategy currently produces some results synchronously
- **THEN** its plan and task results are emitted as `ORCHESTRATION` process events and the stream still identifies the mode as `ORCHESTRATED`

#### Scenario: Lower layer attempts duplicate completion
- **WHEN** a lower execution component returns or attempts to publish completion after the coordinator has terminated the run
- **THEN** no second terminal event is delivered

### Requirement: SSE backward compatibility
The SSE adapter SHALL preserve existing event `type` values and payload fields needed by current clients while adding `runId`, execution mode, run status, and usage metadata where applicable. The stream SHALL publish exactly one `done` terminal event for every started run, including failed, cancelled, timed-out, and budget-exhausted runs.

#### Scenario: Existing client consumes token output
- **WHEN** a strategy emits a model-token event
- **THEN** the SSE payload still uses the existing `token` type and `content` field in addition to the new run metadata

#### Scenario: Run terminates without successful answer
- **WHEN** a started run fails, is cancelled, times out, or exhausts budget
- **THEN** the stream emits an actionable safe error or status event followed by exactly one `done` event carrying the terminal status

### Requirement: Unified response and trace metadata
Synchronous responses and persisted execution traces SHALL expose the run identifier, resolved mode, terminal status, duration, iteration count, model-call count, tool-call count, Token usage, and termination reason. The system MUST NOT persist or emit API Keys, complete system prompts, or hidden chain-of-thought.

#### Scenario: Chat run is traced
- **WHEN** a Chat execution reaches a terminal state
- **THEN** the existing trace storage receives a record keyed by `runId` even though no ReAct iteration or Orchestrator plan exists

#### Scenario: Existing ReAct and Orchestrator detail remains available
- **WHEN** a ReAct or Orchestrated run is traced
- **THEN** existing safe thinking-step or plan/task summaries remain available alongside the common run metadata

#### Scenario: Trace persistence fails
- **WHEN** the trace repository fails after the business terminal state is chosen
- **THEN** the business response retains its run status, the persistence failure is logged with `runId`, and no secret or hidden reasoning is added to the error

### Requirement: Existing runtime compatibility
The change SHALL preserve the existing analysis endpoint paths, session and tenant semantics, AgentProfile records, Tool and Skill registration, RAG and Memory behavior, and model-provider integration without a database migration.

#### Scenario: Existing AgentProfile executes after upgrade
- **WHEN** an existing enabled profile with `chat` or `react` execution mode is invoked
- **THEN** it uses the corresponding unified strategy with its configured model, prompt, Tools, Skills, data source, tenant, and memory behavior intact

#### Scenario: Existing API client omits run-aware fields
- **WHEN** a current client sends the same AnalysisRequest shape used before the change
- **THEN** the server creates all run governance state internally and processes the request without requiring a client migration
