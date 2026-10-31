import {
  act,
  render,
  renderHook,
  screen,
  waitFor,
  type RenderResult,
} from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import * as api from '@openjcockpit/api';
import type { SpecQueue } from '@openjcockpit/api';
import { createI18n, I18nProvider } from '@openjcockpit/i18n';
import { FakeHubProjectProvider } from '@openjcockpit/hub-contract/testing';
import { specsApps } from '../specsApps';
import { specsContributionsResources } from './i18n';
import SpecFilesWidget from './SpecFilesWidget';
import SpecQueueWidget from './SpecQueueWidget';
import { useQueueAttention } from './useQueueAttention';
import { useSpecFileBadge } from './useSpecFileCount';
import { useWorkflowCount } from './useWorkflowCount';
import WorkflowsWidget from './WorkflowsWidget';

vi.mock('@openjcockpit/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@openjcockpit/api')>();
  return {
    ...actual,
    getSpecQueue: vi.fn(),
    listWorkflows: vi.fn(),
    loadProjectSpecs: vi.fn(),
  };
});

const project = { id: 'p1', name: 'Project' } as never;

type Item = SpecQueue['items'][number];
const item = (status: Item['status'], extra: Partial<Item> = {}) =>
  ({ id: `${status}-${Math.random()}`, status, workflowId: 'wf 1', ...extra }) as Item;
const queue = (items: Item[], state: SpecQueue['state'] = 'ACTIVE') =>
  ({ state, items }) as SpecQueue;
const workflow = (name: string, lastExecutionAt?: string) =>
  ({ id: name, name, lastExecutionAt }) as api.WorkflowDefinition;

function wrap(selected: unknown = project) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const i18n = createI18n({ locale: 'en', resources: specsContributionsResources });
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <I18nProvider i18n={i18n}>
        <QueryClientProvider client={client}>
          <FakeHubProjectProvider selected={selected as never}>{children}</FakeHubProjectProvider>
        </QueryClientProvider>
      </I18nProvider>
    );
  };
}

async function renderIn(ui: ReactNode, selected: unknown = project): Promise<RenderResult> {
  let result!: RenderResult;
  await act(async () => {
    result = render(ui, { wrapper: wrap(selected) });
  });
  return result;
}

beforeEach(() => {
  vi.mocked(api.getSpecQueue).mockReset();
  vi.mocked(api.listWorkflows).mockReset();
  vi.mocked(api.loadProjectSpecs).mockReset();
});

describe('spec queue badge', () => {
  it('counts merges to review, failures and approvals, and ignores the rest', async () => {
    vi.mocked(api.getSpecQueue).mockResolvedValue(
      queue([
        item('AWAITING_MERGE'),
        item('FAILED'),
        item('RUNNING', { runStatus: 'AWAITING_APPROVAL' }),
        item('RUNNING', { runStatus: 'RUNNING' }),
        item('QUEUED'),
      ]),
    );
    const { result } = renderHook(() => useQueueAttention(), { wrapper: wrap() });
    expect(result.current).toBeNull();
    await waitFor(() => expect(result.current).toBe(3));
  });

  it('shows no badge when nothing needs attention', async () => {
    vi.mocked(api.getSpecQueue).mockResolvedValue(queue([item('QUEUED')]));
    const { result } = renderHook(() => useQueueAttention(), { wrapper: wrap() });
    await waitFor(() => expect(api.getSpecQueue).toHaveBeenCalledWith('p1'));
    await act(async () => undefined);
    expect(result.current).toBeNull();
  });

  it('does not load anything without a project', async () => {
    const { result } = renderHook(() => useQueueAttention(), { wrapper: wrap(null) });
    expect(result.current).toBeNull();
    expect(api.getSpecQueue).not.toHaveBeenCalled();
  });
});

describe('spec queue widget', () => {
  it('summarises the queue', async () => {
    vi.mocked(api.getSpecQueue).mockResolvedValue(
      queue([
        item('QUEUED'),
        item('QUEUED'),
        item('RUNNING'),
        item('RUNNING', { runStatus: 'AWAITING_APPROVAL' }),
        item('FAILED'),
      ]),
    );
    await renderIn(<SpecQueueWidget />);
    expect(await screen.findByText('Queued: 2')).toBeInTheDocument();
    expect(screen.getByText('Running: 1')).toBeInTheDocument();
    expect(screen.getByText('Needs attention: 2')).toBeInTheDocument();
  });

  it('shows a loading line until the queue is known', async () => {
    vi.mocked(api.getSpecQueue).mockReturnValue(new Promise(() => undefined));
    await renderIn(<SpecQueueWidget />);
    expect(screen.queryByTestId('spec-queue-widget')).toBeNull();
    expect(screen.getByRole('status')).toHaveTextContent('Loading…');
  });

  it('shows an error with retry when the queue fails to load', async () => {
    vi.mocked(api.getSpecQueue).mockRejectedValue(new Error('boom'));
    await renderIn(<SpecQueueWidget />);
    expect(await screen.findByRole('alert')).toHaveTextContent('Failed to load the spec queue.');
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
  });

  it('asks for a project when none is selected', async () => {
    await renderIn(<SpecQueueWidget />, null);
    expect(await screen.findByText('Select a project to see this.')).toBeInTheDocument();
  });
});

