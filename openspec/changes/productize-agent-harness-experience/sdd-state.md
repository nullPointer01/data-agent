# SDD State

- Change: `productize-agent-harness-experience`
- Project: `data-agent`
- Mode: Full SDD
- Java baseline: 17
- Framework: Spring Boot 3.2 + React/Vite + LangChain4j 1.19
- Phase: Verification - authenticated runtime acceptance pending
- Scope: Harness-first product shell, request-level intent/resource planning, Run evidence, task outcome contract, Agent Eval, context governance, extension contract and interview demo
- Anti-scope: Dify-style builder, generic consumer assistant, strong sandbox claims, full event sourcing, multi-level approval and destructive rewrites
- Test strategy: No automated tests or backend compilation per prior explicit user instruction; frontend build, static checks and approved live browser/API verification
- Problem tracking: `problem-log.md`
- Status: 38/41 tasks complete; Stage 5 composable Tool/Skill/sub-Agent bindings, four-mode routing, governed Skill/delegation adapters, request-level resource planning and personal settings are statically accepted. Real request-plan behavior and parent-child execution remain explicitly runtime-unverified. Stage 1/2/3 authenticated runtime acceptance is the remaining work.
