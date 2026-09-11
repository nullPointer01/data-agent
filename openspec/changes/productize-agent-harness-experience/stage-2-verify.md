# Stage 2 Verification

Status: `PARTIAL` - implementation is complete through task 2.9; authenticated runtime acceptance remains pending.

## Static Evidence

| Area | Result | Evidence |
|---|---|---|
| Task contract and Outcome | PASS | Legacy requests retain `NOT_EVALUATED`; normal and approval-resumed Runs keep lifecycle status separate from criterion-level Outcome. |
| Canonical execution path | PASS | `AgentEvalService` calls `DataAnalysisAgent.analyze`; configured samples continue through `AgentRuntimeService -> AgentRunCoordinator`. |
| Transaction boundary | PASS | Dataset/result reads and writes use short `TransactionTemplate` scopes; model execution occurs outside a database transaction. |
| Workload isolation | PASS | `agentEvalExecutor` is bounded and separate from the interactive Orchestrator executor while reusing security/MDC context propagation. |
| Six required metrics | PASS | Completion, tool selection, invalid-loop, approval accuracy, P95 duration and average Token all expose numerator/denominator or evidence count. Missing labels/evidence return `available=false`, never a fabricated zero. |
| Sample drill-down | PASS | Report rows preserve `agentRunId`, `traceId`, safe Outcome details, labels, observations and safe errors; the admin page opens the existing Trace detail view. |
| RAG separation | PASS | RAG reference hit rate is a separate metric and remains unavailable until bounded sample-level citation IDs are persisted. |
| Frontend production build | PASS | Vite 7.3.3 transformed 1734 modules and emitted `index-DfTLh49a.js` plus `index-h8nMWCER.css` on 2026-09-06. |

## Fixed Metric Walkthrough

The deterministic walkthrough uses three samples:

| Sample | Outcome | Expected / observed tools | Approval expected / observed | Invalid loop expected / observed | Duration | Token |
|---|---|---|---|---|---:|---:|
| A | ACHIEVED | `[search] / [search]` | `false / false` | `false / false` | 100 ms | 10 |
| B | NOT_ACHIEVED | `[calculate] / [search]` | `true / false` | `false / true` | 300 ms | 30 |
| C | NOT_EVALUATED | missing | missing | missing | missing | missing |

Expected aggregate:

- Task completion rate: `1 / 2 = 50%`, one sample excluded.
- Correct tool selection rate: `1 / 2 = 50%`, one sample excluded.
- Invalid loop rate: `1 / 2 = 50%`, one sample excluded.
- Approval policy accuracy: `1 / 2 = 50%`, one sample excluded.
- P95 duration: nearest-rank value `300 ms` from two evidenced Runs.
- Average Token usage: `(10 + 30) / 2 = 20`, one sample excluded.

The calculator source applies these same denominator, exclusion, nearest-rank and averaging rules.

## Runtime Evidence Still Required

- Apply `sql/upgrade-agent-eval.sql` in the active schema and prepare at least one `READY` dataset version.
- Restart the backend so the new controller, service, entities and executor beans are loaded.
- Log in as an administrator, start one real Eval Run and confirm `PENDING -> RUNNING -> COMPLETED/FAILED` polling.
- Compare the persisted report with the fixed dataset labels and open at least one sample Trace.
- Capture authenticated desktop and mobile layouts; the current browser session only verified the new static asset manifest and login page with no console errors.

Task 2.10 remains open until these runtime checks are performed. No Maven compilation, automated tests, backend restart or paid model call was executed in this implementation pass.