describe('workflow badge', () => {
  it('counts the workflows', async () => {
    vi.mocked(api.listWorkflows).mockResolvedValue([workflow('a'), workflow('b')]);
    const { result } = renderHook(() => useWorkflowCount(), { wrapper: wrap() });
    expect(result.current).toBeNull();
    await waitFor(() => expect(result.current).toBe(2));
  });

  it('shows no badge when there are none', async () => {
    vi.mocked(api.listWorkflows).mockResolvedValue([]);
    const { result } = renderHook(() => useWorkflowCount(), { wrapper: wrap() });
    await waitFor(() => expect(api.listWorkflows).toHaveBeenCalled());
    await act(async () => undefined);
    expect(result.current).toBeNull();
  });
});

describe('workflows widget', () => {
  it('lists the five most recently run workflows first', async () => {
    vi.mocked(api.listWorkflows).mockResolvedValue([
      workflow('never'),
      workflow('old', '2026-01-01'),
      workflow('never too'),
      workflow('new', '2026-03-01'),
      workflow('mid', '2026-02-01'),
      workflow('a', '2025-12-01'),
      workflow('b', '2025-11-01'),
      workflow('never again'),
    ]);
    await renderIn(<WorkflowsWidget />);
    const items = await screen.findAllByRole('listitem');
    expect(items.map((li) => li.textContent)).toEqual(['new', 'mid', 'old', 'a', 'b']);
  });

  it('says so when there are no workflows', async () => {
    vi.mocked(api.listWorkflows).mockResolvedValue([]);
    await renderIn(<WorkflowsWidget />);
    expect(await screen.findByText('No workflows yet.')).toBeInTheDocument();
  });

  it('shows a loading line until the list is known', async () => {
    vi.mocked(api.listWorkflows).mockReturnValue(new Promise(() => undefined));
    await renderIn(<WorkflowsWidget />);
    expect(screen.queryByTestId('workflows-widget')).toBeNull();
    expect(screen.getByRole('status')).toHaveTextContent('Loading…');
  });

  it('shows an error with retry when the list fails to load', async () => {
    vi.mocked(api.listWorkflows).mockRejectedValue(new Error('boom'));
    await renderIn(<WorkflowsWidget />);
    expect(await screen.findByRole('alert')).toHaveTextContent('Failed to load the workflows.');
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
  });
});

describe('spec files badge and widget', () => {
  it('counts the spec files of the selected project', async () => {
    vi.mocked(api.loadProjectSpecs).mockResolvedValue([{}, {}, {}] as never);
    const { result } = renderHook(() => useSpecFileBadge(), { wrapper: wrap() });
    expect(result.current).toBeNull();
    await waitFor(() => expect(result.current).toBe(3));
  });

  it('shows no badge when the project has no spec files', async () => {
    vi.mocked(api.loadProjectSpecs).mockResolvedValue([]);
    const { result } = renderHook(() => useSpecFileBadge(), { wrapper: wrap() });
    await waitFor(() => expect(api.loadProjectSpecs).toHaveBeenCalled());
    await act(async () => undefined);
    expect(result.current).toBeNull();
  });

  it('does not load anything without a project', async () => {
    const { result } = renderHook(() => useSpecFileBadge(), { wrapper: wrap(null) });
    expect(result.current).toBeNull();
    expect(api.loadProjectSpecs).not.toHaveBeenCalled();
  });

  it('shows the count in the widget, including zero', async () => {
    vi.mocked(api.loadProjectSpecs).mockResolvedValue([]);
    await renderIn(<SpecFilesWidget />);
    expect(await screen.findByText('Spec files: 0')).toBeInTheDocument();
  });

  it('shows a loading line in the widget until the count is known', async () => {
    vi.mocked(api.loadProjectSpecs).mockReturnValue(new Promise(() => undefined));
    await renderIn(<SpecFilesWidget />);
    expect(screen.queryByTestId('spec-files-widget')).toBeNull();
    expect(screen.getByRole('status')).toHaveTextContent('Loading…');
  });

  it('shows an error with retry in the widget when loading fails', async () => {
    vi.mocked(api.loadProjectSpecs).mockRejectedValue(new Error('boom'));
    await renderIn(<SpecFilesWidget />);
    expect(await screen.findByRole('alert')).toHaveTextContent('Failed to load the spec files.');
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
  });

  it('asks for a project when none is selected', async () => {
    await renderIn(<SpecFilesWidget />, null);
    expect(await screen.findByText('Select a project to see this.')).toBeInTheDocument();
  });
});

describe('app definitions', () => {
  const app = (id: string) => specsApps.find((candidate) => candidate.id === id)!;

  it('declares each contribution and resolves the lazy components to the real ones', async () => {
    expect(app('workflow').useBadge).toBe(useWorkflowCount);
    expect(app('spec-files').useBadge).toBe(useSpecFileBadge);
    expect(app('spec-queue').useBadge).toBe(useQueueAttention);
    expect((await app('workflow').widgets![0]!.component()).default).toBe(WorkflowsWidget);
    expect((await app('spec-files').widgets![0]!.component()).default).toBe(SpecFilesWidget);
    expect((await app('spec-queue').widgets![0]!.component()).default).toBe(SpecQueueWidget);
  });

  it('links each widget to its own app route', () => {
    for (const definition of specsApps) {
      expect(definition.widgets?.[0]?.link).toBe(definition.route);
      expect(definition.widgets?.[0]?.sourceAppId).toBe(definition.id);
    }
  });
});
