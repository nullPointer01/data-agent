schemaVersion: 1
change: standard-mcp-tools
phase: { name: sdd-coding, status: COMPLETED }
outcome: PASS_WITH_NOTES
nextPhase: null
returnPhase: null
sourceEvidence:
  baseRevision: 10cdefabaa802ed80c2b6051ac044e06f2dfb169
  preexistingWorktreeFilesEvidence: baseline.txt
  taskLedger:
    - task: T1
      files: [pom.xml, src/main/java/com/ai/integration/mcp/McpIntegrationProperties.java, src/main/java/com/ai/integration/mcp/McpToolCatalog.java, src/main/java/com/ai/integration/mcp/McpServerController.java, src/main/resources/application.yml]
      evidence: "新增 MCP 包；pom 仅 MCP/Schema 两依赖和版本；配置仅 app.mcp 空目录。保留其他既有差异。"
    - task: T2
      files: [src/main/java/com/ai/agent/tool/governance/AgentToolRegistry.java, src/main/java/com/ai/agent/tool/AgentToolInvoker.java, src/main/java/com/ai/agent/tool/governance/AgentToolArgumentValidator.java, src/main/java/com/ai/agent/tool/governance/AgentToolAdmissionService.java, src/main/java/com/ai/agent/tool/governance/AgentToolPolicyEngine.java, src/main/java/com/ai/agent/capability/AgentCapabilityRegistry.java, src/main/java/com/ai/agent/capability/AgentCapabilityService.java, frontend/src/components/CapabilityPackPicker.jsx, src/main/resources/static/index.html, src/main/resources/static/assets]
      evidence: "仅目录合并、精确查找、租户准入、Schema 库替换、MCP 风险和默认绑定排除、外部工具 UI 开关及构建产物；既有 capability/invoker 差异不全量归属。"
    - task: T3
      files: [openspec/changes/standard-mcp-tools/verification.md]
      evidence: "本地真实 HTTP 协议、配置/绑定/组件检查和既有框架回归；未用真实业务库/付费模型。"
    - task: T4
      files: [README.md, CLAUDE.md, src/main/java/com/ai/package-info.java, docs/核心逻辑详解/标准MCP工具接入.md, docs/核心逻辑详解/目录索引.md, docs/核心逻辑详解/框架集成与消息生命周期.md, docs/核心逻辑详解/Agent工具治理.md, docs/interview/当前进度与验收交接.md]
      evidence: "新增学习文档和关联入口/交接；解释官方职责、配置操作与未验边界，保留已有内容。"
taskProgress: { completed: [T1, T2, T3, T4], current: null }
approvals:
  - phase: conversation-implementation
    artifactRevision: 用户明确选择继续技术改造，先接标准MCP
    approvedBy: user
    approvedAt: 2026-09-30
pendingChanges: []
testLevel: middle
