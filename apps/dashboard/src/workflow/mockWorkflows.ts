import {
  AgentSpec,
  DocumentFolderConfig,
  HermesConfig,
  JiraConfig,
  OpaConfig,
  SkillSpec,
  SubagentSpec,
  WorkflowDefinition,
} from './workflowTypes';

export const mockWorkflows: WorkflowDefinition[] = [
  {
    id: 'wf-onboarding',
    name: 'Customer Onboarding',
    projectName: 'Noordzee Logistics B.V.',
    description: 'Processes new customer onboarding requirements end-to-end.',
    agentIds: ['requirement', 'impact', 'test-design', 'implementation', 'review', 'evidence'],
    subagentNames: ['log-collector'],
    skillNames: ['summarize'],
    mcpTools: [],
    trigger: {
      dashboardButtonEnabled: true,
      hermesSignalEnabled: false,
      fileDeliveryEnabled: false,
    },
    execution: { customerId: 'noordzee-logistics', repositoryUrl: '', timeoutSeconds: 600 },
    status: 'ACTIVE',
    lastExecutionStatus: 'COMPLETED',
    lastExecutionAt: new Date().toISOString(),
  },
];

// A workflow with a human approval gate enabled after `realisation` — a deterministic
// fixture for manual verification / future component tests exercising the gated path,
// without affecting `mockWorkflows` (the API fallback data) itself.
export const mockGatedWorkflow: WorkflowDefinition = {
  id: 'wf-spec-realise-gated',
  name: 'Spec to pull request (with approval)',
  projectName: 'Noordzee Logistics B.V.',
  description: 'Realisation workflow with a human approval gate after the realisation stage.',
  agentIds: [
    'requirement',
    'impact',
    'test-design',
    'implementation',
    'review',
    'realisation',
    'evidence',
  ],
  subagentNames: [],
  skillNames: [],
  mcpTools: [],
  trigger: { dashboardButtonEnabled: true, hermesSignalEnabled: false, fileDeliveryEnabled: false },
  execution: { customerId: 'noordzee-logistics', repositoryUrl: '', timeoutSeconds: 600 },
  approvalGate: { enabled: true, placementStage: 'realisation' },
  status: 'ACTIVE',
};

export const mockAgentSpecs: AgentSpec[] = [
  {
    name: 'triage-agent',
    description: 'Triages incoming issues',
    role: 'triage',
    instructions: 'Classify the input and route it to the right subagent.',
    subagentNames: ['log-collector'],
    skillNames: ['summarize'],
    mcpTools: [],
    workflowId: 'wf-onboarding',
  },
];

export const mockSubagentSpecs: SubagentSpec[] = [
  {
    name: 'log-collector',
    parentAgent: 'triage-agent',
    description: 'Collects logs for the incident window',
    responsibilities: 'Gather relevant application logs',
    instructions: 'Query the log store for the affected time range.',
    skillNames: [],
    mcpTools: [],
    workflowId: 'wf-onboarding',
  },
];

export const mockSkillSpecs: SkillSpec[] = [
  {
    name: 'summarize',
    description: 'Summarizes free text',
    inputContract: 'text',
    outputContract: 'summary',
    executionInstructions: 'Summarize the input in a few sentences.',
    mcpTools: [],
    policyNotes: '',
  },
];

export const mockHermesConfig: HermesConfig = {
  projectId: '',
  enabled: false,
  endpointUrl: '',
  signalType: '',
  workflowId: '',
  hasAuthToken: false,
};

export const mockJiraConfig: JiraConfig = {
  projectId: '',
  enabled: false,
  baseUrl: '',
  projectKey: '',
  issueTypeMapping: '',
  workflowId: '',
  hasAuthToken: false,
};

export const mockDocumentFolderConfig: DocumentFolderConfig = {
  projectId: '',
  projectName: '',
  folderName: '',
  folderPath: '',
  fileTriggerEnabled: false,
  allowedDocumentTypes: '',
  workflowId: '',
};

export const mockOpaConfig: OpaConfig = {
  enabled: false,
  baseUrl: '',
  policyPath: '/v1/data/metafactory/workflow/decision',
  healthPath: '/health',
  timeoutSeconds: 3,
  failMode: 'FAIL_CLOSED',
  decisionLoggingEnabled: true,
  decisionLogExportEnabled: true,
  environment: '',
  customerLabel: '',
  projectLabel: '',
  hasAuthToken: false,
};
