import type { AgentDefinition } from '../../../../../types';
import type { WorkflowDefinition, WorkflowOrb } from '../../../../workflowTypes';

const MAX_ORBS = 5;

interface OrbEditorProps {
  orbs: WorkflowOrb[];
  candidates: WorkflowDefinition[];
  stages: AgentDefinition[];
  error: string | null;
  onChange: (orbs: WorkflowOrb[]) => void;
}

export function OrbEditor({ orbs, candidates, stages, error, onChange }: OrbEditorProps) {
  function addOrb() {
    onChange([
      ...orbs,
      { workflowId: '', mode: undefined as unknown as WorkflowOrb['mode'], placementStage: '' },
    ]);
  }
  function removeOrb(index: number) {
    onChange(orbs.filter((_, i) => i !== index));
  }
  function updateOrb(index: number, patch: Partial<WorkflowOrb>) {
    onChange(orbs.map((orb, i) => (i === index ? { ...orb, ...patch } : orb)));
  }

  return (
    <div className="workflow-design-field--full">
      <span>Workflow orbs</span>
      {error && (
        <p id="workflow-orb-error" role="alert" className="settings-error">
          {error}
        </p>
      )}
      {orbs.map((orb, index) => (
        <div key={index} className="workflow-orb-row">
          <label htmlFor={`orb-target-${index}`}>
            Referenced workflow
            <select
              id={`orb-target-${index}`}
              className="settings-input"
              value={orb.workflowId}
              onChange={(e) => updateOrb(index, { workflowId: e.target.value })}
            >
              <option value="">Select a workflow…</option>
              {candidates.map((w) => (
                <option key={w.id} value={w.id}>
                  {w.name}
                </option>
              ))}
            </select>
          </label>
          <fieldset aria-describedby={error ? 'workflow-orb-error' : undefined}>
            <legend>Execution mode</legend>
            <label>
              <input
                type="radio"
                name={`orb-mode-${index}`}
                value="SEQUENTIAL"
                checked={orb.mode === 'SEQUENTIAL'}
                onChange={() => updateOrb(index, { mode: 'SEQUENTIAL' })}
              />
              Sequential — wait for it to finish before continuing
            </label>
            <label>
              <input
                type="radio"
                name={`orb-mode-${index}`}
                value="PARALLEL"
                checked={orb.mode === 'PARALLEL'}
                onChange={() => updateOrb(index, { mode: 'PARALLEL' })}
              />
              Parallel — start it and continue immediately
            </label>
          </fieldset>
          <label htmlFor={`orb-placement-${index}`}>
            Placement anchor
            <select
              id={`orb-placement-${index}`}
              className="settings-input"
              value={orb.placementStage ?? ''}
              onChange={(e) => updateOrb(index, { placementStage: e.target.value })}
            >
              <option value="">Run first (before any agent stage)</option>
              {stages.map((agent) => (
                <option key={agent.id} value={agent.id}>
                  {agent.name}
                </option>
              ))}
            </select>
          </label>
          <button type="button" className="button button--small" onClick={() => removeOrb(index)}>
            Remove orb
          </button>
        </div>
      ))}
      <button
        type="button"
        className="button button--small"
        onClick={addOrb}
        disabled={orbs.length >= MAX_ORBS}
      >
        + Add orb
      </button>
      <span className="settings-desc">
        A workflow orb triggers another workflow as part of this one. Up to 5 orbs are allowed (the
        server enforces the actual configured limit).
      </span>
    </div>
  );
}
