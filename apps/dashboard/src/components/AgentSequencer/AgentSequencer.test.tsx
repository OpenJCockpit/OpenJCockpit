import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { AgentSequencer } from './AgentSequencer';
import type { AgentCard, AgentDefinition, AgentEvent, OrbCard } from '../../types';

const agents: AgentCard[] = [
  {
    kind: 'agent',
    id: 'requirement',
    name: 'Requirement',
    description: 'Gathers requirements',
    status: 'Waiting',
    statusKind: 'waiting',
  },
  {
    kind: 'agent',
    id: 'impact',
    name: 'Impact',
    description: 'Assesses impact',
    status: 'Waiting',
    statusKind: 'waiting',
  },
];

const definitions: AgentDefinition[] = [
  {
    id: 'requirement',
    name: 'Requirement',
    description: 'x',
    role: 'x',
    sequenceOrder: 1,
    inputType: 'x',
    outputType: 'x',
  },
  {
    id: 'impact',
    name: 'Impact',
    description: 'x',
    role: 'x',
    sequenceOrder: 2,
    inputType: 'x',
    outputType: 'x',
  },
];

function nodeFor(name: string): HTMLElement {
  const node = screen.getByText(name).closest('.sequencer-node');
  if (!node) throw new Error(`no .sequencer-node ancestor for "${name}"`);
  return node as HTMLElement;
}

describe('AgentSequencer', () => {
  it('shows no tooltip before hover', () => {
    render(<AgentSequencer nodes={agents} definitions={definitions} />);
    expect(screen.queryByRole('tooltip')).toBeNull();
  });

  it('mouseEnter on a node shows its status label and description, portaled outside .agent-sequencer', () => {
    const { container } = render(<AgentSequencer nodes={agents} definitions={definitions} />);
    fireEvent.mouseEnter(nodeFor('Requirement'));

    const tooltip = screen.getByRole('tooltip');
    expect(tooltip).toHaveTextContent('Waiting');
    expect(tooltip).toHaveTextContent('Gathers requirements');
    expect(tooltip.parentElement).toBe(document.body);
    const sequencer = container.querySelector('.agent-sequencer');
    expect(sequencer?.contains(tooltip)).toBe(false);
  });

  it('shows the latest event title instead of the description when one exists', () => {
    const latestEvents: Record<string, AgentEvent> = {
      requirement: {
        timestamp: 't',
        agentId: 'requirement',
        title: 'Drafted acceptance criteria',
        status: 'done',
        evidenceRef: 'ref',
      },
    };
    render(<AgentSequencer nodes={agents} definitions={definitions} latestEvents={latestEvents} />);
    fireEvent.mouseEnter(nodeFor('Requirement'));

    const tooltip = screen.getByRole('tooltip');
    expect(tooltip).toHaveTextContent('Drafted acceptance criteria');
    expect(tooltip).not.toHaveTextContent('Gathers requirements');
  });

  it('mouseLeave hides the tooltip; hovering a second node shows exactly one, bound to it', () => {
    render(<AgentSequencer nodes={agents} definitions={definitions} />);
    const first = nodeFor('Requirement');
    fireEvent.mouseEnter(first);
    expect(screen.getByRole('tooltip')).toBeInTheDocument();
    fireEvent.mouseLeave(first);
    expect(screen.queryByRole('tooltip')).toBeNull();

    fireEvent.mouseEnter(nodeFor('Impact'));
    const tooltips = screen.getAllByRole('tooltip');
    expect(tooltips).toHaveLength(1);
    expect(tooltips[0]).toHaveTextContent('Assesses impact');
  });

  it('focusing a node shows the tooltip; blur hides it (D-3)', () => {
    render(<AgentSequencer nodes={agents} definitions={definitions} />);
    const node = nodeFor('Requirement');
    fireEvent.focus(node);
    expect(screen.getByRole('tooltip')).toBeInTheDocument();
    fireEvent.blur(node);
    expect(screen.queryByRole('tooltip')).toBeNull();
  });

  it('drag reorder still works through the Tooltip wrapper, and starting a drag closes an open tooltip', () => {
    const { container } = render(<AgentSequencer nodes={agents} definitions={definitions} />);
    const requirementNode = nodeFor('Requirement');
    fireEvent.mouseEnter(requirementNode);
    expect(screen.getByRole('tooltip')).toBeInTheDocument();

    fireEvent.dragStart(requirementNode);
    expect(screen.queryByRole('tooltip')).toBeNull();

    fireEvent.dragOver(nodeFor('Impact'));
    fireEvent.dragEnd(requirementNode);

    const names = Array.from(container.querySelectorAll('.sequencer-meta strong')).map(
      (el) => el.textContent,
    );
    expect(names).toEqual(['Impact', 'Requirement']);
  });

  it('renders an orb node with an accessible name and visible status text, and no false interactive affordance', () => {
    const orbNode: OrbCard = {
      kind: 'orb',
      id: 'orb-1',
      referencedWorkflowId: 'wf-child',
      referencedWorkflowName: 'Spec creation',
      mode: 'SEQUENTIAL',
      status: 'RUNNING',
      statusKind: 'active',
    };
    render(<AgentSequencer nodes={[...agents, orbNode]} definitions={definitions} />);
    const orbEl = screen.getByLabelText('Workflow orb: Spec creation, sequential');
    expect(orbEl).toHaveAttribute('role', 'status');
    expect(orbEl).not.toHaveAttribute('tabindex');
    expect(orbEl.textContent).toContain('RUNNING');
  });

  it('keeps an orb in the middle of the sequence when definitions are present (N-4 regression)', () => {
    const agent1: AgentCard = {
      kind: 'agent',
      id: 'agent-a',
      name: 'Agent A',
      description: 'First agent',
      status: 'Waiting',
      statusKind: 'waiting',
    };
    const orbNode: OrbCard = {
      kind: 'orb',
      id: 'orb:0',
      referencedWorkflowId: 'wf-child',
      referencedWorkflowName: 'Child Workflow',
      mode: 'SEQUENTIAL',
      status: 'RUNNING',
      statusKind: 'active',
    };
    const agent2: AgentCard = {
      kind: 'agent',
      id: 'agent-b',
      name: 'Agent B',
      description: 'Second agent',
      status: 'Waiting',
      statusKind: 'waiting',
    };
    const defs: AgentDefinition[] = [
      {
        id: 'agent-a',
        name: 'Agent A',
        description: 'x',
        role: 'x',
        sequenceOrder: 1,
        inputType: 'x',
        outputType: 'x',
      },
      {
        id: 'agent-b',
        name: 'Agent B',
        description: 'x',
        role: 'x',
        sequenceOrder: 2,
        inputType: 'x',
        outputType: 'x',
      },
    ];
    const { container } = render(
      <AgentSequencer nodes={[agent1, orbNode, agent2]} definitions={defs} />,
    );
    const names = Array.from(container.querySelectorAll('.sequencer-meta strong')).map(
      (el) => el.textContent,
    );
    expect(names).toEqual(['Agent A', 'Child Workflow', 'Agent B']);
  });
});
