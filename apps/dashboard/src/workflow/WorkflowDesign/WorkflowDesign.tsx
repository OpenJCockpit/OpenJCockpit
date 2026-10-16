import { useState } from 'react';
import { AgentsPanel } from './panels/agents/AgentsPanel';
import { GroupsPanel } from './panels/groups/GroupsPanel';
import { SkillsPanel } from './panels/skills/SkillsPanel';
import { SubagentsPanel } from './panels/subagents/SubagentsPanel';
import { WorkflowsPanel } from './panels/workflows/WorkflowsPanel';
import { Tab, TabNav } from '../../ui/Tabs/Tabs';

type DesignTab = 'workflows' | 'groups' | 'agents' | 'subagents' | 'skills';

export function WorkflowDesign() {
  const [tab, setTab] = useState<DesignTab>('workflows');

  return (
    <section className="workflow-design">
      <TabNav secondary aria-label="Design secties">
        <Tab active={tab === 'workflows'} onClick={() => setTab('workflows')}>
          Workflows
        </Tab>
        <Tab active={tab === 'groups'} onClick={() => setTab('groups')}>
          Groups
        </Tab>
        <Tab active={tab === 'agents'} onClick={() => setTab('agents')}>
          Agents
        </Tab>
        <Tab active={tab === 'subagents'} onClick={() => setTab('subagents')}>
          Subagents
        </Tab>
        <Tab active={tab === 'skills'} onClick={() => setTab('skills')}>
          Skills
        </Tab>
      </TabNav>

      {tab === 'workflows' && <WorkflowsPanel />}
      {tab === 'groups' && <GroupsPanel />}
      {tab === 'agents' && <AgentsPanel />}
      {tab === 'subagents' && <SubagentsPanel />}
      {tab === 'skills' && <SkillsPanel />}
    </section>
  );
}
