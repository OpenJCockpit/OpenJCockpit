import { screen } from '@testing-library/react';
import { render } from '../testUtils/render';
import { describe, expect, it } from 'vitest';
import type { AgentEvent } from '@openjcockpit/api';
import { AgentLogConsole } from './AgentLogConsole';

function event(overrides: Partial<AgentEvent>): AgentEvent {
  return {
    timestamp: '2026-01-01T10:20:30Z',
    agentId: 'requirement',
    title: 'Did a thing',
    status: 'done',
    evidenceRef: 'ref',
    ...overrides,
  };
}

describe('AgentLogConsole', () => {
  it('shows an idle label and a waiting placeholder without events', () => {
    render(<AgentLogConsole events={[]} agentName={null} isActive={false} />);
    expect(screen.getByText('AGENT LOG // IDLE')).toBeInTheDocument();
    expect(screen.getByLabelText('Agent execution log')).toHaveValue(
      '// waiting for agent output…',
    );
  });

  it('names the active agent and renders one line per event, keeping unparseable timestamps', () => {
    const { container } = render(
      <AgentLogConsole
        events={[event({}), event({ timestamp: 'not-a-date', title: 'Second', status: 'active' })]}
        agentName="Requirement"
        isActive
      />,
    );
    expect(screen.getByText('AGENT LOG // Requirement')).toBeInTheDocument();
    const lines = screen
      .getByLabelText<HTMLTextAreaElement>('Agent execution log')
      .value.split('\n');
    expect(lines).toHaveLength(2);
    expect(lines[0]).toMatch(/^\[\d{2}:\d{2}:\d{2}\] done — Did a thing$/);
    expect(lines[1]).toBe('[not-a-date] active — Second');
    expect(container.firstElementChild).toHaveAttribute('data-active', '');
  });
});
