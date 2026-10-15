import type { AgentDefinition } from '../../../../../types';
import { SelectField } from '../../../../../components/FormFields/SelectField/SelectField';
import type { WorkflowDefinition, WorkflowOrb } from '../../../../workflowTypes';
import { fieldErrorClass, fieldHintClass } from '../../../../../ui/Form/Form';

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
        <p id="workflow-orb-error" role="alert" className={fieldErrorClass}>
          {error}
        </p>
      )}
      {orbs.map((orb, index) => (
        <div key={index} className="workflow-orb-row">
          <SelectField
            id={`orb-target-${index}`}
            label="Referenced workflow"
            value={orb.workflowId}
            onChange={(v) => updateOrb(index, { workflowId: v })}
          >
            <option value="">Select a workflow…</option>
            {candidates.map((w) => (
              <option key={w.id} value={w.id}>
                {w.name}
              </option>
            ))}
          </SelectField>
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
          <SelectField
            id={`orb-placement-${index}`}
            label="Placement anchor"
            value={orb.placementStage ?? ''}
            onChange={(v) => updateOrb(index, { placementStage: v })}
          >
            <option value="">Run first (before any agent stage)</option>
            {stages.map((agent) => (
              <option key={agent.id} value={agent.id}>
                {agent.name}
              </option>
            ))}
          </SelectField>
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
      <span className={fieldHintClass}>
        A workflow orb triggers another workflow as part of this one. Up to 5 orbs are allowed (the
        server enforces the actual configured limit).
      </span>
    </div>
  );
}
