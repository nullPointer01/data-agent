## ADDED Requirements

### Requirement: Central context admission
Every governed model call SHALL pass through a context admission boundary that considers model window size, Run Token budget, message priority, and required tool protocol continuity.

#### Scenario: Context fits within limits
- **WHEN** the selected context fits both model and Run budgets
- **THEN** the system preserves the context and records that no compaction was required

#### Scenario: Context exceeds limits
- **WHEN** the selected context exceeds a configured limit
- **THEN** the system applies the configured reduction policy before invoking the model

### Requirement: Protected context elements
The context governor SHALL preserve system instructions, the current task contract, unresolved tool calls and results, active approval state, and required recent evidence unless it safely rejects the call.

#### Scenario: Reduction would remove protected content
- **WHEN** no valid reduced context can retain all protected elements
- **THEN** the system fails with a stable context-budget reason instead of silently dropping the protected content

### Requirement: Context governance evidence
The system SHALL record safe context metrics including estimated Tokens before and after reduction, retained message count, strategy, reason, and whether estimation was used.

#### Scenario: Compaction succeeds
- **WHEN** context is summarized or trimmed
- **THEN** Run evidence reports the reduction metrics without persisting raw private message content in metrics or logs

### Requirement: Controlled rollout
Context compaction SHALL be independently configurable and default to conservative behavior until evaluated.

#### Scenario: Compaction feature is disabled
- **WHEN** a context exceeds the model limit while compaction is disabled
- **THEN** the system returns an explicit context-limit result and does not silently enable an unverified summarizer
