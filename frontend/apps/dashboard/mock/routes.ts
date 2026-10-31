import { mockWorkspace } from '../../../libs/api/src/testing/mockWorkspace';
import {
  type Rec,
  db,
  findBy,
  gitCredentials,
  integrationConfigs,
  nextId,
  notFound,
  now,
  queueOf,
  remove,
  slug,
  specsOf,
  upsert,
} from './store';

export interface Ctx {
  /** Path segments captured by the route's regex, URL-decoded. */
  p: string[];
  query: URLSearchParams;
  body: Rec;
}
type Handler = (ctx: Ctx) => unknown;
export type Route = [method: string, pattern: RegExp, handler: Handler];

export const routes: Route[] = [];
const add = (method: string, path: string, handler: Handler) => {
  // `:name` matches one path segment.
  const pattern = new RegExp(`^${path.replace(/:\w+/g, '([^/]+)')}$`);
  routes.push([method, pattern, handler]);
};
const get = (path: string, h: Handler) => add('GET', path, h);
const post = (path: string, h: Handler) => add('POST', path, h);
const put = (path: string, h: Handler) => add('PUT', path, h);
const patch = (path: string, h: Handler) => add('PATCH', path, h);
const del = (path: string, h: Handler) => add('DELETE', path, h);

const str = (value: unknown, fallback = '') => (typeof value === 'string' ? value : fallback);
const without = (rec: Rec, ...keys: string[]) =>
  Object.fromEntries(Object.entries(rec).filter(([key]) => !keys.includes(key)));

// ── Workspace & pre-flight ───────────────────────────────────────────────
get('/api/workspaces/:id', () => mockWorkspace);
post('/api/agentic-workflows/preflight', () => ({ passed: true, errors: [] }));

// ── Projects ─────────────────────────────────────────────────────────────
const project = (id: string) => findBy(db.projects, 'id', id, 'Project');
const patchProject = (id: string, changes: Rec) =>
  Object.assign(project(id), changes, { updatedAt: now() });

get('/api/projects', () => db.projects.filter((p) => p.active === 1));
get('/api/projects/all', () => db.projects);
post('/api/projects', ({ body }) => {
  const base = slug(str(body.name));
  let id = base;
  for (let n = 2; db.projects.some((p) => p.id === id); n++) id = `${base}-${n}`;
  const created: Rec = {
    customerId: id,
    active: 1,
    newProject: 0,
    gitStatus: 'UNKNOWN',
    hasCredentials: false,
    ...body,
    id,
    createdAt: now(),
    updatedAt: now(),
  };
  db.projects.push(created);
  return created;
});
put('/api/projects/:id', ({ p, body }) => patchProject(p[0], body));
patch('/api/projects/:id/activate', ({ p }) => patchProject(p[0], { active: 1 }));
patch('/api/projects/:id/deactivate', ({ p }) => patchProject(p[0], { active: 0 }));
patch('/api/projects/:id/new-project', ({ p, body }) =>
  patchProject(p[0], { newProject: body.newProject }),
);

const gitStatus = (id: string) => {
  const { gitStatus: status, gitStatusCheckedAt, gitStatusMessage } = project(id);
  return { projectId: id, gitStatus: status, gitStatusCheckedAt, gitStatusMessage };
};
get('/api/projects/:id/git-status', ({ p }) => gitStatus(p[0]));
post('/api/projects/:id/git-check', ({ p }) => {
  patchProject(p[0], { gitStatus: 'ACCESSIBLE', gitStatusCheckedAt: now() });
  return gitStatus(p[0]);
});

const noCredentials: Rec = { credentialType: 'NONE', hasSecret: false };
get('/api/projects/:id/git-credentials', ({ p }) => gitCredentials.get(p[0]) ?? noCredentials);
post('/api/projects/:id/git-credentials', ({ p, body }) => {
  const stored = {
    credentialType: body.credentialType,
    username: body.username,
    githubApiUrl: body.githubApiUrl,
    hasSecret: Boolean(body.secret) || gitCredentials.get(p[0])?.hasSecret === true,
  };
  gitCredentials.set(p[0], stored);
  patchProject(p[0], { hasCredentials: body.credentialType !== 'NONE' });
  return {};
});
del('/api/projects/:id/git-credentials', ({ p }) => {
  gitCredentials.delete(p[0]);
  patchProject(p[0], { hasCredentials: false });
  return {};
});

