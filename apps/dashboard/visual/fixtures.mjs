const ts = '2026-01-01T10:00:00Z';
const project = (id, name, extra = {}) => ({
  id,
  name,
  customerId: id,
  gitUrl: `https://git.example.com/${id}.git`,
  description: `${name} project`,
  defaultBranch: 'main',
  environment: 'production',
  owner: 'owner',
  active: 1,
  newProject: 0,
  gitStatus: 'ACCESSIBLE',
  gitStatusCheckedAt: ts,
  hasCredentials: true,
  createdAt: ts,
  updatedAt: ts,
  ...extra,
});

export const projects = [
  project('noordzee', 'Noordzee Logistics B.V.'),
  project('delta', 'Delta Energy', {
    gitStatus: 'NOT_ACCESSIBLE',
    gitStatusMessage: 'auth failed',
    hasCredentials: false,
  }),
];
export const allProjects = [...projects, project('old', 'Archived Co', { active: 0 })];

export const workflows = [
  {
    id: 'wf1',
    name: 'Spec to pull request',
    projectName: '',
    description: 'Realise a spec.',
    agentIds: ['requirement', 'implementation', 'review'],
    subagentNames: [],
    skillNames: ['summarize'],
    mcpTools: [],
    trigger: {
      dashboardButtonEnabled: true,
      hermesSignalEnabled: false,
      fileDeliveryEnabled: false,
    },
    execution: { timeoutSeconds: 600 },
    promptRequired: true,
    status: 'ACTIVE',
    lastExecutionStatus: 'COMPLETED',
    lastExecutionAt: ts,
  },
];

export const specs = [
  {
    id: 's1',
    fileName: 'feature-a.md',
    owner: 'owner',
    lastChanged: ts,
    status: 'open',
    selected: true,
    content: '# Feature A\n\nDo the thing.',
  },
  {
    id: 's2',
    fileName: 'feature-b.md',
    owner: 'owner',
    lastChanged: ts,
    status: 'review',
    content: '# Feature B',
  },
];

export const groups = [
  { id: 'g1', name: 'Core flows', description: 'Core workflows', projectName: '' },
];
export const agents = [
  {
    name: 'planner',
    description: 'Plans the work',
    role: 'planner',
    instructions: 'Plan.',
    subagentNames: ['helper'],
    skillNames: ['summarize'],
    mcpTools: [],
  },
];
export const subagents = [
  {
    name: 'helper',
    parentAgent: 'planner',
    description: 'Helps the planner',
    responsibilities: 'Assist',
    instructions: 'Help.',
    skillNames: ['summarize'],
    mcpTools: [],
  },
];
export const skills = [
  {
    name: 'summarize',
    description: 'Summarizes text',
    inputContract: 'text',
    outputContract: 'summary',
    executionInstructions: 'Summarize.',
    mcpTools: [],
  },
];

export const marketplaces = [
  {
    id: 'm1',
    name: 'Main marketplace',
    marketplaceUrl: 'https://skills.example.com',
    description: 'Skills',
    enabled: true,
    hasApiKey: true,
    createdAt: ts,
    updatedAt: ts,
  },
];

// path suffix -> JSON body; anything else answers 404 and the app uses its own fallbacks
export const routes = [
  [/\/api\/projects\/all$/, allProjects],
  [/\/api\/projects$/, projects],
  [/\/api\/projects\/[^/]+\/spec-files$/, specs],
  [/\/api\/workflows$/, workflows],
  [/\/api\/workflow-groups$/, groups],
  [/\/api\/skills-marketplaces$/, marketplaces],
  [/\/api\/agent-definitions$/, agents],
  [/\/api\/subagent-definitions$/, subagents],
  [/\/api\/skill-definitions$/, skills],
];
