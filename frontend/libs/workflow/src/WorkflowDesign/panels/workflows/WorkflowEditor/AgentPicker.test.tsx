import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { AgentDefinition } from '@openjcockpit/api';
import { AgentPicker } from './AgentPicker';

const agent = (id: string, name: string, role: string): AgentDefinition => ({
  id,
  name,
  role,
  description: `${name} description`,
  sequenceOrder: 0,
  inputType: '',
  outputType: '',
});
const AGENTS = [
  agent('a', 'Alpha', 'Local'),
  agent('b', 'Beta', 'Local'),
  agent('c', 'Gamma', 'A2A'),
];

function setup(agentIds: string[], available = AGENTS) {
  const onChange = vi.fn();
  render(<AgentPicker availableAgents={available} agentIds={agentIds} onChange={onChange} />);
  return onChange;
}

describe('AgentPicker', () => {
  it('explains an empty catalog and hides the order list', () => {
    setup([], []);
    expect(screen.getByText(/No agents available/)).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: 'Execution order' })).not.toBeInTheDocument();
  });

  it('groups agents by catalog section', () => {
    setup([]);
    expect(
      within(screen.getByRole('group', { name: 'Local' })).getAllByRole('checkbox'),
    ).toHaveLength(2);
    expect(
      within(screen.getByRole('group', { name: 'A2A' })).getAllByRole('checkbox'),
    ).toHaveLength(1);
  });

  it('appends a checked agent and removes an unchecked one', () => {
    const onChange = setup(['a']);
    fireEvent.click(screen.getByRole('checkbox', { name: /Beta/ }));
    expect(onChange).toHaveBeenCalledWith(['a', 'b']);
    fireEvent.click(screen.getByRole('checkbox', { name: /Alpha/ }));
    expect(onChange).toHaveBeenLastCalledWith([]);
  });

  it('moves agents up and down', () => {
    const onChange = setup(['a', 'b']);
    fireEvent.click(screen.getByRole('button', { name: 'Move Alpha down' }));
    expect(onChange).toHaveBeenLastCalledWith(['b', 'a']);
    fireEvent.click(screen.getByRole('button', { name: 'Move Beta up' }));
    expect(onChange).toHaveBeenLastCalledWith(['b', 'a']);
  });

  it('disables moves past either end', () => {
    setup(['a', 'b']);
    expect(screen.getByRole('button', { name: 'Move Alpha up' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Move Beta down' })).toBeDisabled();
  });

  it('removes a linked agent from the order list', () => {
    const onChange = setup(['a', 'b']);
    fireEvent.click(screen.getByRole('button', { name: 'Remove Alpha' }));
    expect(onChange).toHaveBeenCalledWith(['b']);
  });

  it('flags a linked agent that is no longer in the catalog', () => {
    setup(['ghost']);
    expect(screen.getByText(/not in the catalog/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Move ghost up' })).toBeInTheDocument();
  });
});
