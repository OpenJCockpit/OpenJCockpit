import { describe, expect, it } from 'vitest';
import { fixedT } from '@openjcockpit/i18n/testing';
import { apiResources } from '../i18n';

import type { AgentEvent } from '../types/types';
import {
  buildAgentCards,
  buildSequencerNodes,
  formatRunDuration,
  isTerminalRunStatus,
  runStatusKind,
  runStatusLabel,
} from './runStatus';
import type { WorkflowDefinition } from './workflowTypes';

const t = fixedT('api', apiResources);

describe('runStatusKind', () => {
  it('returns ok for COMPLETED', () => {
    expect(runStatusKind('COMPLETED')).toBe('ok');
  });

  it('returns changes for FAILED', () => {
    expect(runStatusKind('FAILED')).toBe('changes');
  });

  it('returns changes for CANCELLED', () => {
    expect(runStatusKind('CANCELLED')).toBe('changes');
  });

  it('returns changes for DENIED', () => {
    expect(runStatusKind('DENIED')).toBe('changes');
  });

  it('returns changes for RUN_STATE_LOST', () => {
    expect(runStatusKind('RUN_STATE_LOST')).toBe('changes');
  });

  it('returns changes for NOT_FOUND', () => {
    expect(runStatusKind('NOT_FOUND')).toBe('changes');
  });

  it('returns waiting for AWAITING_APPROVAL', () => {
    expect(runStatusKind('AWAITING_APPROVAL')).toBe('waiting');
  });

  it('returns active for RUNNING', () => {
    expect(runStatusKind('RUNNING')).toBe('active');
  });

  it('returns waiting (safe fallback) for an unrecognized status without throwing', () => {
    expect(() => runStatusKind('SOMETHING_UNKNOWN')).not.toThrow();
    expect(runStatusKind('SOMETHING_UNKNOWN')).toBe('waiting');
  });
});

describe('runStatusLabel', () => {
  it('labels child-workflow, timeout and blocked states', () => {
    expect(runStatusLabel('AWAITING_CHILD_WORKFLOW', t)).toBe('Waiting for child workflow');
    expect(runStatusLabel('TIMED_OUT', t)).toBe('Timed out');
    expect(runStatusLabel('BLOCKED', t)).toBe('Blocked');
  });

  it('returns Awaiting approval for AWAITING_APPROVAL', () => {
    expect(runStatusLabel('AWAITING_APPROVAL', t)).toBe('Awaiting approval');
  });

  it('translates the lifecycle statuses', () => {
    expect(runStatusLabel('COMPLETED', t)).toBe('Completed');
    expect(runStatusLabel('FAILED', t)).toBe('Failed');
    expect(runStatusLabel('CANCELLED', t)).toBe('Cancelled');
    expect(runStatusLabel('DENIED', t)).toBe('Denied');
    expect(runStatusLabel('RUN_STATE_LOST', t)).toBe('Run state lost');
    expect(runStatusLabel('NOT_FOUND', t)).toBe('Not found');
    expect(runStatusLabel('RUNNING', t)).toBe('Running');
    expect(runStatusLabel('ACTIVE', t)).toBe('Active');
  });

  it('returns an unknown status string unchanged', () => {
    expect(runStatusLabel('SOMETHING_UNKNOWN', t)).toBe('SOMETHING_UNKNOWN');
  });
});

describe('runStatusKind child-workflow states', () => {
  it('treats TIMED_OUT and BLOCKED as changes, AWAITING_CHILD_WORKFLOW as waiting', () => {
    expect(runStatusKind('TIMED_OUT')).toBe('changes');
    expect(runStatusKind('BLOCKED')).toBe('changes');
    expect(runStatusKind('AWAITING_CHILD_WORKFLOW')).toBe('waiting');
  });
});

