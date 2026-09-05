## ADDED Requirements

### Requirement: Dedicated model management workspace
The application SHALL present model management as a dedicated operational workspace rather than the generic resource form. The primary view SHALL prioritize provider, configured model, default status, enabled status, and actions needed for repeated administration.

#### Scenario: Administrator scans configured models
- **WHEN** the model page contains multiple configurations
- **THEN** the administrator can identify provider, model name, default model, enabled state, and row actions without scanning full Base URLs or secrets

#### Scenario: No models are configured
- **WHEN** the model list is empty
- **THEN** the page shows a direct add-model action and does not display meaningless zero-value metric cards

### Requirement: Grouped provider selection
The editor SHALL support provider search and grouped selection across priority, domestic, and aggregator providers. OpenAI/GPT and Custom SHALL be the first priority choices, and Ollama SHALL NOT be displayed.

#### Scenario: Search for GPT
- **WHEN** the administrator searches for `GPT` or `OpenAI`
- **THEN** both the OpenAI provider and custom GPT/OpenAI-compatible entry are discoverable with their distinct connection semantics

#### Scenario: Select a domestic provider
- **WHEN** the administrator selects DeepSeek, Qwen, Kimi, GLM, Doubao, Hunyuan, ERNIE, Spark, MiniMax, Baichuan, Yi, StepFun, or SiliconFlow
- **THEN** the editor displays server-provided defaults and recommended models for that provider

### Requirement: Safe provider switching
The editor SHALL distinguish catalog-generated defaults from administrator-edited values. Switching providers SHALL update untouched generated values and MUST NOT silently overwrite manually edited endpoint or model values.

#### Scenario: Switch after accepting defaults
- **WHEN** the endpoint and model still equal values automatically supplied for the previous provider
- **THEN** selecting a new provider replaces them with the new provider defaults

#### Scenario: Switch after manual endpoint edit
- **WHEN** the administrator manually changed the Base URL
- **THEN** selecting another provider preserves the manual URL and clearly marks it as custom until the administrator resets it

### Requirement: Model choice with graceful fallback
The editor SHALL allow choosing a recommended model, fetching available models, filtering fetched results, or entering an arbitrary model name. No discovery error SHALL remove the manual entry path.

#### Scenario: Discovery succeeds
- **WHEN** available models are fetched successfully
- **THEN** the administrator can search and select a returned model while retaining the ability to type another ID

#### Scenario: Discovery fails
- **WHEN** the provider does not support model listing or the request fails
- **THEN** an inline diagnostic is shown and the manual model field remains editable

### Requirement: Explicit connection feedback
The editor SHALL provide a connection-test command separate from Save and display stable idle, loading, success, warning, and error states without shifting the surrounding form layout.

#### Scenario: Successful connection test
- **WHEN** the probe returns success
- **THEN** the editor shows the tested provider/model and latency and enables saving without exposing model output

#### Scenario: Failed connection test
- **WHEN** the probe returns a classified failure
- **THEN** the editor shows a concise correction-oriented message for credentials, endpoint, model, rate limit, timeout, network, unsupported protocol, or unknown failure

#### Scenario: Configuration changes after testing
- **WHEN** provider, Base URL, API Key, or model name changes after a successful test
- **THEN** the previous success is invalidated and the editor returns to an untested state

### Requirement: Progressive configuration form
The editor SHALL keep provider, API Key, and model selection in the primary flow and place Base URL, temperature, maxTokens, default, and enabled controls in a clearly accessible advanced section. Required fields SHALL have visible labels and inline validation.

#### Scenario: Configure a catalog default
- **WHEN** an administrator selects a public catalog provider
- **THEN** the primary flow can be completed with provider, API Key, and model without requiring edits to advanced settings

#### Scenario: Configure a custom endpoint
- **WHEN** the custom provider is selected
- **THEN** Base URL moves into the required primary flow and optional-auth behavior is explained without in-app implementation commentary

### Requirement: Responsive and accessible interaction
The model workspace SHALL remain usable at 375px, 768px, 1024px, and 1440px widths, MUST NOT introduce page-level horizontal overflow, and SHALL provide visible focus, disabled, loading, and error states for interactive controls.

#### Scenario: Mobile model editing
- **WHEN** the editor is opened at 375px viewport width
- **THEN** provider choices, fields, connection status, and footer actions fit in a single-column flow without overlap or clipped text

#### Scenario: Keyboard operation
- **WHEN** an administrator navigates provider selection and form actions using the keyboard
- **THEN** every interactive control has an accessible name and visible focus state

### Requirement: Secret-safe editing
The UI MUST treat masked keys as unchanged secrets, MUST NOT display a recovered secret, and SHALL provide an explicit replace-key action or field behavior.

#### Scenario: Edit without changing key
- **WHEN** an existing model is opened and saved without entering a replacement key
- **THEN** the saved secret remains unchanged and no plaintext key appears in UI state returned by the server

#### Scenario: Replace existing key
- **WHEN** the administrator enters a new API Key
- **THEN** probe and save use the new value while subsequent reads show only the mask
