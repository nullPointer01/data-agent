# Static Verification Report

## Result

- Decision: `PASS_WITH_NOTES`
- Scope: `harden-agent-access-boundaries`
- Completed: 7/7 tasks
- Verification mode: static inspection only
- Runtime tests, Maven compilation, frontend build and Docker build: not run by explicit project constraint

## Endpoint Matrix

| Endpoint | Required boundary | Static result |
| --- | --- | --- |
| `/api/v1/resources/**` | ADMIN | Listed in `ADMIN_ENDPOINTS` before `/api/v1/**` |
| `/api/v1/token/skill-usage` | ADMIN | Explicit ADMIN matcher |
| `/api/v1/token/model-usage` | ADMIN | Explicit ADMIN matcher |
| `/api/v1/token/tenant-summary` | ADMIN | Explicit ADMIN matcher |
| `/api/v1/token/usage-records` | ADMIN | Explicit ADMIN matcher |
| `/api/v1/token/reset` | ADMIN | Explicit ADMIN matcher |
| `/api/v1/rag/settings` | ADMIN | Explicit ADMIN matcher |
| `/api/v1/rag/health` | ADMIN | Explicit ADMIN matcher |
| `/api/v1/rag/evaluate` | ADMIN | Explicit ADMIN matcher |
| `/api/v1/rag/benchmark/**` | ADMIN | Explicit ADMIN matcher and existing method annotation |
| `POST /api/v1/rag/retrieve` | Authenticated user | Not in ADMIN matcher; covered by `/api/v1/**` |
| `GET /api/v1/my/models` | Authenticated user | Not in ADMIN matcher; covered by `/api/v1/**` |
| `/api/v1/token/user-summary` | Authenticated user | Not in ADMIN matcher; covered by `/api/v1/**` |
| `/api/v1/token/remaining-quota` | Authenticated user | Not in ADMIN matcher; covered by `/api/v1/**` |

## Static Checks

- Authentication chain: valid JWT -> subject -> `SysUserRepository.findById` -> enabled check -> current persisted tenant and enabled roles -> `TenantUser`.
- JWT claim scan: `JwtAuthenticationFilter` does not read roles, tenantId or username claims.
- Refresh chain: persisted user existence and enabled state are checked before a new access token is created.
- Personal model DTO: exactly five model metadata fields; no API Key, Base URL, temperature, token limit, tenant or creator fields.
- Personal model query: current tenant enabled models are followed by enabled shared `default` tenant models.
- Registration chain: ordinary registration reads server configuration and does not read `RegisterRequest.tenantId`; bootstrap admin remains independently controlled.
- Knowledge sync chain: all four HTTP operations reach `findByKnowledgeIdAndTenantIdAndCreatedBy` before reading or mutating synchronization state.
- Frontend chain: `ChatPage` uses `/api/v1/my/models` and reads `displayName`; it no longer calls the administrator model list.
- Compatibility: no endpoint or database schema was removed or changed.
- Whitespace validation: `git diff --check` passed for implementation files at completion.

## Notes And Residual Risk

- HTTP 401/403 behavior and disabled-account behavior require manual runtime verification after application restart.
- The current implementation performs one user lookup per authenticated request. No authentication cache is introduced in this stage.
- `ADMIN` remains a platform-wide governance role; platform-admin versus tenant-admin isolation remains deferred.
- Self-registration remains visible in the static frontend even when a non-local environment disables it; the backend rejects it. A public capabilities endpoint can improve this UX later.