describe('isTerminalRunStatus', () => {
  it('returns true for terminal statuses', () => {
    expect(isTerminalRunStatus('COMPLETED')).toBe(true);
    expect(isTerminalRunStatus('FAILED')).toBe(true);
    expect(isTerminalRunStatus('CANCELLED')).toBe(true);
    expect(isTerminalRunStatus('DENIED')).toBe(true);
    expect(isTerminalRunStatus('RUN_STATE_LOST')).toBe(true);
    expect(isTerminalRunStatus('NOT_FOUND')).toBe(true);
  });

  it('returns false for non-terminal statuses', () => {
    expect(isTerminalRunStatus('TIMED_OUT')).toBe(true);
    expect(isTerminalRunStatus('BLOCKED')).toBe(true);
    expect(isTerminalRunStatus('AWAITING_CHILD_WORKFLOW')).toBe(false);
    expect(isTerminalRunStatus('RUNNING')).toBe(false);
    expect(isTerminalRunStatus('AWAITING_APPROVAL')).toBe(false);
    expect(isTerminalRunStatus('SOMETHING_UNKNOWN')).toBe(false);
  });
});

describe('formatRunDuration', () => {
  it('formats a 2m 15s duration', () => {
    expect(formatRunDuration('2024-01-01T00:00:00.000Z', '2024-01-01T00:02:15.000Z', t)).toBe(
      '2m 15s',
    );
  });

  it('returns n/a when completedAt is null', () => {
    expect(formatRunDuration('2024-01-01T00:00:00.000Z', null, t)).toBe('n/a');
  });

  it('returns n/a when completedAt is undefined', () => {
    expect(formatRunDuration('2024-01-01T00:00:00.000Z', undefined, t)).toBe('n/a');
  });

  it('returns 0s for a near-zero boundary case without throwing', () => {
    expect(() =>
      formatRunDuration('2024-01-01T00:00:00.000Z', '2024-01-01T00:00:00.400Z', t),
    ).not.toThrow();
    expect(formatRunDuration('2024-01-01T00:00:00.000Z', '2024-01-01T00:00:00.400Z', t)).toBe('0s');
  });

  it('formats an hour-scale duration', () => {
    expect(formatRunDuration('2024-01-01T00:00:00.000Z', '2024-01-01T01:03:00.000Z', t)).toBe(
      '1h 3m',
    );
  });
});

