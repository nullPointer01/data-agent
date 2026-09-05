# Static Verification Report

## Summary

- Change: `build-unified-agent-run-harness`
- Scope: Unified Agent Run lifecycle, routing, budgets, cancellation, typed events and Trace correlation
- Verification mode: Static acceptance only
- Result: `PASS_WITH_NOTES`
- Date: 2026-09-03

The implementation satisfies the change's static acceptance criteria. The remaining notes are runtime risks that cannot be closed under the explicit project constraint that Maven compilation, automated tests and frontend builds must not be run.

## Success Criteria Evidence

### SC-1: One Run entry and one stable runId

Result: PASS (static)

- `AgentRuntimeService.execute*` delegates both transports to `AgentRunCoordinator`.
- `AgentRunCoordinator.createRunContext` creates one UUID before execution and registers that context once.
- The same `AgentRunContext` supplies events, response metadata, cancellation lookup and Trace persistence.
- `AgentExecutionTraceService.recordRun` writes `context.runId()` into `trace_id`.

Manual walkthrough: a valid synchronous or streaming request reaches `DataAnalysisAgentImpl`, builds one `AgentExecutionContext`, then enters the Coordinator once. No lower execution strategy creates another run identity.

### SC-2: Transport-independent routing

Result: PASS (static)

- `AgentRunRouteResolver.resolve` has no streaming, SSE or transport parameter/condition.
- Routing order is command, explicit Agent, explicit Skill, then default Orchestrator/ReAct fallback.
- Sync and stream methods both call the same Coordinator and resolver; streaming only changes the event sink.

Manual walkthrough: identical default requests resolve to `ORCHESTRATED` when the bean is present and `REACT` when absent, regardless of the endpoint used. Existing AgentProfile `chat` and `react` values map to `CHAT` and `REACT` without schema changes.

### SC-3: Controlled lifecycle and one terminal state

Result: PASS (static)

- `AgentRunControl` stores status, reason, detail and completion time in one atomic lifecycle value.
- Only `CREATED -> RUNNING` and `RUNNING -> terminal` CAS transitions are accepted.
- Completion, failure, cancellation, timeout and budget exhaustion compete for the first terminal transition.
- `GuardedAgentEventSink` accepts only the first terminal event and rejects later events.
- Only `AgentRunCoordinator` publishes `RUN_STARTED` and `RUN_TERMINATED`.

Manual walkthrough: if deadline and successful completion race, the first CAS wins; `finalizeRun` reads the winning snapshot and later transitions cannot replace it.

### SC-4: Five server-controlled limits

Result: PASS (static)

- `AgentRuntimeProperties` defines positive timeout, iteration, model-call, tool-call and Token defaults.
- `AgentRunLimits` validates all five values and is immutable.
- `application.yml` binds only server configuration under `app.agent.runtime.*`; `AnalysisRequest` does not own Run governance fields.
- Atomic counters in `AgentRunControl` guard concurrent admissions.

Manual walkthrough: concurrent worker tasks share the same `AgentRunControl`; at most one task wins the final available counter slot.

### SC-5: Model/tool boundaries, deadline and cancellation

Result: PASS (static)

- `McpModelService.beginAgentModelCall` performs model admission before provider invocation and settles actual or estimated Token usage afterward.
- SDK retry attempts remain inside one admitted business model call.
- `AgentToolInvoker` performs tool admission before executing the registered implementation.
- `ReActLoopRunner` performs iteration admission and uses Run limits instead of a hardcoded eight-round constant.
- `AgentRunRegistry` and `AgentRunCancellationService` provide tenant/user-bound, node-local cancellation.
- `AnalysisStreamService` cancels the active Run and interrupts its Future on timeout, disconnect or transport failure.

Manual walkthrough: cancellation during an in-flight provider request may not stop that HTTP request, but the terminal CAS remains `CANCELLED`; the next boundary check rejects further model/tool work and the guarded sink drops later process events.

### SC-6: Response/SSE correlation and compatibility

Result: PASS (static)

- `AnalysisResponse` exposes `runId`, mode, status, termination reason and usage while retaining prior fields.
- `AgentSseEventWriter` preserves legacy event types and payload keys and adds common Run metadata.
- The first accepted Run event is `run_started`; the terminal event maps to exactly one `done`.
- `ChatPage.jsx` captures `runId`, keeps existing `token`, `tool_call`, orchestration, error, session and trace handling, and requests cancellation before aborting the stream.

Manual walkthrough: current frontend parsing selects known fields from JSON objects, so added fields are ignored without breaking existing branches. This confirms compatibility for the repository's current client, not every external client.

### SC-7: Existing runtime compatibility

Result: PASS (static)

- Existing `/api/v1/analysis/analyze` and `/api/v1/analysis/analyze/stream` paths remain unchanged.
- The new cancellation endpoint is additive.
- Thin strategies delegate to the existing configurable Agent, ReAct and Orchestrator implementations.
- Commands, explicit Skills, Profile model/prompt/tools/skills, RAG, Memory and session handling stay on existing services.
- No entity, repository schema, SQL migration or new persistence table was added by this change.

Manual walkthrough: existing request payloads need no Run fields; all governance state is generated from server configuration after request validation.

### SC-8: Unified Trace and sensitive-data boundary

Result: PASS_WITH_NOTES (static)

- `AgentExecutionTraceService.recordRun` records the runId as traceId plus mode, status, duration, iteration/model/tool counts, Token usage and termination reason.
- Production call sites use only `recordRun`; legacy `record` and `recordReAct` methods have no production callers.
- Coordinator filters `thinking`, `thought` and `chain_of_thought` response steps before events and Trace persistence.
- Coordinator sanitizes API-key-like values from surfaced errors.
- Repository scan found no committed `sk-*` or Bearer secret; the only API-key-like match is the documented local placeholder.

Note: tool results and provider payloads can only be fully validated with representative runtime fixtures. Existing output truncation and the new hidden-reasoning/error filters reduce exposure, but runtime content inspection was not performed.

## Static Checks

Executed successfully:

```text
openspec validate build-unified-agent-run-harness --strict
git diff --check
```

Targeted source scans confirmed:

- Resolver contains no transport or streaming branch.
- Coordinator is the only publisher of start and terminal Agent events.
- Conversation persistence calls exist only in Coordinator.
- No production caller remains for legacy `recordReAct` or Orchestrator `record` paths.
- Model, tool and iteration admission guards are present at their execution boundaries.
- Run Scope is restored in `finally`, including decorated Orchestrator tasks.
- No merge-conflict marker was found.
- No committed real API key or Bearer credential was detected.

## Explicit Omissions

The following were intentionally not performed:

- Maven compilation, package, validate or verify
- Automated test creation or execution
- Coverage measurement
- Frontend build
- Docker build or dependency startup
- Real provider calls and real long-running cancellation scenarios
- Multi-node cancellation validation

These omissions follow the user's explicit project constraints. Cancellation remains node-local by design; persistent Checkpoint/Resume, cross-node cancellation, tool approval and retry/idempotency governance remain future changes.

## Remaining Risks

- Runtime defaults are protective starting values and need P50/P95/P99 Trace data before tuning.
- Provider TokenUsage availability varies; fallback estimation remains necessary and has not been calibrated across all providers.
- Cooperative cancellation cannot guarantee immediate interruption of an already-issued HTTP/tool operation.
- Source consistency is statically checked, but compile/runtime compatibility is not proven until the user authorizes a JDK 17 build or manual smoke test.

