# SDD Assumptions

## Active Assumptions

### A009 Unified bindings can version legacy Tool semantics without destructive migration

- Source: `AgentProfile.tools`, `AgentToolInvoker.buildToolSpecifications` and current Profile API
- Confidence: high
- Assumption: nullable `capability_bindings` can distinguish legacy fallback (`NULL`) from a new explicit empty set (`[]`) while old clients continue sending `tools`.
- Verification: `AgentProfile` preserves nullable JSON, `AgentProfileResponse` exposes both the lossless identity list and `explicitCapabilityBindings`, `AgentProfileService` separates new and legacy update branches, and the frontend fixture submitted both a mixed three-type list and an explicit empty list without loss. The additive SQL migration has not been applied in this verification environment.
- Status: statically confirmed; live database persistence remains unverified

### A010 Child Agents can share root Run governance

- Source: `AgentRunScope`, `ContextPropagatingTaskDecorator`, `McpModelService` and `AgentToolExecutionPipeline`
- Confidence: medium
- Assumption: nested configured Agent execution inside the same `AgentRunScope` will consume the root model/Tool/Token/deadline budgets if the current capability scope is also propagated to Tool executor threads.
- Verification: `delegateToAgent` reuses the active `AgentRunScope`; the dedicated delegation executor propagates that scope; nested `ConfigurableAgentExecutor` calls resolve a child capability snapshot; `McpModelService` and `AgentToolExecutionPipeline` debit model, Token, Tool and remaining-time limits from the same root control. `AgentResumeWorker` rebuilds the current Profile scope before continuing an approval Checkpoint.
- Status: statically confirmed; a real nested Run and approval-resume Run remain unverified

### A001 Current worktree is the integration baseline

- Source: repository inspection
- Confidence: high
- Assumption: Existing modified and untracked files are intentional current work and must be preserved.
- Verification: Review every touched file against `git diff`; never reset or restore unrelated changes.
- Status: confirmed

### A002 Agent Run remains the canonical execution identity

- Source: `AgentRunCoordinator`, `AnalysisResponse`, `AgentExecutionTraceService`
- Confidence: high
- Assumption: `runId` and `traceId` can remain the same canonical correlation value across modes.
- Verification: `AgentRunCoordinator` creates one UUID; `AgentExecutionTraceService.recordRun` writes it as `traceId`; `AgentDurableRunStore.create` writes it as the durable primary key; `AgentResumeWorker` restores and appends to the same Run and Trace.
- Status: confirmed

### A003 Existing SSE terminal event contains enough live summary data

- Source: `AgentRunCoordinator.emitTerminal` and `AgentRunSnapshot`
- Confidence: high
- Assumption: Stage 1 can display status, duration, calls, Tokens and termination reason without changing the live event schema.
- Verification: Capture a real successful and failed SSE response after frontend integration.
- Status: statically confirmed, runtime unverified

### A004 Historical messages do not preserve full Run evidence

- Source: `ChatPage` local state and conversation response inspection
- Confidence: medium
- Assumption: Reloading a session loses transient `traceEvents`, usage and outcome details.
- Verification: `ConversationMessage` has no Run field, `SessionManager.saveMessage` accepts no Run argument, and `SessionMessagesResponse` directly returns those entities. `ChatPage` keeps Run events only in component state.
- Status: confirmed by code; runtime reproduction remains for Stage 1

### A005 Durable persistence currently covers approval-oriented Runs only

- Source: durable runtime documentation and `AgentDurableRunStore`
- Confidence: medium
- Assumption: ordinary successful Runs cannot all be queried through `/api/v1/agent-runs/{runId}` when durable mode is disabled.
- Verification: `AgentRunCoordinator` calls `AgentDurableRunStore.create` before every strategy whenever durable mode is enabled, then synchronizes every terminal Run. When disabled it creates no durable row and `AgentDurableRunService` rejects reads. The original “approval-only persistence” premise is incorrect.
- Status: invalidated and replaced by A008

### A006 First Agent Eval can reuse existing Trace and RAG benchmark evidence

- Source: `AgentQualityService`, `AgentExecutionTraceService`, RAG eval package
- Confidence: medium
- Assumption: common metrics can be derived without duplicating execution telemetry, but dataset/run entities will likely be new.
- Verification: `AgentEvalMetricCalculator` derives six Harness metrics from persisted sample Run evidence; RAG reference quality remains a separate explicitly unavailable metric until bounded citation IDs are persisted.
- Status: confirmed with the limitation tracked as P018

### A007 No automated tests or backend compilation

- Source: explicit prior user instruction and active change states
- Confidence: high
- Assumption: New automated tests, Maven compile/package, and backend startup remain prohibited until the user explicitly changes the instruction.
- Verification: Use frontend build, static checks and user-approved live HTTP/browser verification only.
- Status: confirmed

### A008 Trace is the durable fallback evidence for ordinary Runs

- Source: `AgentExecutionTraceService.recordRun`, `AgentRunCoordinator.finalizeRun`, `SecurityConfig`
- Confidence: high
- Assumption: A successful Trace write preserves the canonical Run identity and safe run metadata even when durable mode is disabled, but its existing API is admin-only.
- Verification: `recordRun` persists `context.runId()` plus status, reason, duration, iterations, calls and Tokens before the terminal SSE event. `/api/v1/agent-traces/**` is explicitly listed in `ADMIN_ENDPOINTS`.
- Status: confirmed; Stage 1 needs a narrower owner-only projection rather than opening the admin endpoint

## Invalidated Assumptions

- A005's title implied that durable persistence only covered approval Runs. Code proves all Runs are persisted when the durable feature is enabled; the actual limitation is the feature flag and missing conversation-to-Run reference.