// Per-project integration settings. Secrets are accepted but only reported as `hasAuthToken`.
const integrationDefaults = (kind: string, projectId: string): Rec =>
  kind === 'document-folder'
    ? {
        projectId,
        projectName: str(project(projectId).name, projectId),
        folderName: projectId,
        fileTriggerEnabled: false,
      }
    : { projectId, enabled: false, hasAuthToken: false };

for (const [kind, store] of Object.entries(integrationConfigs)) {
  get(
    `/api/projects/:id/${kind}-config`,
    ({ p }) => store.get(p[0]) ?? integrationDefaults(kind, p[0]),
  );
  post(`/api/projects/:id/${kind}-config`, ({ p, body }) => {
    const previous = store.get(p[0]);
    const saved: Rec = {
      ...integrationDefaults(kind, p[0]),
      ...without(body, 'authToken'),
      ...(kind === 'document-folder'
        ? {}
        : { hasAuthToken: Boolean(body.authToken) || previous?.hasAuthToken === true }),
    };
    store.set(p[0], saved);
    return saved;
  });
  del(`/api/projects/:id/${kind}-config`, ({ p }) => {
    store.delete(p[0]);
    return {};
  });
}

// ── Spec files ───────────────────────────────────────────────────────────
const specResult = (projectId: string, fileName: string, message: string) => ({
  projectId,
  branch: `spec/${slug(fileName)}`,
  baseBranch: 'main',
  commitHash: 'a1b2c3d',
  fileName,
  message,
});
get('/api/projects/:id/spec-files', ({ p }) => specsOf(p[0]));
put('/api/projects/:id/spec-files', ({ p, body }) => {
  const fileName = str(body.fileName);
  const id = slug(fileName);
  const previous = specsOf(p[0]).find((s) => s.id === id);
  upsert(specsOf(p[0]), 'id', id, {
    ...previous,
    id,
    fileName,
    owner: str(previous?.owner, 'dev'),
    lastChanged: now(),
    status: str(previous?.status, 'open'),
    content: body.content,
  });
  return specResult(p[0], fileName, 'Saved (mock)');
});
get('/api/projects/:id/spec-init/status', () => ({ pending: false, templateExists: true }));
post('/api/projects/:id/spec-init', ({ p }) =>
  specResult(p[0], 'template.spec.md', 'Spec folder initialised (mock)'),
);

// ── Spec queue ───────────────────────────────────────────────────────────
const FINISHED = ['MERGED', 'COMPLETED_NO_CHANGES', 'SKIPPED'];
const withPositions = (items: Rec[]) => {
  let position = 0;
  for (const item of items) item.position = item.status === 'QUEUED' ? ++position : null;
};
const queueItem = (projectId: string, itemId: string) =>
  findBy(queueOf(projectId).items, 'id', itemId, 'Queue item');
const queueView = (projectId: string) => {
  const queue = queueOf(projectId);
  withPositions(queue.items);
  return {
    projectId,
    state: queue.state,
    autoMergeAllowed: queue.autoMergeAllowed,
    items: queue.items.filter((i) => !FINISHED.includes(str(i.status))),
    recentlyFinished: queue.items.filter((i) => FINISHED.includes(str(i.status))),
    lastPolledAt: null,
    runner: { enabled: true, configured: true },
  };
};
const workflowName = (workflowId: string) =>
  str(db.workflows.find((w) => w.id === workflowId)?.name) || null;

