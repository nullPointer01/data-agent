# Assumptions

- A001: `capability_bindings=NULL` is the only version marker for the legacy contract. Source: current code and `productize-agent-harness-experience` design. Confidence: high.
- A002: Historical application saves of an empty legacy Tool list wrote SQL `NULL`, so a persisted JSON `[]` can be safely treated as explicit zero Tool. Source: current `AgentProfile.setToolList`. Confidence: medium; verify with read-only SQL audit before production deployment.
- A003: MySQL is the authoritative Profile store and supports JSON validation functions used by the audit script. Source: current runtime dependency and SQL directory. Confidence: high.
- A004: Existing legal unified bindings use stable `tool:`, `skill:`, and `agent:` identities. Source: `AgentCapabilityBindingSet` and current Harness documentation. Confidence: high.
- A005: The earlier user constraint prohibiting tests and compilation remains active until explicitly revoked. Source: user instruction and existing OpenSpec task overrides. Confidence: high.
