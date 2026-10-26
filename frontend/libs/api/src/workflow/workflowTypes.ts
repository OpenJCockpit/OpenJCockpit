export type {
  McpToolRefDto as McpToolRef,
  SkillSpecDto as SkillSpec,
  SubagentSpecDto as SubagentSpec,
  AgentSpecDto as AgentSpec,
  AgentCatalogLocalAgentDto as LocalCatalogAgent,
  AgentCatalogProviderAgentDto as ProviderCatalogAgent,
  A2AServiceDto as A2AService,
  A2AServiceRequest,
  AgentCatalogDto as AgentCatalog,
  TriggerConfigDto as TriggerConfig,
  ExecutionConfigDto as ExecutionConfig,
  WorkflowGroupDto as WorkflowGroup,
  WorkflowExportBundleDto as WorkflowExportBundle,
  WorkflowImportResultDto as WorkflowImportResult,
  WorkflowStartInputDto as WorkflowStartInput,
  WorkflowDefinitionDto as WorkflowDefinition,
  WorkflowStartResponseDto as WorkflowStartResponse,
  PromptRequestDto as PromptRequest,
  ProjectHermesConfigDto as HermesConfig,
  ProjectHermesConfigRequest as HermesConfigRequest,
  ProjectJiraConfigDto as JiraConfig,
  ProjectJiraConfigRequest as JiraConfigRequest,
  ProjectDocumentFolderConfigDto as DocumentFolderConfig,
  ProjectDocumentFolderConfigRequest as DocumentFolderConfigRequest,
  OpaConfigDto as OpaConfig,
  OpaConfigRequestDto as OpaConfigRequest,
  PolicyDecisionAuditEntryDto as PolicyDecisionAuditEntry,
  ExternalSkillDto as ExternalSkill,
  SkillCatalogSourceStatusDto as SkillCatalogSourceStatus,
  SkillCatalogDto as SkillCatalog,
  ApprovalGateConfigDto as ApprovalGateConfig,
  WorkflowOrbMode,
  WorkflowOrbDto as WorkflowOrb,
  ApprovalStageOutcome,
  ApprovalDecisionKind,
  ApprovalGateCommentDto as ApprovalGateComment,
  ApprovalGateContextDto as ApprovalGateContext,
  ApprovalDecisionRequestDto as ApprovalDecisionRequest,
  ApprovalDecisionResultDto as ApprovalDecisionResult,
  ApprovalDecisionAuditEntryDto as ApprovalDecisionAuditEntry,
  WorkflowExecutionSummaryDto as WorkflowExecutionSummary,
  WorkflowExecutionPageDto as WorkflowExecutionPage,
} from '../generated/contract';
import type {
  AgentCatalogLocalSectionDto,
  SkillCatalogSourceStatusDto as SkillCatalogSourceStatus,
} from '../generated/contract';

export type AgentCatalogStatus = AgentCatalogLocalSectionDto['status'];

/** A2A agent card as hosted by the provider (A2A 0.3 fields used by the dashboard). */
export interface A2AAgentCard {
  name: string;
  description?: string;
  url?: string;
  version?: string;
  protocolVersion?: string;
  preferredTransport?: string;
  capabilities?: { streaming?: boolean; pushNotifications?: boolean };
  defaultInputModes?: string[];
  defaultOutputModes?: string[];
  skills?: {
    id: string;
    name?: string;
    description?: string;
    tags?: string[];
  }[];
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

export type SkillCatalogSourceKind = SkillCatalogSourceStatus['kind'];

export type SkillCatalogSourceOutcome = SkillCatalogSourceStatus['outcome'];

// ── Human approval gate (GET/POST /api/agent-runs/{runId}/approval-gate*) ───
// Wire contract frozen by the workflow-approval-gate architecture (§6.1/§6.2).
// Do not rename, reorder, or invent a field — a mismatch here is a backend
// integration defect to escalate, not something to silently work around.

// ── Workflow orb (workflow-referencing node in a pipeline) ─────────────────
// Mirrors WorkflowOrbDto / WorkflowOrbMode in shared/contracts/schemas/workflow.yaml.
// Do not rename, reorder, or invent a field — a mismatch here is a backend
// integration defect to escalate, not something to silently work around.
