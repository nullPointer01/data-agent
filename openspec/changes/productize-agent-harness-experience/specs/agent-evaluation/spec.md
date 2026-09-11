## ADDED Requirements

### Requirement: Versioned Agent evaluation dataset
The system SHALL run Agent evaluations from a versioned dataset containing task input, expected behavior, evaluation rules, and optional required tools or approval decisions.

#### Scenario: Evaluation begins
- **WHEN** an authorized user starts an evaluation
- **THEN** the run records dataset version, Agent Profile version, model identifier, Harness configuration identity, and start time

### Requirement: Sample-level evidence
Every aggregate evaluation result SHALL be traceable to sample-level inputs, outcomes, reason codes, and safe Run references.

#### Scenario: Aggregate metric is inspected
- **WHEN** a user opens a reported metric
- **THEN** the system can list the contributing samples and distinguish passed, failed, and not-evaluated cases

### Requirement: Required Harness quality metrics
An evaluation report SHALL calculate task completion rate, correct tool selection rate, invalid loop rate, approval policy accuracy, P95 duration, and average Token usage when the dataset provides the required labels.

#### Scenario: Dataset lacks a required label
- **WHEN** a metric cannot be calculated from the dataset and Run evidence
- **THEN** the report marks the metric unavailable and states the missing evidence instead of returning zero

### Requirement: RAG is a quality dimension
The evaluation system SHALL preserve RAG retrieval metrics as a separate dimension and SHALL NOT equate retrieval success with end-to-end task success.

#### Scenario: Knowledge task retrieves correct evidence but fails the task
- **WHEN** RAG recall passes but the Agent output violates a success criterion
- **THEN** the report records retrieval success and task failure independently
