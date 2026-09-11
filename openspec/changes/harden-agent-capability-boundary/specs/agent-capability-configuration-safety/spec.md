## ADDED Requirements

### Requirement: Capability configuration states are explicit
The system MUST distinguish unconfigured, explicitly empty, explicitly configured, and invalid Agent capability persistence states without using one empty collection to represent multiple states.

#### Scenario: Legacy null preserves compatibility
- **WHEN** an Agent Profile has `capability_bindings=NULL` and legacy `tools=NULL`
- **THEN** the system SHALL classify it as legacy unconfigured and resolve only the currently registered and policy-enabled Tool set before applying RBAC and runtime restrictions

#### Scenario: Legacy empty array means zero tools
- **WHEN** an Agent Profile has `capability_bindings=NULL` and legacy `tools=[]`
- **THEN** the system SHALL classify it as legacy explicitly empty and request zero Tools

#### Scenario: Valid legacy list remains restricted
- **WHEN** an Agent Profile has `capability_bindings=NULL` and legacy `tools` contains a valid non-empty JSON string array
- **THEN** the system SHALL request exactly the normalized Tool names in that array before applying narrower runtime restrictions

#### Scenario: Explicit empty unified bindings mean zero capabilities
- **WHEN** an Agent Profile has `capability_bindings=[]`
- **THEN** the system SHALL request zero Tools, Skills, and sub Agents and SHALL NOT read legacy capability fields as a fallback

#### Scenario: Valid unified bindings are authoritative
- **WHEN** an Agent Profile has a valid non-empty `capability_bindings` JSON array
- **THEN** the system SHALL use exactly those normalized stable identities as the requested capability set and SHALL NOT merge legacy fields

### Requirement: Invalid capability data fails closed
The system MUST reject malformed or semantically invalid persisted capability configuration and MUST NOT translate it to a default, empty, or expanded capability set.

#### Scenario: Malformed legacy Tool JSON is rejected
- **WHEN** `capability_bindings=NULL` and legacy `tools` is blank, malformed JSON, a non-array value, or contains invalid, blank, or duplicate Tool names
- **THEN** the system SHALL raise a capability configuration error before constructing the runtime Tool allowlist and SHALL execute no Tool because of that configuration

#### Scenario: Malformed unified binding JSON is rejected
- **WHEN** `capability_bindings` is non-null but blank, malformed JSON, a non-array value, or contains invalid, blank, duplicate, or unsupported capability identities
- **THEN** the system SHALL raise a capability configuration error and SHALL NOT fall back to legacy `tools` or `skill_id`

#### Scenario: Safe diagnostic response
- **WHEN** an API request reads or updates a Profile whose persisted capability configuration is invalid
- **THEN** the system SHALL return a stable capability-configuration error code with the affected field and Agent identifier and SHALL NOT include the raw persisted JSON

### Requirement: JSON conversion has one ownership boundary
The system SHALL perform Agent capability JSON encoding, decoding, normalization, and state classification through one reusable capability configuration Codec; the JPA entity MUST expose only raw persisted fields and non-parsing field-state checks.

#### Scenario: All consumers share the Codec
- **WHEN** Profile save, response mapping, capability catalog construction, or runtime binding resolution needs capability values
- **THEN** each path SHALL use the same Codec contract and SHALL NOT instantiate an ObjectMapper in `AgentProfile` or catch a decode failure as an empty list

#### Scenario: Encoding explicit empty values
- **WHEN** a new client saves an explicit empty unified binding list
- **THEN** the Codec SHALL persist `[]` in `capability_bindings` and SHALL preserve it as an explicit zero-capability contract

#### Scenario: Legacy null remains representable
- **WHEN** a legacy-compatible save intentionally retains the old contract
- **THEN** the system SHALL persist `capability_bindings=NULL` and SHALL NOT silently convert it to `[]`

### Requirement: Legacy migration is auditable and operator controlled
The system SHALL provide a read-only audit of existing capability configuration states and an explicit migration procedure, and MUST NOT mutate Agent capability configuration during application startup.

#### Scenario: Audit identifies every risk state
- **WHEN** an operator runs the capability configuration audit
- **THEN** it SHALL separately report unified invalid values, legacy invalid values, legacy null values, legacy empty arrays, and records already using valid unified bindings without changing data

#### Scenario: Unambiguous legacy list migration
- **WHEN** an operator explicitly migrates a Profile with a valid non-empty legacy Tool list
- **THEN** the migration SHALL write equivalent `tool:<name>` stable identities, preserve a valid legacy Skill binding where applicable, and leave legacy fields available as compatibility projections

#### Scenario: Dynamic default is not silently materialized
- **WHEN** a legacy Profile uses `tools=NULL`
- **THEN** automated migration SHALL leave it unchanged until an operator confirms the intended concrete Tool snapshot

#### Scenario: Startup does not migrate data
- **WHEN** the application starts after this change
- **THEN** no initializer, runner, or schema update hook SHALL rewrite `tools` or `capability_bindings`
