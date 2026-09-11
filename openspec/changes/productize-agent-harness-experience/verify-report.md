# Verification Report

## Decision

- Change: `productize-agent-harness-experience`
- Decision: `PASS_WITH_UNVERIFIED_RUNTIME`
- Completed tasks: 38/41
- Remaining tasks: `1.7`, `2.10`, `3.5`
- Constraint: no automated tests, Maven compilation/package, or backend restart were performed, following the user's explicit instruction.

The implementation and static evidence are internally consistent, and the frontend production build plus mocked desktop/mobile visual checks passed. The change is not ready to archive because three stage gates require authenticated execution against the rebuilt backend and real external dependencies.

## Success Criteria

| Criterion | Result | Evidence | Remaining condition |
|---|---|---|---|
| SC-1 Product identity | PASS | The ordinary-user shell has three primary destinations; the My Agent viewport exposes Agent identity, task input, recent Run entry and Harness summary. `CapabilityPicker` was visually checked at 1280x720 and 390x844 with sanitized API fixtures and no final console errors. | Real-role navigation should still be checked after login against the rebuilt backend. |
| SC-2 Run evidence | UNVERIFIED | Static flow preserves one canonical `runId/traceId` through Coordinator, Trace, conversation `run_id`, owner-safe Run API and Run Inspector. The Inspector maps status, mode, duration, model calls, tool calls, Token usage and termination reason. | Rebuild/restart backend, run one authenticated SSE request, refresh the session and compare live and historical Run evidence. |
| SC-3 Event loop | UNVERIFIED | Typed live events and safe persisted projections cover start, routing/planning, retrieval, tool, approval/resume, budget and terminal states. Persisted fallback correctly marks incomplete event history. | Capture a real SSE timeline plus one durable approval/resume timeline. |
| SC-4 Task contract | UNVERIFIED | Optional immutable goal/criteria contracts, Checkpoint v2 compatibility and deterministic `ACHIEVED`, `NOT_ACHIEVED`, `NOT_EVALUATED` branches are wired through normal and resumed Runs. | Execute legacy, achieved, failed-criterion and insufficient-evidence requests on the rebuilt backend. |
| SC-5 Agent evaluation | UNVERIFIED | Versioned Eval entities, isolated executor, six Harness metric aggregation and sample-level drill-down are present. Missing RAG citation evidence is reported unavailable rather than zero. | Apply `sql/upgrade-agent-eval.sql`, execute a fixed dataset twice and compare deterministic results. |
| SC-6 Context governance | UNVERIFIED | Chat, ReAct and orchestrated model calls pass through the Context Governor; safe evidence records before/after Token estimates, retained messages, reason and estimate flag. No-compression is represented explicitly. | Demonstrate one actual reduction or budget rejection and inspect both live and historical evidence. |
| SC-7 Interview demonstration | PASS | `docs/interview/AGENT_HARNESS_DEMO.md` contains four repeatable paths for knowledge evidence, governed approval/recovery, budget termination and composable capabilities. Each includes setup, request, expected evidence, fault injection and claim boundaries; referenced endpoints, flags, tools and reason codes were statically matched to source. | Running the scripts is part of the three open stage gates, not a substitute for this documentation check. |
| SC-8 Problem closure | PASS | `problem-log.md` records P001-P045 with status, impact, root cause, resolution, evidence and residual risk. Stage 5 implementation gaps P028-P045 are resolved; remaining OPEN/MONITORING entries are explicit product debt rather than hidden blockers. | P018 remains explicit until bounded RAG citation evidence is persisted; P008 and monitoring items remain outside this change's required runtime gates. |
| SC-9 Capability binding | PASS (static) | Profile storage distinguishes legacy `NULL` from explicit `[]`, validates at most 64 stable identities and returns Tool/Skill/sub-Agent bindings losslessly. Sanitized frontend fixtures submitted a mixed four-item binding list and an explicit zero-capability list with matching legacy Tool projections. | Apply `sql/upgrade-agent-capability-bindings.sql`, save both forms through the rebuilt backend and reload them from MySQL. |
| SC-10 Governed composition | PASS (static) | Save-time admission rejects unknown, unavailable, unauthorized, self and cyclic bindings. Runtime snapshots re-check tenant/RBAC/lifecycle; Skill uses an exact stable-ID adapter; child delegation uses its own Profile under the root Run scope, rejects cycles and a fourth level, and uses a separate bounded executor without bypassing the Tool Pipeline. | Capture a real parent-child model/tool Run and prove shared counters, deadline, rejection evidence and cancellation behavior. |
| SC-11 Mode consistency | PASS (static) | `PersonalAgentExecutionModeResolver`, route resolution and configurable execution preserve Chat/ReAct/Orchestrated boundaries in synchronous and streaming paths. Chat builds no callable specifications; ReAct adds bound Tool/Skill adapters; Orchestrated additionally adds the bound delegation adapter; complex Auto requires a currently usable child Agent. | Run the four-mode matrix against a rebuilt backend and inspect the actual model requests and Run evidence. |
| SC-12 Configuration experience | PASS | Personal Agent settings select all three capability types, display per-type counts and four mode explanations, preserve removable unavailable historical bindings, disable unavailable new choices and block saving when the directory fails. Desktop/mobile fixtures had no nested labels, no root overflow and no console errors. | Repeat the save/reload flow against the real authenticated capability directory. |

