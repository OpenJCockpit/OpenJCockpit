import type { AgentDefinition } from '../../../../../types';
import { referenceLabel } from '../../../../linkableAgents';

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
  function toggle(id: string, checked: boolean) {
    onChange(checked ? [...agentIds, id] : agentIds.filter((existing) => existing !== id));
  }

  function move(index: number, delta: number) {
    const target = index + delta;
    if (target < 0 || target >= agentIds.length) return;
    const next = [...agentIds];
    [next[index], next[target]] = [next[target], next[index]];
    onChange(next);
  }

  const sections = [...new Set(availableAgents.map((agent) => agent.role))];
  const byId = new Map(availableAgents.map((agent) => [agent.id, agent]));

  return (
    <div className="workflow-design-field--full">
      <span>Agents</span>
      {availableAgents.length === 0 && (
        <p className="settings-desc">
          No agents available. Add agents to the /agents folder or register them at an A2A provider
          — see Workflow design → Agents.
        </p>
      )}
      {sections.map((section) => (
        <fieldset key={section} className="workflow-agent-picker" aria-label={section}>
          <legend className="workflow-agent-picker__section">{section}</legend>
          {availableAgents
            .filter((agent) => agent.role === section)
            .map((agent) => (
              <label key={agent.id} className="workflow-agent-picker__option">
                <input
                  type="checkbox"
                  checked={agentIds.includes(agent.id)}
                  onChange={(e) => toggle(agent.id, e.target.checked)}
                />
                <span className="workflow-agent-picker__name">{agent.name}</span>
                <span className="settings-desc">{agent.description}</span>
              </label>
            ))}
        </fieldset>
      ))}
      {agentIds.length > 0 && (
        <ol className="workflow-agent-order" aria-label="Execution order">
          {agentIds.map((id, index) => {
            const agent = byId.get(id);
            return (
              <li key={id}>
                <span>
                  {agent ? `${agent.name} (${agent.role})` : referenceLabel(id)}
                  {!agent && <span className="settings-desc"> — not in the catalog</span>}
                </span>
                <button
                  type="button"
                  className="back-button"
                  aria-label={`Move ${agent?.name ?? id} up`}
                  disabled={index === 0}
                  onClick={() => move(index, -1)}
                >
                  ↑
                </button>
                <button
                  type="button"
                  className="back-button"
                  aria-label={`Move ${agent?.name ?? id} down`}
                  disabled={index === agentIds.length - 1}
                  onClick={() => move(index, 1)}
                >
                  ↓
                </button>
                <button
                  type="button"
                  className="back-button"
                  aria-label={`Remove ${agent?.name ?? id}`}
                  onClick={() => toggle(id, false)}
                >
                  ✕
                </button>
              </li>
            );
          })}
        </ol>
      )}
      <span className="settings-desc">
        The workflow runs the linked agents in this order; each agent receives the workflow input
        and the outputs of the agents before it.
      </span>
    </div>
  );
}