get('/api/projects/:id/spec-queue', ({ p }) => queueView(p[0]));
get('/api/projects/:id/spec-queue/settings', ({ p }) => ({
  autoMergeAllowed: queueOf(p[0]).autoMergeAllowed,
}));
put('/api/projects/:id/spec-queue/settings', ({ p, body }) => {
  queueOf(p[0]).autoMergeAllowed = body.autoMergeAllowed === true;
  return { autoMergeAllowed: queueOf(p[0]).autoMergeAllowed, updatedAt: now() };
});
post('/api/projects/:id/spec-queue/items', ({ p, body }) => {
  const item: Rec = {
    id: nextId('qi'),
    projectId: p[0],
    specFile: body.specFile,
    workflowId: body.workflowId,
    workflowName: workflowName(str(body.workflowId)),
    autoMerge: body.autoMerge === true,
    position: null,
    status: 'QUEUED',
    pullRequestUrls: [],
    createdBy: 'dev',
    createdAt: now(),
  };
  queueOf(p[0]).items.push(item);
  withPositions(queueOf(p[0]).items);
  return item;
});
patch('/api/projects/:id/spec-queue/items/:item', ({ p, body }) => {
  const item = queueItem(p[0], p[1]);
  if (body.workflowId !== undefined) {
    item.workflowId = body.workflowId;
    item.workflowName = workflowName(str(body.workflowId));
  }
  if (body.autoMerge !== undefined) item.autoMerge = body.autoMerge;
  return item;
});
del('/api/projects/:id/spec-queue/items/:item', ({ p }) => {
  const item = queueItem(p[0], p[1]);
  remove(queueOf(p[0]).items, 'id', p[1], 'Queue item');
  return item;
});
post('/api/projects/:id/spec-queue/items/:item/retry', ({ p }) => {
  const item = queueItem(p[0], p[1]);
  delete item.failureReason;
  item.status = 'QUEUED';
  return item;
});
post('/api/projects/:id/spec-queue/items/:item/skip', ({ p }) =>
  Object.assign(queueItem(p[0], p[1]), { status: 'SKIPPED', finishedAt: now() }),
);
put('/api/projects/:id/spec-queue/order', ({ p, body }) => {
  const ids = Array.isArray(body.itemIds) ? body.itemIds : [];
  const queue = queueOf(p[0]);
  const rank = (item: Rec) => {
    const index = ids.indexOf(item.id);
    return index === -1 ? ids.length : index;
  };
  queue.items.sort((a, b) => rank(a) - rank(b));
  return queueView(p[0]);
});
post('/api/projects/:id/spec-queue/pause', ({ p }) => {
  queueOf(p[0]).state = 'PAUSED';
  return queueView(p[0]);
});
post('/api/projects/:id/spec-queue/resume', ({ p }) => {
  queueOf(p[0]).state = 'ACTIVE';
  return queueView(p[0]);
});

// ── Workflows, groups, runs ──────────────────────────────────────────────
const workflow = (id: string) => findBy(db.workflows, 'id', id, 'Workflow');

get('/api/workflows', () => db.workflows);
get('/api/workflows/export', () => ({ groups: db.groups, workflows: db.workflows }));
post('/api/workflows/import', ({ body }) => {
  const groups = Array.isArray(body.groups) ? (body.groups as Rec[]) : [];
  const workflows = Array.isArray(body.workflows) ? (body.workflows as Rec[]) : [];
  for (const g of groups) upsert(db.groups, 'id', str(g.id), g);
  for (const w of workflows) upsert(db.workflows, 'id', str(w.id), w);
  return { groupsImported: groups.length, workflowsImported: workflows.length };
});
get('/api/workflows/:id', ({ p }) => workflow(p[0]));
post('/api/workflows', ({ body }) => {
  const created = { ...body, id: str(body.id) || `wf-${slug(str(body.name))}` };
  db.workflows.push(created);
  return created;
});
put('/api/workflows/:id', ({ p, body }) => {
  const updated = { ...workflow(p[0]), ...body, id: p[0] };
  return upsert(db.workflows, 'id', p[0], updated);
});
del('/api/workflows/:id', ({ p }) => {
  remove(db.workflows, 'id', p[0], 'Workflow');
  return {};
});

get('/api/workflow-groups', () => db.groups);
post('/api/workflow-groups', ({ body }) => {
  const created = { ...body, id: str(body.id) || `grp-${slug(str(body.name))}` };
  db.groups.push(created);
  return created;
});
put('/api/workflow-groups/:id', ({ p, body }) =>
  upsert(db.groups, 'id', p[0], { ...body, id: p[0] }),
);
del('/api/workflow-groups/:id', ({ p }) => {
  remove(db.groups, 'id', p[0], 'Workflow group');
  return {};
});

// A started run reads as RUNNING for a few seconds, then COMPLETED, so polling UIs see both.
const RUN_DURATION_MS = 5_000;
const EVENT_TITLES: Record<string, string> = { RUNNING: 'Started', COMPLETED: 'Finished' };
function runView(run: Rec): Rec {
  const startedAt = Date.parse(str(run.startedAt));
  const done = Date.now() - startedAt >= RUN_DURATION_MS;
  const agentIds = run.agentIds as string[];
  const status = done ? 'COMPLETED' : 'RUNNING';
  return {
    ...without(run, 'agentIds'),
    status,
    completedAt: done ? new Date(startedAt + RUN_DURATION_MS).toISOString() : null,
    events: (done ? agentIds : agentIds.slice(0, 1)).map((agentId) => ({
      timestamp: str(run.startedAt),
      agentId,
      title: EVENT_TITLES[status],
      status: done ? 'COMPLETED' : 'RUNNING',
      evidenceRef: '',
    })),
    generatedArtifacts: done ? ['report.md'] : [],
  };
}
const run = (id: string) => runView(findBy(db.runs, 'runId', id, 'Run'));

