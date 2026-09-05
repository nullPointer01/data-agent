# SDD State

- Change: `build-unified-agent-run-harness`
- Project: `data-agent`
- Mode: Full SDD
- Java baseline: 17
- Framework: Spring Boot 3.2 + React/Vite + LangChain4j 1.19
- Phase: Coding Complete
- Status: 18/18 tasks complete; static acceptance passed with notes, awaiting user confirmation before Verify
- Scope: Unified Agent Run lifecycle, deterministic routing, five budgets, deadline/cancellation, typed events, SSE compatibility and unified trace
- Anti-scope: New Agent mode, tool approval/governance, persistent run/checkpoint, cross-node cancellation, database migration
- Test strategy: No automated tests or Maven compile per explicit user instruction
- Verification strategy: Static acceptance recorded in `verify-report.md`; Maven, tests and frontend build intentionally not run
- Archive status: Not archived
