## ADDED Requirements

### Requirement: Harness-first product shell
The application SHALL present My Agent as a reference application powered by the Java Agent Harness and SHALL separate the user workspace from the administrative Control Plane.

#### Scenario: User enters the reference application
- **WHEN** an authenticated user opens the application
- **THEN** the first viewport shows the active Agent, task input, current or latest Run status entry, and a concise Harness capability summary

#### Scenario: Administrator enters the Control Plane
- **WHEN** an authenticated administrator switches to the management console
- **THEN** the navigation groups configuration, runtime observability, and governance without presenting all database entities as peer product concepts

### Requirement: Unified run summary
The application SHALL display a safe Run summary linked by the canonical `runId`, and SHALL use the same value as `traceId` where a Trace exists.

#### Scenario: Streaming run terminates
- **WHEN** the client receives a terminal SSE event
- **THEN** it displays status, execution mode, duration, model calls, tool calls, Token usage, and termination reason from that event

#### Scenario: User reloads a durable run
- **WHEN** the Run owner opens a persisted Run after a page refresh
- **THEN** the system returns the safe persisted summary without exposing checkpoint ciphertext, raw tool arguments, credentials, or another user's Run

### Requirement: Safe operational timeline
The application SHALL render an ordered operational timeline and SHALL NOT expose hidden model reasoning.

#### Scenario: Run uses retrieval and tools
- **WHEN** a Run emits retrieval, planning, tool, approval, budget, or terminal events
- **THEN** the timeline renders event time, type, status, title, and a sanitized summary in occurrence order

#### Scenario: Event contains sensitive or hidden content
- **WHEN** an event contains credentials, raw sensitive tool values, or hidden reasoning
- **THEN** the server omits or sanitizes that content before persistence or delivery

### Requirement: Progressive detail
The user experience SHALL keep the final result primary and place engineering evidence in progressively disclosed details.

#### Scenario: Run completes normally
- **WHEN** the Agent returns a final result
- **THEN** the result remains visually primary while evidence and diagnostics are available through a Run Inspector
