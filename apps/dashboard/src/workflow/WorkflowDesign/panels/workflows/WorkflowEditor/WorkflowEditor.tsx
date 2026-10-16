import type { Dispatch, SetStateAction } from 'react';
import type { AgentDefinition } from '../../../../../types';
import type { WorkflowDefinition, WorkflowGroup } from '../../../../workflowTypes';
import { candidateOrbTargets } from '../../../../workflowVisibility';
import { selectedStagesInOrder } from '../../../utils';
import { AgentPicker } from './AgentPicker';
import { SelectField } from '../../../../../components/FormFields/SelectField/SelectField';
import { TextAreaField } from '../../../../../components/FormFields/TextAreaField/TextAreaField';
import { TextField } from '../../../../../components/FormFields/TextField/TextField';
import { EditorForm } from '../../shared/EditorForm';
import { OrbEditor } from './OrbEditor';

interface WorkflowEditorProps {
  isNew: boolean;
  form: WorkflowDefinition;
  setForm: Dispatch<SetStateAction<WorkflowDefinition>>;
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
  function defaultGatePlacement(agentIds: string[]): string {
    const ordered = selectedStagesInOrder(availableAgents, agentIds);
    return ordered.length > 0 ? ordered[ordered.length - 1].id : '';
  }

  const orbCandidates = candidateOrbTargets(form, items, groups);

  return (
    <EditorForm
      title={isNew ? 'New workflow' : 'Edit workflow'}
      saving={saving}
      onSave={onSave}
      onCancel={onCancel}
      saveDescribedBy={saveError ? 'workflow-save-error' : undefined}
    >
      <TextField
        label="Workflow ID"
        value={form.id}
        disabled={!isNew}
        onChange={(v) => setForm((f) => ({ ...f, id: v }))}
        placeholder="Auto-generated if left blank"
        hint={
          isNew
            ? 'Optional — a unique ID is generated automatically if you leave this blank.'
            : undefined
        }
      />
      <TextField
        label="Workflow name"
        value={form.name}
        onChange={(v) => setForm((f) => ({ ...f, name: v }))}
        placeholder="Customer Onboarding"
      />
      <TextField
        label="Project name"
        value={form.projectName}
        onChange={(v) => setForm((f) => ({ ...f, projectName: v }))}
        hint="Optional when the workflow is in a group; required without one."
      />
      <SelectField
        label="Group"
        value={form.groupId ?? ''}
        onChange={(v) => setForm((f) => ({ ...f, groupId: v }))}
        hint="Bundle workflows with a shared meaning. Manage groups in the Groups tab."
      >
        <option value="">No group</option>
        {groups.map((g) => (
          <option key={g.id} value={g.id}>
            {g.name}
          </option>
        ))}
      </SelectField>
      <TextField
        label="Status"
        value={form.status}
        onChange={(v) => setForm((f) => ({ ...f, status: v }))}
        placeholder="ACTIVE"
      />
      <TextAreaField
        label="Description"
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
        Human approval gate
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
        <span className="settings-desc">
          Pauses the run after the chosen agent for a human decision: Accept, Deny, or Accept with
          comments to re-run that agent with the feedback.
        </span>
      </label>
      {form.approvalGate?.enabled && (
        <div className="workflow-design-field--full">
          <SelectField
            id="approval-gate-placement"
            label="Gate placement"
            aria-describedby="approval-gate-note"
            value={form.approvalGate.placementStage}
            onChange={(v) =>
              setForm((f) => ({
                ...f,
                approvalGate: { enabled: true, placementStage: v },
              }))
            }
          >
            {selectedStagesInOrder(availableAgents, form.agentIds).map((agent) => (
              <option key={agent.id} value={agent.id}>
                {agent.name}
              </option>
            ))}
          </SelectField>
          <p id="approval-gate-note" className="settings-desc approval-gate-note">
            Up to 3 feedback iterations are available at this gate: the chosen agent re-runs with
            the reviewer's comments and its previous output.
          </p>
        </div>
      )}
      <OrbEditor
        orbs={form.workflowOrbs ?? []}
        candidates={orbCandidates}
        stages={selectedStagesInOrder(availableAgents, form.agentIds)}
        error={orbError}
        onChange={(workflowOrbs) => setForm((f) => ({ ...f, workflowOrbs }))}
      />
      <label>
        Ask for prompt on start
        <input
          type="checkbox"
          checked={form.promptRequired ?? false}
          onChange={(e) => setForm((f) => ({ ...f, promptRequired: e.target.checked }))}
        />
        <span className="settings-desc">
          Shows a themed prompt dialog when this workflow is started (e.g. spec creation from prompt
          + RAG).
        </span>
      </label>
      <TextAreaField
        label="Prompt instructions (prefix)"
        wide
        rows={3}
        value={form.promptInstructions ?? ''}
        onChange={(v) => setForm((f) => ({ ...f, promptInstructions: v }))}
        placeholder="Instructions prepended to the user's prompt, e.g. create a branch, create the spec from the template and push it for a pull request."
        hint="Prepended to the user's prompt when the workflow starts."
      />
      <label>
        Dashboard button trigger
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
        label="Customer ID (execution)"
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
