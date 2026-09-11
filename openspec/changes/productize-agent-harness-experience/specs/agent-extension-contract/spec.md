## ADDED Requirements

### Requirement: Unified capability descriptor
Tools, Skills, and sub Agents SHALL expose a capability descriptor containing stable identifier, type, version, input contract, output contract, owner, permissions, risk metadata, lifecycle state, and availability.

#### Scenario: Capability is listed
- **WHEN** an authorized user opens the capability directory
- **THEN** the system returns only descriptors visible to that user's tenant and permissions

### Requirement: Registration validation
The system SHALL reject a capability registration that lacks required contracts, conflicts with an existing identity/version, or violates governance policy.

#### Scenario: Tool omits risk or permission metadata
- **WHEN** a Tool provider attempts registration without mandatory governance metadata
- **THEN** registration fails before the Tool can enter any Agent allowlist

### Requirement: Agent Profile binding
An Agent Profile SHALL bind capabilities by stable identity and SHALL resolve the effective set as the intersection of profile selection, tenant availability, user authorization, and runtime policy.

#### Scenario: Bound capability is later disabled
- **WHEN** a capability bound to an Agent Profile becomes disabled or unauthorized
- **THEN** subsequent Runs exclude it and expose a safe capability-unavailable reason

#### Scenario: New Profile explicitly binds no capabilities
- **WHEN** a new client saves an empty capability identity list
- **THEN** the Profile preserves an explicit empty binding and the Runtime does not reinterpret it as all Tools

#### Scenario: Legacy Profile has no unified binding field
- **WHEN** a Profile created by an older client has a null unified binding field
- **THEN** the Runtime derives its effective Tools and optional Skill from legacy fields without changing stored data

### Requirement: Bound Skill execution
An Agent in ReAct or Orchestrated mode SHALL expose only its authorized bound Skills through a governed adapter.

#### Scenario: Model requests an unbound Skill
- **WHEN** the model supplies a Skill identity that is not in the current Agent binding snapshot
- **THEN** execution is rejected before the Skill implementation is invoked and a safe denial is recorded

### Requirement: Bound sub Agent delegation
An Agent in Orchestrated mode SHALL delegate only to authorized bound sub Agents while sharing the root Run controls.

#### Scenario: Child Agent executes work
- **WHEN** the model delegates to a bound enabled child Agent
- **THEN** the child uses its own Profile and capability snapshot while consuming the root Run deadline, model-call, tool-call, iteration and Token budgets

#### Scenario: Delegation contains a cycle or exceeds depth
- **WHEN** a child Agent repeats an Agent already active in the delegation path or enters a fourth level
- **THEN** the Harness rejects delegation before another model call and records a safe reason

### Requirement: Explicit execution mode boundaries
Configured Agents SHALL apply one consistent capability policy in synchronous and streaming paths.

#### Scenario: Agent runs in Chat mode
- **WHEN** a Profile resolves to Chat
- **THEN** no Tool, Skill or sub Agent specification is exposed to the model

#### Scenario: Agent runs in ReAct mode
- **WHEN** a Profile resolves to ReAct
- **THEN** only bound Tools and Skills are exposed and sub Agent delegation is excluded

#### Scenario: Agent runs in Orchestrated mode
- **WHEN** a Profile resolves to Orchestrated
- **THEN** bound Tools, Skills and sub Agents may be selected under the same Run governance

### Requirement: Provider-neutral extension boundary
The Harness SHALL treat MCP as one possible external Tool provider and SHALL preserve the same admission, permission, timeout, result sanitization, and observability pipeline for all providers.

#### Scenario: MCP tool is invoked
- **WHEN** a future MCP-backed Tool is selected by a Run
- **THEN** it cannot bypass the existing Tool governance pipeline or receive broader credentials than its descriptor permits
