import { useState } from 'react';
import { useTranslation } from '@openjcockpit/i18n';
import { AgentsPanel } from './panels/agents/AgentsPanel';
import { GroupsPanel } from './panels/groups/GroupsPanel';
import { SkillsPanel } from './panels/skills/SkillsPanel';
import { SubagentsPanel } from './panels/subagents/SubagentsPanel';
import { WorkflowsPanel } from './panels/workflows/WorkflowsPanel';
import { Tab, TabNav } from '@openjcockpit/ui';

type DesignTab = 'workflows' | 'groups' | 'agents' | 'subagents' | 'skills';

export function WorkflowDesign() {
  const { t } = useTranslation('workflow');
  const [tab, setTab] = useState<DesignTab>('workflows');

  return (
    <section className="workflow-design">
      <TabNav secondary aria-label={t('design.tabsLabel')}>
        <Tab active={tab === 'workflows'} onClick={() => setTab('workflows')}>
          {t('design.tabs.workflows')}
        </Tab>
        <Tab active={tab === 'groups'} onClick={() => setTab('groups')}>
          {t('design.tabs.groups')}
        </Tab>
        <Tab active={tab === 'agents'} onClick={() => setTab('agents')}>
          {t('design.tabs.agents')}
        </Tab>
        <Tab active={tab === 'subagents'} onClick={() => setTab('subagents')}>
          {t('design.tabs.subagents')}
        </Tab>
        <Tab active={tab === 'skills'} onClick={() => setTab('skills')}>
          {t('design.tabs.skills')}
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
