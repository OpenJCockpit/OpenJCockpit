import { fireEvent, screen } from '@testing-library/react';
import { render } from '../testUtils/render';
import { describe, expect, it, vi } from 'vitest';
import { WorkflowDesign } from './WorkflowDesign';

vi.mock('./panels/agents/AgentsPanel', () => ({ AgentsPanel: () => <p>agents panel</p> }));
vi.mock('./panels/groups/GroupsPanel', () => ({ GroupsPanel: () => <p>groups panel</p> }));
vi.mock('./panels/skills/SkillsPanel', () => ({ SkillsPanel: () => <p>skills panel</p> }));
vi.mock('./panels/subagents/SubagentsPanel', () => ({
  SubagentsPanel: () => <p>subagents panel</p>,
}));
vi.mock('./panels/workflows/WorkflowsPanel', () => ({
  WorkflowsPanel: () => <p>workflows panel</p>,
}));

describe('WorkflowDesign tabs', () => {
  it('starts on workflows and switches to each panel', () => {
    render(<WorkflowDesign />);
    expect(screen.getByText('workflows panel')).toBeInTheDocument();
    for (const [tab, panel] of [
      ['Groups', 'groups panel'],
      ['Agents', 'agents panel'],
      ['Subagents', 'subagents panel'],
      ['Skills', 'skills panel'],
      ['Workflows', 'workflows panel'],
    ]) {
      fireEvent.click(screen.getByRole('button', { name: tab! }));
      expect(screen.getByText(panel!)).toBeInTheDocument();
    }
  });
});
