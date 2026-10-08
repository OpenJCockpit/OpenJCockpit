export type StatusKind = 'active' | 'done' | 'waiting' | 'open' | 'review' | 'changes' | 'ok';

export type GitStatus = 'UNKNOWN' | 'ACCESSIBLE' | 'NOT_ACCESSIBLE' | 'CHECK_FAILED';

export interface Project {
  id: string;
  name: string;
  customerId?: string;
  gitUrl?: string;
  description?: string;
  defaultBranch?: string;
  environment?: string;
  owner?: string;
  active: number;
  newProject: number;
  gitStatus: GitStatus;
  gitStatusCheckedAt?: string;
  gitStatusMessage?: string;
  hasCredentials?: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectRequest {
  name: string;
  customerId?: string;
  gitUrl?: string;
  description?: string;
  defaultBranch?: string;
  environment?: string;
  owner?: string;
  newProject?: number;
}

export interface ProjectGitCredentialRequest {
  credentialType: 'NONE' | 'HTTPS_TOKEN' | 'USERNAME_PASSWORD' | 'GITHUB_PAT' | 'GITHUB_APP';
  username?: string;
  secret?: string | null;
  githubApiUrl?: string;
}

export interface ProjectGitCredentialDto {
  credentialType: 'NONE' | 'HTTPS_TOKEN' | 'USERNAME_PASSWORD' | 'GITHUB_PAT' | 'GITHUB_APP';
  username?: string;
  githubApiUrl?: string;
  hasSecret: boolean;
}

export interface ProjectGitStatusDto {
  projectId: string;
  gitStatus: GitStatus;
  gitStatusCheckedAt?: string;
  gitStatusMessage?: string;
}

export interface PreflightError {
  code: string;
  message: string;
}

export interface PreflightResult {
  passed: boolean;
  errors: PreflightError[];
}

export interface AgentEvent {
  timestamp: string;
  agentId: string;
  title: string;
  status: string;
  evidenceRef: string;
}

export interface AgentRun {
  runId: string;
  customerId: string;
  specFile: string;
  repositoryUrl: string;
  status: string;
  startedAt: string;
  events: AgentEvent[];
  generatedArtifacts: string[];
  workflowId?: string | null;
  startedBy?: string | null;
  completedAt?: string | null;
  failureSummary?: string | null;
}

export interface AgentDefinition {
  id: string;
  name: string;
  description: string;
  role: string;
  sequenceOrder: number;
  inputType: string;
  outputType: string;
}

export interface CustomerSummary {
  id: string;
  name: string;
  sector: string;
  environment: string;
  lastActive: string;
  dataClassification: string;
  region: string;
}

export interface SpecFile {
  id: string;
  fileName: string;
  owner: string;
  lastChanged: string;
  status: string;
  selected?: boolean;
  content?: string;
  repositoryUrl?: string;
}

export interface SpecInitResult {
  projectId: string;
  branch: string;
  baseBranch: string;
  commitHash: string;
  fileName: string;
  pullRequestUrl?: string;
  message: string;
}

export interface SpecInitStatus {
  pending: boolean;
  branch?: string;
  branchUrl?: string;
  pullRequestUrl?: string;
  /** True when the main branch already contains a .specify folder. */
  templateExists?: boolean;
}

export interface AgentCard {
  kind: 'agent';
  id: string;
  name: string;
  description: string;
  status: string;
  statusKind: StatusKind;
}

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

export interface PullRequestSummary {
  id: string;
  title: string;
  branch: string;
  createdAt: string;
  ownerLabel: string;
  ownerName: string;
  status: string;
  statusKind: StatusKind;
}

export interface EvidenceEvent {
  time: string;
  title: string;
  actor: string;
  status: string;
}

export interface EvidenceDetails {
  specVersion: string;
  runId: string;
  agentFlow: string;
  lastUpdate: string;
}

export interface QualityControl {
  name: string;
  status: string;
  statusText: string;
}

export interface StackServiceStatus {
  name: string;
  description: string;
  status: string;
}

export interface SkillsMarketplaceConnection {
  id: string;
  name: string;
  marketplaceUrl: string;
  description?: string;
  enabled: boolean;
  hasApiKey: boolean;
  createdBy?: string;
  createdAt: string;
  updatedAt: string;
}

export interface SkillsMarketplaceConnectionRequest {
  name: string;
  marketplaceUrl: string;
  apiKey?: string;
  description?: string;
  enabled: boolean;
}

export interface Workspace {
  customer: CustomerSummary;
  selectedSpecFile: string;
  specs: SpecFile[];
  agents: AgentCard[];
  pullRequests: PullRequestSummary[];
  evidenceEvents: EvidenceEvent[];
  evidenceDetails: EvidenceDetails;
  qualityControls: QualityControl[];
  stack: StackServiceStatus[];
  footer: {
    environment: string;
    region: string;
    dataClassification: string;
    embabelVersion: string;
    springAiControlVersion: string;
  };
}
