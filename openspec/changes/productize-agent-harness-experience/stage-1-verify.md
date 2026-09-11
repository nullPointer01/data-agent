# Stage 1 Verification

Date: 2026-09-06

## Result

`IMPLEMENTED_WITH_RUNTIME_VERIFICATION_PENDING`

Stage 1 application code is implemented. The current browser-control session only reaches the login page and the running backend has not been restarted with the new Java classes, so authenticated owner/other-user API checks, desktop/mobile workbench screenshots and a real SSE run remain unverified. No result below treats static evidence as a substitute for those checks.

## Success Criteria

| Criterion | Status | Evidence | Pending |
|---|---|---|---|
| SC-1 Product identity | PARTIAL | User navigation has 3 entries; admin navigation is grouped into Runtime & Quality, Capabilities and Governance; My Agent contains Agent identity, task composer, latest Run state and Harness signals. Frontend build passed. | Authenticated first-viewport desktop/mobile check. |
| SC-2 Run evidence | PARTIAL | Live terminal usage is normalized; owner endpoint merges durable state with same-ID Trace; messages persist nullable `run_id`; history hydrates the Run Inspector. DTO omits checkpoint, ciphertext, raw arguments and hidden reasoning. | Restart backend, run one task, refresh, then reopen the same Run as owner. |
| SC-3 Event closure | PARTIAL | Live normalization covers start, retrieval, planning, tool, approval, budget, error/pause/end. Persisted projection exposes only provable start/tool/resume/terminal events and explicitly returns `eventHistoryComplete=false`. | Capture one real SSE sequence and one approval/resume sequence. |
| SC-8 Problem closure | PASS | P001-P013 record causes, resolutions, evidence and residual risk. P004/P008 remain planned work; P005/P009 are explicitly monitored rather than hidden. | Continue updating the log in later stages. |

## Static And Build Evidence

- `npm run build`: PASS, Vite 7.3.3, 1732 modules transformed.
- Persisted evidence deterministic sample: PASS for `BUDGET_EXHAUSTED`, Token usage, partial history and terminal state.
- `git diff --check`: PASS.
- `openspec validate productize-agent-harness-experience --strict`: PASS.
- Running static manifest: PASS after syncing generated assets; `index-DzZQmy1F.js` returns HTTP 200.
- Browser DOM: PASS for the current unauthenticated login page; protected workspace is not claimed as verified.

## Required Runtime Checklist

- Log in as a normal user and confirm the first viewport has no horizontal overflow or overlap at desktop and mobile widths.
- Complete one Chat or ReAct request and verify status, mode, duration, model calls, tool calls, Tokens and termination reason share one `runId/traceId`.
- Refresh the session and reopen Run Inspector; verify persisted evidence loads and is labeled partial when appropriate.
- Request the same Run with a second user and confirm the response is identical to a missing Run response.
- Log in as ADMIN and navigate each Control Plane group, beginning at Runtime Traces.
