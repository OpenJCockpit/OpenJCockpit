import type { Dispatch, SetStateAction } from 'react';
import { candidateOrbTargets } from '@openjcockpit/api';
import type { AgentDefinition, WorkflowDefinition, WorkflowGroup } from '@openjcockpit/api';
import { useTranslation } from '@openjcockpit/i18n';
import { selectedStagesInOrder } from '../../../utils';
import { AgentPicker } from './AgentPicker';
import { SelectField, TextAreaField, TextField, fieldHintClass, cx } from '@openjcockpit/ui';
import { EditorForm } from '../../shared/EditorForm';
import { OrbEditor } from './OrbEditor';
import type { WorkflowDraft } from './OrbEditor';
import WorkflowDesignStyles from '../../../WorkflowDesign.module.scss';

interface WorkflowEditorProps {
  isNew: boolean;
  form: WorkflowDraft;
  setForm: Dispatch<SetStateAction<WorkflowDraft>>;
  saving: boolean;
  onSave: () => void;
  onCancel: () => void;
  groups: WorkflowGroup[];
  items: WorkflowDefinition[];
  availableAgents: AgentDefinition[];
  orbError: string | null;
  saveError: string | null;
}

export function WorkflowEditor({
  isNew,
  form,
  setForm,
  saving,
  onSave,
  onCancel,
  groups,
  items,
  availableAgents,
  orbError,
  saveError,
}: WorkflowEditorProps) {
  const { t } = useTranslation('workflow');
  const { t: tApi } = useTranslation('api');
  function defaultGatePlacement(agentIds: string[]): string {
    const ordered = selectedStagesInOrder(availableAgents, agentIds, tApi);
    return ordered[ordered.length - 1]?.id ?? '';
  }

  const orbCandidates = candidateOrbTargets(form, items, groups);

  return (
    <EditorForm
      title={isNew ? t('workflowEditor.new') : t('workflowEditor.edit')}
      saving={saving}
      onSave={onSave}
      onCancel={onCancel}
      saveDescribedBy={saveError ? 'workflow-save-error' : undefined}
    >
      <TextField
        label={t('workflowEditor.id')}
        value={form.id}
        disabled={!isNew}
        onChange={(v) => setForm((f) => ({ ...f, id: v }))}
        placeholder={t('workflowEditor.idPlaceholder')}
        hint={isNew ? t('workflowEditor.idHint') : undefined}
      />
      <TextField
        label={t('workflowEditor.name')}
        value={form.name}
        onChange={(v) => setForm((f) => ({ ...f, name: v }))}
        placeholder={t('workflowEditor.namePlaceholder')}
      />
      <TextField
        label={t('workflowEditor.projectName')}
        value={form.projectName}
        onChange={(v) => setForm((f) => ({ ...f, projectName: v }))}
        hint={t('workflowEditor.projectHint')}
      />
      <SelectField
        label={t('workflowEditor.group')}
        value={form.groupId ?? ''}
        onChange={(v) => setForm((f) => ({ ...f, groupId: v }))}
        hint={t('workflowEditor.groupHint')}
      >
        <option value="">{t('workflowEditor.noGroup')}</option>
        {groups.map((g) => (
          <option key={g.id} value={g.id}>
            {g.name}
          </option>
        ))}
      </SelectField>
      <TextField
        label={t('workflowEditor.status')}
        value={form.status}
        onChange={(v) => setForm((f) => ({ ...f, status: v }))}
        placeholder="ACTIVE"
      />
      <TextAreaField
        label={t('workflowEditor.description')}
        wide
        rows={2}
        value={form.description}
        onChange={(v) => setForm((f) => ({ ...f, description: v }))}
      />
      <AgentPicker
        availableAgents={availableAgents}
        agentIds={form.agentIds}
        onChange={(agentIds) => setForm((f) => ({ ...f, agentIds }))}
      />
      <label>
        {t('workflowEditor.approvalGate')}
        <input
          type="checkbox"
          checked={form.approvalGate?.enabled ?? false}
          onChange={(e) => {
            const enabled = e.target.checked;
            setForm((f) => ({
              ...f,
              approvalGate: {
                enabled,
                placementStage: enabled
                  ? f.approvalGate?.placementStage || defaultGatePlacement(f.agentIds)
                  : (f.approvalGate?.placementStage ?? ''),
              },
            }));
          }}
        />
        <span className={fieldHintClass}>{t('workflowEditor.approvalGateHint')}</span>
      </label>
      {form.approvalGate?.enabled && (
        <div className={WorkflowDesignStyles.workflowDesignFieldFull}>
          <SelectField
            id="approval-gate-placement"
            label={t('workflowEditor.gatePlacement')}
            aria-describedby="approval-gate-note"
            value={form.approvalGate.placementStage}
            onChange={(v) =>
              setForm((f) => ({
                ...f,
                approvalGate: { enabled: true, placementStage: v },
              }))
            }
          >
            {selectedStagesInOrder(availableAgents, form.agentIds, tApi).map((agent) => (
              <option key={agent.id} value={agent.id}>
                {agent.name}
              </option>
            ))}
          </SelectField>
          <p
            id="approval-gate-note"
            className={cx(fieldHintClass, WorkflowDesignStyles.approvalGateNote)}
          >
            {t('workflowEditor.gateNote')}
          </p>
        </div>
      )}
      <OrbEditor
        orbs={form.workflowOrbs ?? []}
        candidates={orbCandidates}
        stages={selectedStagesInOrder(availableAgents, form.agentIds, tApi)}
        error={orbError}
        onChange={(workflowOrbs) => setForm((f) => ({ ...f, workflowOrbs }))}
      />
      <label>
        {t('workflowEditor.promptRequired')}
        <input
          type="checkbox"
          checked={form.promptRequired ?? false}
          onChange={(e) => setForm((f) => ({ ...f, promptRequired: e.target.checked }))}
        />
        <span className={fieldHintClass}>{t('workflowEditor.promptRequiredHint')}</span>
      </label>
      <TextAreaField
        label={t('workflowEditor.promptInstructions')}
        wide
        rows={3}
        value={form.promptInstructions ?? ''}
        onChange={(v) => setForm((f) => ({ ...f, promptInstructions: v }))}
        placeholder={t('workflowEditor.promptInstructionsPlaceholder')}
        hint={t('workflowEditor.promptInstructionsHint')}
      />
      <label>
        {t('workflowEditor.dashboardTrigger')}
        <input
          type="checkbox"
          checked={form.trigger?.dashboardButtonEnabled ?? false}
          onChange={(e) =>
            setForm((f) => ({
              ...f,
              trigger: {
                ...(f.trigger ?? {
                  hermesSignalEnabled: false,
                  fileDeliveryEnabled: false,
                  dashboardButtonEnabled: false,
                }),
                dashboardButtonEnabled: e.target.checked,
              },
            }))
          }
        />
      </label>
      <TextField
        label={t('workflowEditor.customerId')}
        value={form.execution?.customerId ?? ''}
        onChange={(v) =>
          setForm((f) => ({
            ...f,
            execution: { ...(f.execution ?? {}), customerId: v },
          }))
        }
      />
    </EditorForm>
  );
}
