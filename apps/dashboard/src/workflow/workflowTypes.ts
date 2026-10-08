export interface McpToolRef {
  name: string;
  description: string;
}

export interface SkillSpec {
  name: string;
  description: string;
  inputContract: string;
  outputContract: string;
  executionInstructions: string;
  mcpTools: McpToolRef[];
  policyNotes?: string;
}

export interface SubagentSpec {
  name: string;
  parentAgent: string;
  description: string;
  responsibilities: string;
  instructions: string;
  skillNames: string[];
  mcpTools: McpToolRef[];
  workflowId?: string;
}

export interface AgentSpec {
  name: string;
  description: string;
  role: string;
  instructions: string;
  subagentNames: string[];
  skillNames: string[];
  mcpTools: McpToolRef[];
  workflowId?: string;
  /** True for the built-in pipeline-stage agents (e.g. realisation) — read-only, not persisted. */
  builtIn?: boolean;
}

export interface TriggerConfig {
  dashboardButtonEnabled: boolean;
  hermesSignalEnabled: boolean;
  hermesSignalType?: string;
  fileDeliveryEnabled: boolean;
  fileDeliveryFolderPath?: string;
}

export interface ExecutionConfig {
  customerId?: string;
  repositoryUrl?: string;
  timeoutSeconds?: number;
}

export interface WorkflowGroup {
  id: string;
  name: string;
  description: string;
  /** Empty = global group: its workflows are available for every project. */
  projectName?: string;
}

export interface WorkflowExportBundle {
  groups: WorkflowGroup[];
  workflows: WorkflowDefinition[];
}

export interface WorkflowImportResult {
  groupsImported: number;
  workflowsImported: number;
  /** Human-readable references (e.g. orb targets) that could not be resolved during import. Informational only — the import itself still succeeded (AC-51: reported, not rejected). */
  violations?: string[];
}

export interface WorkflowStartInput {
  prompt?: string;
  specFile?: string;
  /** Git URL of the selected project; the run pushes its spec branch here. */
  repositoryUrl?: string;
  /** Id of the selected project; the backend adds its git credentials to the run. */
  projectId?: string;
}

export interface WorkflowDefinition {
  id: string;
  name: string;
  projectName: string;
  groupId?: string;
  description: string;
  agentIds: string[];
  subagentNames: string[];
  skillNames: string[];
  mcpTools: McpToolRef[];
  trigger?: TriggerConfig;
  execution?: ExecutionConfig;
  promptRequired?: boolean;
  /** Prepended to the user's prompt on start (e.g. branch/commit/push instructions). */
  promptInstructions?: string;
  /** Human approval gate configuration. Absent/undefined = no gate (byte-identical to pre-feature behavior). */
  approvalGate?: ApprovalGateConfig;
  /** Workflow orbs: workflow-referencing nodes in the pipeline (distinct from agent nodes). */
  workflowOrbs?: WorkflowOrb[];
  status: string;
  lastExecutionStatus?: string;
  lastExecutionAt?: string;
}

export interface WorkflowStartResponse {
  workflowId: string;
  executionId: string;
  status: string;
  startedAt: string;
  message: string;
}

export interface PromptRequest {
  prompt: string;
}

export interface HermesConfig {
  projectId: string;
  enabled: boolean;
  endpointUrl?: string;
  signalType?: string;
  workflowId?: string;
  hasAuthToken: boolean;
}

export interface HermesConfigRequest {
  enabled: boolean;
  endpointUrl?: string;
  authToken?: string | null;
  signalType?: string;
  workflowId?: string;
}

export interface JiraConfig {
  projectId: string;
  enabled: boolean;
  baseUrl?: string;
  projectKey?: string;
  issueTypeMapping?: string;
  workflowId?: string;
  hasAuthToken: boolean;
}

export interface JiraConfigRequest {
  enabled: boolean;
  baseUrl?: string;
  projectKey?: string;
  authToken?: string | null;
  issueTypeMapping?: string;
  workflowId?: string;
}

export interface DocumentFolderConfig {
  projectId: string;
  projectName: string;
  folderName: string;
  folderPath?: string;
  fileTriggerEnabled: boolean;
  allowedDocumentTypes?: string;
  workflowId?: string;
}

export interface DocumentFolderConfigRequest {
  folderPath?: string;
  fileTriggerEnabled: boolean;
  allowedDocumentTypes?: string;
  workflowId?: string;
}

export interface OpaConfig {
  enabled: boolean;
  baseUrl: string;
  policyPath: string;
  healthPath: string;
  timeoutSeconds: number;
  failMode: string;
  decisionLoggingEnabled: boolean;
  decisionLogExportEnabled: boolean;
  environment: string;
  customerLabel: string;
  projectLabel: string;
  hasAuthToken: boolean;
}

export interface OpaConfigRequest {
  enabled: boolean;
  baseUrl?: string;
  policyPath?: string;
  healthPath?: string;
  timeoutSeconds?: number;
  failMode?: string;
  decisionLoggingEnabled?: boolean;
  decisionLogExportEnabled?: boolean;
  environment?: string;
  customerLabel?: string;
  projectLabel?: string;
  authToken?: string | null;
}

