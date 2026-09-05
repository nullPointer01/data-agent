# Static Verification Report

- Change: `govern-agent-tool-execution`
- Date: 2026-09-04
- Baseline: JDK 17, Spring Boot 3.2, LangChain4j 1.19
- Result: Static acceptance passed; runtime behavior remains unverified under the explicit no-build/no-test constraint.

## Verification Constraints

Per the user's explicit project constraint, this verification did not create or run automated tests, execute Maven compile/package/validate/verify, or run a frontend build. Evidence below comes from OpenSpec validation, source-level call-chain inspection, configuration inspection, targeted searches, manual scenario walkthroughs and diff integrity checks.

## Success Criteria Evidence

### SC-1 - One Governed Execution Boundary

Passed by static inspection.

- `AgentToolRegistry` contains the only `DefaultToolExecutor.builder()` registration site.
- `AgentToolExecutionPipeline` contains the only `registered.executor().execute(...)` implementation call.
- The only two application invocation sites use `AgentToolInvoker.invokeStructured(...)`: `ReActStepHandler` and `ParallelPlanExecutor`.
- No `toolInvoker.invoke(...)` call site remains.
- The six ReAct loop call sites all pass an `AgentToolInvocationContext` into the two current loop signatures.

### SC-2 - Principal, Tenant And Agent Allowlist Intersection

Passed by static inspection and scenario walkthrough.

- `AgentRunCoordinator` resolves one persisted `AgentToolAuthorizationSnapshot` before entering `AgentRunScope`.
- `AgentToolAuthorizationService` rejects missing, disabled or cross-tenant users and stores immutable permission/tool sets without credentials.
- `AgentToolPolicyEngine` requires a Run snapshot, current invocation allowlist, server-enabled tool, RBAC permission and accepted risk level.
- Profile contexts derive names from the exact filtered `ToolSpecification` list. The fixed Orchestrator precheck allowlist is `searchKnowledge`, `listDataSources` and `searchMemory`.
- Unknown, invalid and unauthorized requests return before `beforeToolCall()`, so implementation attempts and tool budget consumption remain zero.

### SC-3 - Complete Tool Metadata

Passed by static inspection.

- Annotation scan found 18 `@Tool` methods and 18 `@AgentToolPolicy` declarations.
- No retryable declaration lacks `readOnly=true` or `idempotent=true`.
- `AgentToolDescriptor` rejects missing policy, invalid limits, blank permissions, unsafe retry declarations and multi-attempt non-retryable tools during registry construction.
- Duplicate model-visible tool names and unknown configured enabled-tool names fail registry construction.

### SC-4 - Stable Logical Call And Bounded Attempts

Passed by static control-flow inspection; real timing is unverified.

- One `toolCallId` is created before the attempt loop and reused for the final structured result, Journal, event and log.
- Maximum attempts are the lower of descriptor and server limits.
- Every implementation attempt calls `ensureActive()` and `beforeToolCall()` before submission.
- Effective waiting time is the lower of tool/global timeout and Run remaining deadline; timeout cancels the Future and discards late output.
- Retry backoff is bounded and rechecks Run state before another attempt.

### SC-5 - Retry Safety And Zero-Attempt Rejections

Passed by static control-flow inspection.

- Retry requires all of `readOnly`, `idempotent`, `retryable`, a retriable classified failure and an active Run.
- Only explicit timeout, transient connection, HTTP 429 and HTTP 5xx paths are classified retriable.
- Unknown tools, malformed/oversized/Schema-invalid arguments, RBAC/allowlist rejection and risk rejection finish before the attempt loop.
- Non-retryable or non-idempotent descriptors are limited to one attempt.

### SC-6 - Structured And Safe Results

Passed by static inspection.

- `AgentToolExecutionStatus` covers success, unknown tool, invalid arguments, authorization/policy rejection, timeout, execution failure and Run termination.
- ReAct recovery branches on structured status, not localized error-message prefixes.
- `AgentToolOutputSanitizer` redacts Bearer tokens, API keys, secret-like fields and URI credentials before output reaches the model, event, Journal or structured tool log.
- Output length uses the lower of the descriptor and server limit and records sanitization/truncation flags.
- The obsolete structured logger entry point accepting raw tool parameters/results was removed.

### SC-7 - Correlated And Bounded Observability

Passed by static inspection.

- `AgentToolTelemetry` is the only `AgentEventType.TOOL_CALL` producer and emits once from the logical invocation finish path.
- Journal records contain `runId`, `toolCallId`, tool/risk/status, authorization, attempts, duration, safe argument summary and processing flags.
- The synchronized per-Run Journal is bounded and increments `overflowCount` when evicting its oldest record.
- Existing Trace `shared_context_json` stores the Journal records, capacity and overflow count without a schema migration.
- Metrics use only bounded tool/status/risk/outcome tags; IDs and exception messages are not metric tags.

### SC-8 - Existing Integration Compatibility

Passed by static interface inspection; runtime compatibility is unverified without compilation.

- Existing analysis paths and persistence schema were not changed by this change.
- `AgentProfile.tools` still filters the same LangChain4j specifications; an empty legacy list still means all server-enabled tools.
- Tool names, descriptions and model-visible parameter schemas continue to be generated from the existing `AgentTools` annotations.
- Default ReAct, configured Profile ReAct and Orchestrator precheck all enter the governed pipeline with an explicit server-created context.

## Manual Scenario Walkthroughs

| Scenario | Static outcome |
| --- | --- |
| Unknown tool | `UNKNOWN_TOOL`, 0 attempts, 0 tool budget, one safe logical event/Journal record |
| Malformed, oversized, missing or unknown argument | `INVALID_ARGUMENTS`, field/length/hash summary only, 0 attempts |
| Tool omitted from Profile | `UNAUTHORIZED`, 0 attempts, no implementation execution |
| Cross-tenant or disabled user | denied Run authorization snapshot; all tools rejected |
| Valid authorized tool | one budget admission followed by bounded executor submission |
| First transient failure then success | same `toolCallId`, two attempts only when retry safety conditions hold |
| Non-idempotent failure | at most one attempt |
| Tool timeout | Future interruption requested, safe `TIMEOUT`, late value not observed |
| Run cancellation/deadline/budget before retry | active-state check prevents the next attempt |
| Journal overflow | oldest record evicted, capacity retained, overflow count incremented |

## Commands And Integrity

- `openspec validate govern-agent-tool-execution --strict`: passed.
- Tracked `git diff --check`: passed.
- Targeted no-index whitespace checks for new governance and touched integration files: passed.
- Counts: 18 tools, 18 policies, 1 registry builder, 1 raw executor boundary, 2 structured invocation sites, 0 legacy invocation sites, 1 tool event producer.
- Source scans found no active logger call accepting raw tool arguments or raw tool results.

## Residual Risk

- No Java compiler or dependency resolver was executed, so imports, overload resolution and framework/API compatibility are not proven by this report.
- Real timeout accuracy, executor saturation, queue rejection, thread interruption behavior and context propagation were not exercised.
- `Future.cancel(true)` is cooperative. JDBC/HTTP clients that ignore interruption may continue in the background; client-level timeouts remain required.
- External systems, side effects and transient failure classifications were not exercised. The current tools are not a basis for claiming exactly-once behavior.
- Persistent approval, Checkpoint/Resume, cross-node cancellation/idempotency and real high-risk write tools remain intentionally out of scope.
