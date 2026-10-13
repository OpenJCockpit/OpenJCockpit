import './WorkflowDesign.scss';
import { useState } from 'react';
import { AgentsPanel } from './panels/agents/AgentsPanel';
import { GroupsPanel } from './panels/groups/GroupsPanel';
import { SkillsPanel } from './panels/skills/SkillsPanel';
import { SubagentsPanel } from './panels/subagents/SubagentsPanel';
import { WorkflowsPanel } from './panels/workflows/WorkflowsPanel';

type DesignTab = 'workflows' | 'groups' | 'agents' | 'subagents' | 'skills';

export function WorkflowDesign() {
  const [tab, setTab] = useState<DesignTab>('workflows');

  return (
    <section className="workflow-design">
      <nav className="workflow-tab-nav workflow-tab-nav--secondary" aria-label="Design secties">
        <button
          className={`workflow-tab ${tab === 'workflows' ? 'workflow-tab--active' : ''}`}
          onClick={() => setTab('workflows')}
        >
          Workflows
        </button>
        <button
          className={`workflow-tab ${tab === 'groups' ? 'workflow-tab--active' : ''}`}
          onClick={() => setTab('groups')}
        >
          Groups
        </button>
        <button
          className={`workflow-tab ${tab === 'agents' ? 'workflow-tab--active' : ''}`}
          onClick={() => setTab('agents')}
        >
          Agents
        </button>
        <button
          className={`workflow-tab ${tab === 'subagents' ? 'workflow-tab--active' : ''}`}
          onClick={() => setTab('subagents')}
        >
          Subagents
        </button>
        <button
          className={`workflow-tab ${tab === 'skills' ? 'workflow-tab--active' : ''}`}
          onClick={() => setTab('skills')}
        >
          Skills
        </button>
      </nav>

      {tab === 'workflows' && <WorkflowsPanel />}
      {tab === 'groups' && <GroupsPanel />}
      {tab === 'agents' && <AgentsPanel />}
      {tab === 'subagents' && <SubagentsPanel />}
      {tab === 'skills' && <SkillsPanel />}
    </section>
  );
}