describe('buildAgentCards / buildSequencerNodes', () => {
  const workflow = {
    id: 'wf',
    name: 'WF',
    agentIds: ['a', 'b'],
  } as WorkflowDefinition;
  const event = (agentId: string, status: string) => ({ agentId, status }) as AgentEvent;

  it('derives agent card status from the latest events', () => {
    const cards = buildAgentCards(
      workflow,
      [],
      {
        a: event('a', 'DONE'),
        b: event('b', 'RUNNING'),
      },
      t,
    );
    expect(cards.map((c) => c.statusKind)).toEqual(['done', 'active']);
    expect(buildAgentCards(null, [], {}, t)).toEqual([]);
  });

  it('names agent cards from their definitions', () => {
    const cards = buildAgentCards(
      workflow,
      [{ id: 'a', name: 'Alpha', description: 'First' } as never],
      {},
      t,
    );
    expect(cards[0]).toMatchObject({ name: 'Alpha', description: 'First' });
  });

  it('returns the agent cards untouched without a workflow or without orbs', () => {
    const cards = buildAgentCards(workflow, [], {}, t);
    expect(buildSequencerNodes(null, cards, [], null, new Map(), t)).toBe(cards);
    expect(buildSequencerNodes(workflow, cards, [], null, new Map(), t)).toBe(cards);
    const empty = { ...workflow, workflowOrbs: [] } as unknown as WorkflowDefinition;
    expect(buildSequencerNodes(empty, cards, [], null, new Map(), t)).toBe(cards);
  });

  it('places orbs: unanchored first, anchored after their stage, orphaned last', () => {
    const wf = {
      ...workflow,
      workflowOrbs: [
        { workflowId: 'x', mode: 'PARALLEL', placementStage: 'a' },
        { workflowId: 'y', mode: 'PARALLEL' },
        { workflowId: 'z', mode: 'PARALLEL', placementStage: 'gone' },
      ],
    } as WorkflowDefinition;
    const cards = buildAgentCards(wf, [], {}, t);
    const nodes = buildSequencerNodes(wf, cards, [], null, new Map(), t);
    expect(nodes.map((n) => n.id)).toEqual(['orb:1', 'a', 'orb:0', 'b', 'orb:2']);
    expect(nodes[4]?.status).toBe('Waiting (anchor stage removed)');
  });

  describe('orb status matching', () => {
    const orbEvent = (title: string, evidenceRef = '') =>
      ({ agentId: 'workflow-orb', status: 'DONE', title, evidenceRef }) as AgentEvent;
    const names = new Map([['child', 'Child WF']]);
    const build = (mode: string, events: AgentEvent[], runStatus: string | null = null) => {
      const wf = {
        ...workflow,
        agentIds: [],
        workflowOrbs: [{ workflowId: 'child', mode }],
      } as unknown as WorkflowDefinition;
      return buildSequencerNodes(wf, [], events, runStatus, names, t)[0] as {
        status: string;
        statusKind: string;
        childRunId?: string;
        referencedWorkflowName: string;
      };
    };

    it('marks a started parallel child as Done with its run id', () => {
      const node = build('PARALLEL', [
        orbEvent('Started child workflow Child WF (PARALLEL)', 'run://r1'),
      ]);
      expect(node).toMatchObject({
        status: 'Done',
        statusKind: 'done',
        childRunId: 'r1',
        referencedWorkflowName: 'Child WF',
      });
    });

    it('marks a started sequential child as Running while the parent awaits it', () => {
      const node = build(
        'SEQUENTIAL',
        [orbEvent('Started child workflow Child WF (SEQUENTIAL)', 'run://r2')],
        'AWAITING_CHILD_WORKFLOW',
      );
      expect(node).toMatchObject({ status: 'Running', statusKind: 'active', childRunId: 'r2' });
    });

    it('marks a child that did not start as Blocked', () => {
      const node = build('PARALLEL', [orbEvent('Child workflow child did not start: nope')]);
      expect(node).toMatchObject({ status: 'Blocked', statusKind: 'changes' });
    });

    it('marks a refused orb as Blocked', () => {
      const node = build('PARALLEL', [orbEvent('Workflow orb refused: cycle')]);
      expect(node).toMatchObject({ status: 'Blocked', statusKind: 'changes' });
    });

    it('falls back to the workflow id when the name is unknown and no event matches', () => {
      const wf = {
        ...workflow,
        agentIds: [],
        workflowOrbs: [{ workflowId: 'mystery', mode: 'PARALLEL' }],
      } as unknown as WorkflowDefinition;
      const [node] = buildSequencerNodes(wf, [], [], null, new Map(), t);
      expect(node).toMatchObject({ referencedWorkflowName: 'mystery', status: 'Waiting' });
    });

    it('matches each orb to its own event, never reusing a consumed one', () => {
      const wf = {
        ...workflow,
        agentIds: [],
        workflowOrbs: [
          { workflowId: 'child', mode: 'PARALLEL' },
          { workflowId: 'child', mode: 'PARALLEL' },
          { workflowId: 'other', mode: 'PARALLEL' },
        ],
      } as unknown as WorkflowDefinition;
      const events = [
        orbEvent('Unrelated orb note'),
        orbEvent('Started child workflow Child WF (PARALLEL)', 'run://r1'),
        orbEvent('Started child workflow Child WF (PARALLEL)', 'run://r2'),
        orbEvent('Workflow orb refused: cycle'),
        orbEvent('Workflow orb refused: again'),
      ];
      const nodes = buildSequencerNodes(wf, [], events, null, names, t) as {
        status: string;
        childRunId?: string;
      }[];
      expect(nodes.map((n) => n.childRunId)).toEqual(['r1', 'r2', undefined]);
      expect(nodes.map((n) => n.status)).toEqual(['Done', 'Done', 'Blocked']);
    });

    it('skips already-consumed events while looking for a refusal', () => {
      const wf = {
        ...workflow,
        agentIds: [],
        workflowOrbs: [
          { workflowId: 'a', mode: 'PARALLEL' },
          { workflowId: 'b', mode: 'PARALLEL' },
        ],
      } as unknown as WorkflowDefinition;
      const events = [orbEvent('Workflow orb refused: one'), orbEvent('Workflow orb refused: two')];
      const nodes = buildSequencerNodes(wf, [], events, null, names, t);
      expect(nodes.map((n) => n.status)).toEqual(['Blocked', 'Blocked']);
    });
  });
});
