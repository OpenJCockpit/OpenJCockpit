import type { AgentDefinition, WorkflowDefinition, WorkflowOrb } from '@openjcockpit/api';
import { useTranslation } from '@openjcockpit/i18n';
import { SelectField, fieldErrorClass, fieldHintClass, Button } from '@openjcockpit/ui';
import WorkflowDesignStyles from '../../../WorkflowDesign.module.scss';

const MAX_ORBS = 5;

// An orb being edited: the execution mode is unset until the user picks one.
export type OrbDraft = Omit<WorkflowOrb, 'mode'> & { mode?: WorkflowOrb['mode'] };

// A workflow being edited; its orbs may still be incomplete.
export type WorkflowDraft = Omit<WorkflowDefinition, 'workflowOrbs'> & {
  workflowOrbs?: OrbDraft[];
};

// Rows are editable and removable, so they need a key that survives reordering. Orbs carry no id of
// their own, so each draft object gets a client-side key kept off the payload; edits inherit it.
const orbKeys = new WeakMap<OrbDraft, string>();

function orbKey(orb: OrbDraft): string {
  let key = orbKeys.get(orb);
  if (!key) {
    key = crypto.randomUUID();
    orbKeys.set(orb, key);
  }
  return key;
}

interface OrbEditorProps {
  orbs: OrbDraft[];
  candidates: WorkflowDefinition[];
  stages: AgentDefinition[];
  error: string | null;
  onChange: (orbs: OrbDraft[]) => void;
}

export function OrbEditor({ orbs, candidates, stages, error, onChange }: OrbEditorProps) {
  const { t } = useTranslation('workflow');
  function addOrb() {
    onChange([...orbs, { workflowId: '', placementStage: '' }]);
  }
  function removeOrb(index: number) {
    onChange(orbs.filter((_, i) => i !== index));
  }
  function updateOrb(index: number, patch: Partial<OrbDraft>) {
    onChange(
      orbs.map((orb, i) => {
        if (i !== index) return orb;
        const updated = { ...orb, ...patch };
        orbKeys.set(updated, orbKey(orb));
        return updated;
      }),
    );
  }

  return (
    <div className={WorkflowDesignStyles.workflowDesignFieldFull}>
      <span>{t('orbEditor.title')}</span>
      {error && (
        <p id="workflow-orb-error" role="alert" className={fieldErrorClass}>
          {error}
        </p>
      )}
      {orbs.map((orb, index) => (
        <div key={orbKey(orb)} className="workflow-orb-row">
          <SelectField
            id={`orb-target-${index}`}
            label={t('orbEditor.referenced')}
            value={orb.workflowId}
            onChange={(v) => updateOrb(index, { workflowId: v })}
            options={[
              { value: '', label: t('orbEditor.select') },
              ...candidates.map((w) => ({ value: w.id, label: w.name })),
            ]}
          />
          <fieldset aria-describedby={error ? 'workflow-orb-error' : undefined}>
            <legend>{t('orbEditor.mode')}</legend>
            <label>
              <input
                type="radio"
                name={`orb-mode-${index}`}
                value="SEQUENTIAL"
                checked={orb.mode === 'SEQUENTIAL'}
                onChange={() => updateOrb(index, { mode: 'SEQUENTIAL' })}
              />
              {t('orbEditor.sequential')}
            </label>
            <label>
              <input
                type="radio"
                name={`orb-mode-${index}`}
                value="PARALLEL"
                checked={orb.mode === 'PARALLEL'}
                onChange={() => updateOrb(index, { mode: 'PARALLEL' })}
              />
              {t('orbEditor.parallel')}
            </label>
          </fieldset>
          <SelectField
            id={`orb-placement-${index}`}
            label={t('orbEditor.placement')}
            value={orb.placementStage ?? ''}
            onChange={(v) => updateOrb(index, { placementStage: v })}
            options={[
              { value: '', label: t('orbEditor.runFirst') },
              ...stages.map((agent) => ({ value: agent.id, label: agent.name })),
            ]}
          />
          <Button type="button" size="sm" onClick={() => removeOrb(index)}>
            {t('orbEditor.remove')}
          </Button>
        </div>
      ))}
      <Button type="button" size="sm" onClick={addOrb} disabled={orbs.length >= MAX_ORBS}>
        {t('orbEditor.add')}
      </Button>
      <span className={fieldHintClass}>{t('orbEditor.hint', { max: MAX_ORBS })}</span>
    </div>
  );
}
