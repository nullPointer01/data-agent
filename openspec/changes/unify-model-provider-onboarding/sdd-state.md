# SDD State

- Change: `unify-model-provider-onboarding`
- Project: `data-agent`
- Mode: Full SDD
- Java baseline: 17
- Framework: Spring Boot 3.2 + React 19 + LangChain4j 1.19
- Phase: Verify
- Status: Implementation revision in progress; provider catalog and model editor updated
- Scope: Unified OpenAI-compatible provider onboarding, real Chat probe, dedicated model management UI, removal of Ollama entry
- Test strategy: No automated tests or Maven compile; frontend build is authorized by the current UI correction request
- Verification strategy: OpenSpec strict validation, static contract/security checks, diff integrity; browser and real-provider checks only after explicit authorization
- Verification report: `verify-report.md`
- Archive status: Not archived