post('/api/workflows/:id/start', ({ p, body }) => {
  const wf = workflow(p[0]);
  const startedAt = now();
  const runId = nextId('run');
  const execution = wf.execution as Rec | undefined;
  db.runs.push({
    runId,
    workflowId: p[0],
    customerId: str(body.projectId, str(execution?.customerId, 'noordzee')),
    specFile: str(body.specFile),
    repositoryUrl: str(body.repositoryUrl),
    startedAt,
    startedBy: 'dev',
    failureSummary: null,
    agentIds: Array.isArray(wf.agentIds) && wf.agentIds.length > 0 ? wf.agentIds : ['agent'],
  });
  Object.assign(wf, { lastExecutionStatus: 'RUNNING', lastExecutionAt: startedAt });
  return {
    workflowId: p[0],
    executionId: runId,
    status: 'RUNNING',
    startedAt,
    message: 'Started (mock)',
  };
});
get('/api/workflows/:id/executions', ({ p, query }) => {
  const limit = Number(query.get('limit') ?? 20);
  const offset = Number(query.get('offset') ?? 0);
  const all = db.runs
    .filter((r) => r.workflowId === p[0])
    .map(runView)
    .reverse();
  return {
    items: all.slice(offset, offset + limit).map((r) => ({
      runId: r.runId,
      workflowId: r.workflowId,
      status: r.status,
      startedAt: r.startedAt,
      completedAt: r.completedAt,
      durationMillis: r.completedAt === null ? null : RUN_DURATION_MS,
      startedBy: r.startedBy,
    })),
    limit,
    offset,
    total: all.length,
    hasMore: offset + limit < all.length,
  };
});
get('/api/workflows/:id/executions/:run', ({ p }) => run(p[1]));
get('/api/agent-runs/:id', ({ p }) => run(p[0]));
del('/api/agent-runs/:id', ({ p }) => {
  remove(db.runs, 'runId', p[0], 'Run');
  return {};
});
get('/api/agent-runs/:id/approval-gate', () => {
  throw notFound('Approval gate');
});
get('/api/agent-runs/:id/approval-decisions', () => []);
post('/api/agent-runs/:id/approval-gate/decision', ({ p }) => ({
  runId: p[0],
  status: 'RUNNING',
  iteration: 1,
  message: 'Decision recorded (mock)',
}));
get('/api/workflows/:id/decision-logs', () => []);
get('/api/workflow-executions/:id/decision-logs', () => []);

// ── Agent / subagent / skill definitions (keyed by name) ─────────────────
const definitions: [path: string, list: Rec[], generate: (prompt: string) => Rec][] = [
  [
    'agent-definitions',
    db.agents,
    (prompt) => ({
      role: 'generated',
      instructions: prompt,
      subagentNames: [],
      skillNames: [],
      mcpTools: [],
    }),
  ],
  [
    'subagent-definitions',
    db.subagents,
    (prompt) => ({
      parentAgent: '',
      responsibilities: prompt,
      instructions: prompt,
      skillNames: [],
      mcpTools: [],
    }),
  ],
  [
    'skill-definitions',
    db.skills,
    (prompt) => ({
      inputContract: 'text',
      outputContract: 'text',
      executionInstructions: prompt,
      mcpTools: [],
    }),
  ],
];
for (const [path, list, generate] of definitions) {
  get(`/api/${path}`, () => list);
  // Registered before `/:name` routes: generation must not be read as a name.
  post(`/api/${path}/generate-from-prompt`, ({ body }) => {
    const prompt = str(body.prompt);
    return {
      name: slug(prompt.split(/\s+/).slice(0, 3).join(' ')),
      description: prompt,
      ...generate(prompt),
    };
  });
  post(`/api/${path}`, ({ body }) => {
    list.push(body);
    return body;
  });
  put(`/api/${path}/:name`, ({ p, body }) => upsert(list, 'name', p[0], body));
  del(`/api/${path}/:name`, ({ p }) => {
    remove(list, 'name', p[0], 'Definition');
    return {};
  });
}

