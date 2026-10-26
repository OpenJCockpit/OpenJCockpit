export type {
  GitStatus,
  ProjectDto as Project,
  ProjectRequest,
  ProjectGitCredentialRequest,
  ProjectGitCredentialDto,
  ProjectGitStatusDto,
  AgentEventDto as AgentEvent,
  AgentRunDto as AgentRun,
  CustomerSummary,
  SpecFile,
  SpecInitResult,
  SpecInitStatus,
  PullRequestSummary,
  EvidenceEvent,
  EvidenceDetails,
  QualityControl,
  StackServiceStatus,
  SkillsMarketplaceConnectionDto as SkillsMarketplaceConnection,
  SkillsMarketplaceConnectionRequest,
} from '../generated/contract';
import type {
  AgentCard as AgentCardDto,
  PreflightError as PreflightErrorDto,
  Workspace as WorkspaceDto,
  WorkflowPreflightResult,
} from '../generated/contract';

/** `UNKNOWN_ERROR` is synthesised client-side when the pre-flight call itself fails. */
export type PreflightError = Omit<PreflightErrorDto, 'code'> & {
  code: PreflightErrorDto['code'] | 'UNKNOWN_ERROR';
};

export type PreflightResult = Omit<WorkflowPreflightResult, 'errors'> & {
  errors: PreflightError[];
};

export interface AgentDefinition {
  id: string;
  name: string;
  description: string;
  role: string;
  sequenceOrder: number;
  inputType: string;
  outputType: string;
}

export type StatusKind = AgentCardDto['statusKind'];

/** An agent as the sequencer shows it: the contract's `AgentCard` plus a UI-only discriminator. */
export type AgentCard = AgentCardDto & { kind: 'agent' };

export interface OrbCard {
  kind: 'orb';
  id: string;
  referencedWorkflowId: string;
  referencedWorkflowName: string;
  mode: 'SEQUENTIAL' | 'PARALLEL';
  status: string;
  statusKind: StatusKind;
  childRunId?: string;
}

export type SequencerNode = AgentCard | OrbCard;

/** `agents` carries the sequencer's `kind` discriminator, which the contract does not define. */
export type Workspace = Omit<WorkspaceDto, 'agents'> & { agents: AgentCard[] };
