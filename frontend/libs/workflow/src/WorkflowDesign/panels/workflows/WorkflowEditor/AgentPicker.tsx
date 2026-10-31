import type { AgentDefinition } from '@openjcockpit/api';
import { referenceLabel } from '@openjcockpit/api';
import { useTranslation } from '@openjcockpit/i18n';
import { Button, fieldHintClass } from '@openjcockpit/ui';
import WorkflowDesignStyles from '../../../WorkflowDesign.module.scss';

const REMOVE_GLYPH = '✕';

interface AgentPickerProps {
  /** Linkable catalog agents (id = agent reference key, role = catalog section). */
  availableAgents: AgentDefinition[];
  /** Linked agent references in execution order. */
  agentIds: string[];
  onChange: (agentIds: string[]) => void;
}

/**
 * Links a workflow to agents of the central agent catalog (Workflow design → Agents), grouped by
 * catalog section (Local, then each A2A provider). The linked list below is the execution order:
 * agents run one after another, each receiving the workflow input and the previous outputs.
 */
export function AgentPicker({ availableAgents, agentIds, onChange }: AgentPickerProps) {
  const { t } = useTranslation('workflow');
  const { t: tApi } = useTranslation('api');
  function toggle(id: string, checked: boolean) {
    onChange(checked ? [...agentIds, id] : agentIds.filter((existing) => existing !== id));
  }

  function move(index: number, delta: number) {
    const target = index + delta;
    const next = [...agentIds];
    [next[index], next[target]] = [next[target]!, next[index]!];
    onChange(next);
  }

  const sections = [...new Set(availableAgents.map((agent) => agent.role))];
  const byId = new Map(availableAgents.map((agent) => [agent.id, agent]));

  return (
    <div className={WorkflowDesignStyles.workflowDesignFieldFull}>
      <span>{t('agentPicker.agents')}</span>
      {availableAgents.length === 0 && <p className={fieldHintClass}>{t('agentPicker.empty')}</p>}
      {sections.map((section) => (
        <fieldset
          key={section}
          className={WorkflowDesignStyles.workflowAgentPicker}
          aria-label={section}
        >
          <legend className={WorkflowDesignStyles.workflowAgentPickerSection}>{section}</legend>
          {availableAgents
            .filter((agent) => agent.role === section)
            .map((agent) => (
              <label key={agent.id} className={WorkflowDesignStyles.workflowAgentPickerOption}>
                <input
                  type="checkbox"
                  checked={agentIds.includes(agent.id)}
                  onChange={(e) => toggle(agent.id, e.target.checked)}
                />
                <span className={WorkflowDesignStyles.workflowAgentPickerName}>{agent.name}</span>
                <span className={fieldHintClass}>{agent.description}</span>
              </label>
            ))}
        </fieldset>
      ))}
      {agentIds.length > 0 && (
        <ol className={WorkflowDesignStyles.workflowAgentOrder} aria-label={t('agentPicker.order')}>
          {agentIds.map((id, index) => {
            const agent = byId.get(id);
            return (
              <li key={id}>
                <span>
                  {agent ? `${agent.name} (${agent.role})` : referenceLabel(id, tApi)}
                  {!agent && <span className={fieldHintClass}>{t('agentPicker.missing')}</span>}
                </span>
                <Button
                  type="button"
                  variant="secondary"
                  aria-label={t('agentPicker.moveUp', { name: agent?.name ?? id })}
                  disabled={index === 0}
                  onClick={() => move(index, -1)}
                >
                  ↑
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  aria-label={t('agentPicker.moveDown', { name: agent?.name ?? id })}
                  disabled={index === agentIds.length - 1}
                  onClick={() => move(index, 1)}
                >
                  ↓
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  aria-label={t('agentPicker.removeAgent', { name: agent?.name ?? id })}
                  onClick={() => toggle(id, false)}
                >
                  {REMOVE_GLYPH}
                </Button>
              </li>
            );
          })}
        </ol>
      )}
      <span className={fieldHintClass}>{t('agentPicker.hint')}</span>
    </div>
  );
}
