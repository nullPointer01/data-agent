## ADDED Requirements

### Requirement: Optional task contract
An Agent execution request SHALL accept an optional task goal and one or more success criteria while remaining compatible with requests that only contain a question.

#### Scenario: Legacy request omits task contract
- **WHEN** a client submits only the existing question and session fields
- **THEN** the Run executes with unchanged routing behavior and reports outcome status `NOT_EVALUATED`

#### Scenario: Request includes success criteria
- **WHEN** a client submits a goal and at least one valid success criterion
- **THEN** the system binds an immutable normalized task contract to the Run

### Requirement: Separate run and outcome status
The system SHALL represent execution lifecycle status separately from business outcome status.

#### Scenario: Run completes without outcome evaluation
- **WHEN** execution reaches `COMPLETED` but no success criteria were evaluated
- **THEN** the response reports run status `COMPLETED` and outcome status `NOT_EVALUATED`

#### Scenario: Criteria are not satisfied
- **WHEN** execution completes successfully but evaluated evidence does not satisfy all mandatory criteria
- **THEN** the response reports outcome status `NOT_ACHIEVED` without changing the Run lifecycle status to a technical failure

### Requirement: Evidence-backed outcome decision
Every `ACHIEVED` or `NOT_ACHIEVED` outcome SHALL include the evaluation method, criterion-level decisions, and references to safe Run evidence.

#### Scenario: Deterministic evaluator decides outcome
- **WHEN** configured deterministic assertions can evaluate the criteria
- **THEN** each criterion records pass/fail, evaluator version, and evidence references

#### Scenario: No reliable evaluator exists
- **WHEN** the system cannot reliably evaluate a criterion
- **THEN** it reports `NOT_EVALUATED` rather than inferring success from HTTP status or answer fluency
