import type { ReactElement } from 'react';
import { fireEvent, screen, within } from '@testing-library/react';
import { renderWithI18n } from '@openjcockpit/i18n/testing';
import { apiResources } from '@openjcockpit/api';
import { describe, expect, it, vi } from 'vitest';
import { linkableAgents } from './linkableAgents';
import { AgentPicker } from './WorkflowDesign/panels/workflows/WorkflowEditor/AgentPicker';
import type { AgentCatalog } from '@openjcockpit/api';

const render = (ui: ReactElement) => renderWithI18n(ui, { resources: apiResources });

const CATALOG: AgentCatalog = {
  local: {
    name: 'Local',
    status: 'OK',
    agents: [
      {
        id: 'triage',
        name: 'Triage',
        path: 'support/triage.md',
        folders: ['support'],
        skills: [],
        tools: [],
        valid: true,
      },
      {
        id: 'broken',
        name: 'broken',
        path: 'broken.md',
        folders: [],
        skills: [],
        tools: [],
        valid: false,
      },
    ],
  },
  providers: [
    {
      name: 'LiteLLM',
      type: 'litellm',
      status: 'OK',
      agents: [
        { id: 'x', name: 'qa-engineer', description: 'Tests', streaming: false, skills: [] },
      ],
    },
  ],
};

describe('linkable agents', () => {
  it('maps valid catalog agents to agent references grouped by section', () => {
    const agents = linkableAgents(CATALOG);
    expect(agents.map((a) => [a.id, a.role])).toEqual([
      ['local:triage', 'Local'],
      ['a2a:litellm:qa-engineer', 'LiteLLM'],
    ]);
  });

  it('defaults a missing description to an empty string', () => {
    const catalog: AgentCatalog = {
      local: {
        ...CATALOG.local,
        agents: [{ ...CATALOG.local.agents[0]! }],
      },
      providers: [
        {
          ...CATALOG.providers[0]!,
          agents: [{ id: 'y', name: 'bare', streaming: false, skills: [] }],
        },
      ],
    };
    expect(linkableAgents(catalog).map((a) => a.description)).toEqual(['', '']);
  });

  it('groups the picker by catalog section and appends linked agents to the execution order', () => {
    const onChange = vi.fn();
    render(
      <AgentPicker
        availableAgents={linkableAgents(CATALOG)}
        agentIds={['a2a:litellm:qa-engineer', 'local:gone']}
        onChange={onChange}
      />,
    );
    expect(
      within(screen.getByRole('group', { name: 'Local' })).getByText('Triage'),
    ).toBeInTheDocument();
    expect(
      within(screen.getByRole('group', { name: 'LiteLLM' })).getByText('qa-engineer'),
    ).toBeInTheDocument();
    expect(screen.getByText(/gone \(Local\)/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('checkbox', { name: /Triage/ }));
    expect(onChange).toHaveBeenCalledWith([
      'a2a:litellm:qa-engineer',
      'local:gone',
      'local:triage',
    ]);
  });
});
