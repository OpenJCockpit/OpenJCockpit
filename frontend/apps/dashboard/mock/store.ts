import {
  agents,
  allProjects,
  groups,
  marketplaces,
  skills,
  specs,
  subagents,
  workflows,
} from '../visual/fixtures.mjs';

// In-memory state behind the mock API. It lives as long as the dev server and starts from the
// fixtures the visual-regression runner uses, so edits survive reloads but not a server restart.
export type Rec = Record<string, unknown>;

const clone = <T>(value: T): T => structuredClone(value);
export const now = () => new Date().toISOString();

export const slug = (text: string) =>
  text
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '') || 'item';

export class MockError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

export const notFound = (what: string) => new MockError(404, 'NOT_FOUND', `${what} not found`);

let counter = 0;
export const nextId = (prefix: string) => `${prefix}-${++counter}`;

export const db = {
  projects: clone(allProjects) as Rec[],
  workflows: clone(workflows) as Rec[],
  groups: clone(groups) as Rec[],
  agents: clone(agents) as Rec[],
  subagents: clone(subagents) as Rec[],
  skills: clone(skills) as Rec[],
  marketplaces: clone(marketplaces) as Rec[],
  a2aServices: [] as Rec[],
  runs: [] as Rec[],
  opa: {
    enabled: false,
    baseUrl: 'http://localhost:8181',
    policyPath: '/v1/data/openjcockpit/allow',
    healthPath: '/health',
    timeoutSeconds: 5,
    failMode: 'CLOSED',
    decisionLoggingEnabled: true,
    decisionLogExportEnabled: false,
    environment: 'local',
    customerLabel: 'noordzee',
    projectLabel: 'noordzee',
    hasAuthToken: false,
  } as Rec,
};

/** State that exists once per project, created on first access. */
export function perProject<T>(store: Map<string, T>, projectId: string, init: () => T): T {
  let value = store.get(projectId);
  if (value === undefined) {
    value = init();
    store.set(projectId, value);
  }
  return value;
}

export const projectSpecs = new Map<string, Rec[]>();
export const specsOf = (projectId: string) =>
  perProject(projectSpecs, projectId, () => clone(specs) as Rec[]);

export interface QueueState {
  state: string;
  autoMergeAllowed: boolean;
  items: Rec[];
}
export const queues = new Map<string, QueueState>();
export const queueOf = (projectId: string) =>
  perProject(queues, projectId, () => ({ state: 'ACTIVE', autoMergeAllowed: false, items: [] }));

export const gitCredentials = new Map<string, Rec>();
export const integrationConfigs = {
  hermes: new Map<string, Rec>(),
  jira: new Map<string, Rec>(),
  'document-folder': new Map<string, Rec>(),
};

export function findBy(list: Rec[], key: string, value: string, what: string): Rec {
  const found = list.find((item) => item[key] === value);
  if (!found) throw notFound(what);
  return found;
}

/** Replaces the entry whose `key` equals `value`, or appends when there is none. */
export function upsert(list: Rec[], key: string, value: string, entry: Rec): Rec {
  const index = list.findIndex((item) => item[key] === value);
  if (index === -1) list.push(entry);
  else list[index] = entry;
  return entry;
}

export function remove(list: Rec[], key: string, value: string, what: string): void {
  const index = list.findIndex((item) => item[key] === value);
  if (index === -1) throw notFound(what);
  list.splice(index, 1);
}
