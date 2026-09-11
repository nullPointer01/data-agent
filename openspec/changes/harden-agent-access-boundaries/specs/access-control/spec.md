# Access Control Delta Specification

## Requirement: Backend-Enforced Control Plane

The system SHALL enforce administrator access at the HTTP security boundary for every control-plane endpoint, independently of frontend navigation visibility.

### Scenario: Ordinary user calls a control-plane endpoint

- **WHEN** an authenticated non-admin user calls tenant resource aggregation, tenant Token administration, or RAG operational endpoints
- **THEN** the request is rejected before the controller executes

### Scenario: Ordinary user retrieves personal RAG context

- **WHEN** an authenticated user calls the personal RAG retrieve endpoint
- **THEN** the request remains allowed and retrieval is filtered by the current tenant and user

## Requirement: Minimal Personal Model Catalog

The system SHALL expose a read-only catalog of enabled model options for personal Agent configuration without exposing model credentials or administrator connection settings.

### Scenario: User lists selectable models

- **WHEN** an authenticated user requests personal model options
- **THEN** only enabled current-tenant and shared-default options are returned
- **AND** every option contains only model identity and display metadata
- **AND** no API key or Base URL is returned

## Requirement: Current Persisted Account Authorization

The system SHALL use the current persisted account enabled state, tenant and active roles when authenticating protected HTTP requests.

### Scenario: Account is disabled after token issuance

- **WHEN** a user presents a cryptographically valid access or refresh token after the persisted account is disabled
- **THEN** the system does not authenticate the request or issue a new access token

### Scenario: Roles change after token issuance

- **WHEN** a user presents an unexpired access token after persisted role membership changes
- **THEN** authorization uses the current persisted roles rather than the role claim in the token

## Requirement: Server-Controlled Self Registration

The system SHALL control whether self registration is enabled and which tenant receives self-registered accounts.

### Scenario: Client supplies a tenant during registration

- **WHEN** self registration is enabled and a client sends any tenant identifier
- **THEN** the created user receives the server-configured registration tenant

### Scenario: Self registration is disabled

- **WHEN** a client calls the registration endpoint while self registration is disabled
- **THEN** the service rejects account creation without persisting a user

## Requirement: Personal Knowledge Sync Ownership

The system SHALL apply the same current-user ownership boundary to knowledge synchronization as to normal knowledge CRUD.

### Scenario: Same-tenant user targets another user's knowledge

- **WHEN** a user reads, changes, deletes, or triggers synchronization for knowledge created by another user in the same tenant
- **THEN** the operation is rejected without revealing whether that knowledge exists
