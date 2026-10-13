import type { AgentDefinition } from '../../../../../types';

interface AgentPickerProps {
  availableAgents: AgentDefinition[];
  agentIds: string[];
  onChange: (agentIds: string[]) => void;
}

export function AgentPicker({ availableAgents, agentIds, onChange }: AgentPickerProps) {
  function toggle(id: string, checked: boolean) {
    const nextIds = checked ? [...agentIds, id] : agentIds.filter((existing) => existing !== id);
    const sequenceOrderOf = (agentId: string): number => {
      const agent = availableAgents.find((a) => a.id === agentId);
      return agent ? agent.sequenceOrder : Number.MAX_SAFE_INTEGER;
    };
    onChange(
      nextIds
        .map((agentId, index) => ({ agentId, index }))
        .sort(
          (a, b) => sequenceOrderOf(a.agentId) - sequenceOrderOf(b.agentId) || a.index - b.index,
        )
        .map((entry) => entry.agentId),
    );
  }

  const unknownAgentIds = agentIds.filter(
    (id) => !availableAgents.some((agent) => agent.id === id),
  );

  return (
    <div className="workflow-design-field--full">
      <span>Pipeline agents</span>
      <div className="workflow-agent-picker">
        {availableAgents.map((agent) => (
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
      </div>
      <span className="settings-desc">
        Select the pipeline stages this workflow runs, in order. Include "Realisation Agent" to have
        the workflow write real code from the implementation plan and push it to a branch — without
        it, the workflow stops after writing the plan document.
      </span>
      {unknownAgentIds.length > 0 && (
        <p className="settings-desc" role="status">
          Unrecognised pipeline agent id(s) ignored in the picker: {unknownAgentIds.join(', ')}.
          They remain stored and will round-trip on save.
        </p>
      )}
    </div>
  );
}