## Static And UI Verification

- Frontend production build passed after the Capability Picker integration. Generated assets are present under `src/main/resources/static/assets`.
- Mocked authenticated UI checks passed at desktop and mobile widths. Screenshots are under `output/playwright/`.
- Capability descriptors contain stable identity, type, version, contracts, tenant/owner, permissions, risk, lifecycle and availability.
- Capability discovery filters by tenant, sub-Agent ownership and persistent RBAC permissions; Profile mutation performs the same bindability check server-side.
- Capability Registry is read-only. Tool execution ownership remains in `AgentToolExecutionPipeline`.
- Personal Agent settings use the unified stable binding contract for Tool, Skill and sub-Agent selections; the Runtime resolves the effective subset again for every Agent layer and execution mode.
- Fixture save payload preserved `tool:searchKnowledge`, `skill:skill-1`, `agent:agent-child` and `tool:updatePrice`; explicit zero capability saved both `tools: []` and `capabilityBindings: []`.
- Responsive fixture measurements were 390/390 viewport/document width with a 374/374 modal, and 1280/1280 with a 920/920 modal. Nested labels and final console errors were both zero.
- SQL changes are additive and separated into explicit upgrade scripts, but they were not applied during this verification pass.

## Security Review

- Owner Run detail remains scoped by tenant and user and returns safe projections rather than Checkpoint ciphertext or raw tool arguments.
- Model-proposed tool names and arguments remain untrusted and must pass Registry, RBAC, Agent allowlist, risk policy and output sanitization.
- Profile binding cannot rely on frontend hiding: unknown, unauthorized or unavailable Tool/Skill/sub-Agent identities are rejected by the service, as are self and known cyclic Agent references.
- If the capability directory cannot be loaded, the UI blocks Agent configuration saves instead of treating failure as an empty Tool list with legacy “all tools” semantics.
- No API keys, complete prompts, raw sensitive Tool values or hidden model reasoning were added to the UI, Trace or documentation.
- Administrators can discover tenant sub Agents; ordinary users only discover their own. Self-binding is excluded by `agentId` when editing.

## Anti-Scope Check

- No Dify-style workflow canvas was introduced.
- The product remains a Harness reference application, not a claim of a universal consumer assistant.
- The sandbox demo is not described as strong isolation or cross-system exactly-once.
- No full event-sourcing system or multi-level approval graph was added.
- Existing dirty worktree content was preserved; no destructive repository rewrite was performed.

## Required Runtime Acceptance

1. Task `1.7`: authenticate as normal user and administrator, execute a real SSE Run, inspect the seven summary fields and six event classes, refresh, and confirm owner-only historical evidence.
2. Task `2.10`: apply the Eval migration and execute the same fixed dataset twice; verify the six metrics and sample Run drill-down without inferred RAG quality.
3. Task `3.5`: enable durable mode in an isolated environment, demonstrate one budget rejection or real context reduction, then complete one approval pause/resume path.

Stage 5 runtime evidence should be captured alongside these gates: save/reload explicit and legacy bindings, exercise the four-mode matrix, and execute one parent-child delegation with shared root Run controls.

Until those three checks pass, this change must remain active and its runtime claims must be described as unverified.