// ── A2A services, catalogs, marketplaces ─────────────────────────────────
const a2aService = (body: Rec, id: string): Rec => ({
  id,
  name: body.name,
  type: 'A2A',
  source: 'DASHBOARD',
  editable: true,
  baseUrl: body.baseUrl,
  publicUrl: body.publicUrl ?? null,
  hasApiKey: Boolean(body.apiKey),
  callerTokenUrl: body.callerTokenUrl ?? null,
  callerClientId: body.callerClientId ?? null,
  hasCallerClientSecret: Boolean(body.callerClientSecret),
  status: 'OK',
  message: null,
  agentCount: 0,
  updatedAt: now(),
});
get('/api/a2a-services', () => db.a2aServices);
post('/api/a2a-services', ({ body }) => {
  const created = a2aService(body, str(body.id) || slug(str(body.name)));
  db.a2aServices.push(created);
  return created;
});
put('/api/a2a-services/:id', ({ p, body }) =>
  upsert(db.a2aServices, 'id', p[0], a2aService(body, p[0])),
);
del('/api/a2a-services/:id', ({ p }) => {
  remove(db.a2aServices, 'id', p[0], 'A2A service');
  return {};
});

get('/api/agent-catalog', () => ({
  local: {
    name: 'Local',
    status: 'OK',
    agents: db.agents.map((agent) => ({
      id: agent.name,
      name: agent.name,
      description: agent.description,
      path: `${str(agent.name)}.md`,
      folders: [],
      skills: agent.skillNames ?? [],
      tools: [],
      valid: true,
    })),
  },
  providers: db.a2aServices.map((s) => ({ name: s.name, type: s.type, status: 'OK', agents: [] })),
}));
get('/api/agent-catalog/providers/:provider/agents/:agent/card', ({ p }) => ({
  name: p[1],
  description: `Mock agent card for ${p[1]}`,
  version: '1.0.0',
  protocolVersion: '0.3.0',
  capabilities: { streaming: false },
  skills: [],
}));

get('/api/skill-catalog', () => {
  const enabled = db.marketplaces.filter((m) => m.enabled === true);
  return {
    localSkills: db.skills,
    externalSkills: enabled.map((m) => ({
      marketplaceId: m.id,
      marketplaceName: m.name,
      name: `${slug(str(m.name))}-sample`,
      description: 'Sample marketplace skill',
    })),
    sources: [
      {
        kind: 'LOCAL',
        marketplaceId: null,
        name: 'Local',
        outcome: 'SUCCESS',
        itemCount: db.skills.length,
        httpStatus: null,
      },
      ...enabled.map((m) => ({
        kind: 'MARKETPLACE',
        marketplaceId: m.id,
        name: m.name,
        outcome: 'SUCCESS',
        itemCount: 1,
        httpStatus: 200,
      })),
    ],
  };
});

const marketplace = (body: Rec, previous?: Rec): Rec => ({
  ...previous,
  ...without(body, 'apiKey'),
  id: previous?.id ?? nextId('m'),
  hasApiKey: Boolean(body.apiKey) || previous?.hasApiKey === true,
  createdBy: previous?.createdBy ?? 'dev',
  createdAt: previous?.createdAt ?? now(),
  updatedAt: now(),
});
get('/api/skills-marketplaces', () => db.marketplaces);
post('/api/skills-marketplaces', ({ body }) => {
  const created = marketplace(body);
  db.marketplaces.push(created);
  return created;
});
put('/api/skills-marketplaces/:id', ({ p, body }) =>
  upsert(
    db.marketplaces,
    'id',
    p[0],
    marketplace(body, findBy(db.marketplaces, 'id', p[0], 'Marketplace')),
  ),
);
del('/api/skills-marketplaces/:id', ({ p }) => {
  remove(db.marketplaces, 'id', p[0], 'Marketplace');
  return {};
});

// ── OPA ──────────────────────────────────────────────────────────────────
get('/api/opa-config', () => db.opa);
put('/api/opa-config', ({ body }) => {
  Object.assign(db.opa, without(body, 'authToken'), {
    hasAuthToken: Boolean(body.authToken) || db.opa.hasAuthToken === true,
  });
  return db.opa;
});
get('/api/opa-config/health', () => ({ reachable: db.opa.enabled === true }));