export interface PolicyDecisionAuditEntry {
  id: string;
  workflowId?: string;
  workflowExecutionId?: string;
  projectId?: string;
  projectName?: string;
  customerId?: string;
  customerName?: string;
  agentId?: string;
  subagentId?: string;
  skillId?: string;
  mcpToolName?: string;
  triggerSource?: string;
  requestedAction?: string;
  opaDecisionId?: string;
  opaDecisionResult: string;
  policyReason?: string;
  riskLevel?: string;
  requiredApproval: boolean;
  auditTags: string[];
  timestamp: string;
  policyRevision?: string;
  policyUnavailable: boolean;
  failModeApplied?: string;
}

export interface DecisionLogFilter {
  from?: string;
  to?: string;
  agentId?: string;
  subagentId?: string;
  skillId?: string;
  mcpToolName?: string;
  result?: string;
}

// ── Skill catalog (local + marketplace, GET /api/skill-catalog) ──────────

export type SkillCatalogSourceKind = 'LOCAL' | 'MARKETPLACE';

export type SkillCatalogSourceOutcome =
  | 'SUCCESS'
  | 'AUTH_FAILED'
  | 'UNREACHABLE'
  | 'TIMEOUT'
  | 'INVALID_RESPONSE'
  | 'BLOCKED_BY_POLICY'
  | 'CONFIG_ERROR';

/** Read-only skill offered by a marketplace connection. No execution fields exist — never add them. */
export interface ExternalSkill {
  marketplaceId: string;
  marketplaceName: string;
  name: string;
  description: string;
}

export interface SkillCatalogSourceStatus {
  kind: SkillCatalogSourceKind;
  marketplaceId: string | null;
  name: string;
  outcome: SkillCatalogSourceOutcome;
  itemCount: number;
  httpStatus: number | null;
}

export interface SkillCatalog {
  localSkills: SkillSpec[];
  externalSkills: ExternalSkill[];
  sources: SkillCatalogSourceStatus[];
}

// ── Human approval gate (GET/POST /api/agent-runs/{runId}/approval-gate*) ───
// Wire contract frozen by the workflow-approval-gate architecture (§6.1/§6.2).
// Do not rename, reorder, or invent a field — a mismatch here is a backend
// integration defect to escalate, not something to silently work around.

export interface ApprovalGateConfig {
  enabled: boolean;
  placementStage: string;
}

// ── Workflow orb (workflow-referencing node in a pipeline) ─────────────────
// Mirrors WorkflowOrbDto / WorkflowOrbMode in shared/contracts/schemas/workflow.yaml.
// Do not rename, reorder, or invent a field — a mismatch here is a backend
// integration defect to escalate, not something to silently work around.

export type WorkflowOrbMode = 'SEQUENTIAL' | 'PARALLEL';

export interface WorkflowOrb {
  workflowId: string;
  mode: WorkflowOrbMode;
  /** Absent/blank means the orb runs first. */
  placementStage?: string;
}

export type ApprovalStageOutcome = 'PUBLISHED' | 'PUBLISH_FAILED' | 'NO_CHANGE' | 'NOT_APPLICABLE';

export type ApprovalDecisionKind = 'ACCEPT' | 'DENY' | 'ACCEPT_WITH_COMMENTS';

export interface ApprovalGateComment {
  iteration: number;
  author: string;
  comment: string;
  submittedAt: string;
}

export interface ApprovalGateContext {
  runId: string;
  workflowId: string;
  placementStage: string;
  stageOutcome: ApprovalStageOutcome;
  outcomeReason?: string;
  changeSummary: string;
  changedPaths: string[];
  changedFileCount: number;
  omittedFileCount: number;
  branchName?: string;
  pullRequestUrl?: string;
  branchCompareUrl?: string;
  iteration: number;
  maxFeedbackIterations: number;
  /**
   * The ONLY switch governing the feedback/re-run capability. Computed server-side.
   * Never re-derive this from `placementStage` — see architecture risk 8 / AC-58/AC-59.
   */
  feedbackSupported: boolean;
  furtherFeedbackAllowed: boolean;
  commentHistory: ApprovalGateComment[];
  openedAt: string;
}

export interface ApprovalDecisionRequest {
  decision: ApprovalDecisionKind;
  /** Current gate iteration this decision is for — the server's optimistic-concurrency check. */
  iteration: number;
  comment?: string;
}

export interface ApprovalDecisionResult {
  runId: string;
  status: string;
  iteration: number;
  message: string;
}

export interface ApprovalDecisionAuditEntry {
  id: string;
  runId: string;
  workflowId: string;
  gateStage: string;
  iteration: number;
  decision: ApprovalDecisionKind;
  comment?: string;
  actorUsername: string;
  actorSubject: string;
  stageOutcome: ApprovalStageOutcome;
  branchName?: string;
  pullRequestUrl?: string;
  timestamp: string;
}

export interface WorkflowExecutionSummary {
  runId: string;
  workflowId: string;
  status: string;
  startedAt: string;
  completedAt: string | null;
  durationMillis: number | null;
  startedBy: string | null;
}

export interface WorkflowExecutionPage {
  items: WorkflowExecutionSummary[];
  limit: number;
  offset: number;
  total: number;
  hasMore: boolean;
}
