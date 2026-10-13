import type { Dispatch, SetStateAction } from 'react';
import type { AgentDefinition } from '../../../../../types';
import type { WorkflowDefinition, WorkflowGroup } from '../../../../workflowTypes';
import { candidateOrbTargets } from '../../../../workflowVisibility';
import { selectedStagesInOrder } from '../../../utils';
import { AgentPicker } from './AgentPicker';
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
    if (ordered.some((agent) => agent.id === 'realisation')) return 'realisation';
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
      <label>
        Workflow ID
        <input
          className="settings-input"
          value={form.id}
          disabled={!isNew}
          onChange={(e) => setForm((f) => ({ ...f, id: e.target.value }))}
          placeholder="Auto-generated if left blank"
        />
        {isNew && (
          <span className="settings-desc">
            Optional — a unique ID is generated automatically if you leave this blank.
          </span>
        )}
      </label>
      <label>
        Workflow name
        <input
          className="settings-input"
          value={form.name}
          onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
          placeholder="Customer Onboarding"
        />
      </label>
      <label>
        Project name
        <input
          className="settings-input"
          value={form.projectName}
          onChange={(e) => setForm((f) => ({ ...f, projectName: e.target.value }))}
        />
        <span className="settings-desc">
          Optional when the workflow is in a group; required without one.
        </span>
      </label>
      <label>
        Group
        <select
          className="settings-input"
          value={form.groupId ?? ''}
          onChange={(e) => setForm((f) => ({ ...f, groupId: e.target.value }))}
        >
          <option value="">No group</option>
          {groups.map((g) => (
            <option key={g.id} value={g.id}>
              {g.name}
            </option>
          ))}
        </select>
        <span className="settings-desc">
          Bundle workflows with a shared meaning. Manage groups in the Groups tab.
        </span>
      </label>
      <label>
        Status
        <input
          className="settings-input"
          value={form.status}
          onChange={(e) => setForm((f) => ({ ...f, status: e.target.value }))}
          placeholder="ACTIVE"
        />
      </label>
      <label className="workflow-design-field--full">
        Description
        <textarea
          className="settings-input"
          rows={2}
          value={form.description}
          onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
        />
      </label>
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
          Pauses the run after the chosen stage for a human Accept/Deny decision (and, only after
          Realisation, Accept with comments to steer a re-run).
        </span>
      </label>
      {form.approvalGate?.enabled && (
        <div className="workflow-design-field--full">
          <label htmlFor="approval-gate-placement">
            Gate placement
            <select
              id="approval-gate-placement"
              className="settings-input"
              aria-describedby="approval-gate-note"
              value={form.approvalGate.placementStage}
              onChange={(e) =>
                setForm((f) => ({
                  ...f,
                  approvalGate: { enabled: true, placementStage: e.target.value },
                }))
              }
            >
              {selectedStagesInOrder(availableAgents, form.agentIds).map((agent) => (
                <option key={agent.id} value={agent.id}>
                  {agent.name}
                </option>
              ))}
            </select>
          </label>
          <p id="approval-gate-note" className="settings-desc approval-gate-note">
            {form.approvalGate.placementStage === 'realisation'
              ? 'Up to 3 feedback iterations are available at this gate.'
              : 'This gate offers Accept and Deny only. The feedback and re-run capability requires a realisation placement.'}
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
      <label className="workflow-design-field--full">
        Prompt instructions (prefix)
        <textarea
          className="settings-input"
          rows={3}
          value={form.promptInstructions ?? ''}
          onChange={(e) => setForm((f) => ({ ...f, promptInstructions: e.target.value }))}
          placeholder="Instructions prepended to the user's prompt, e.g. create a branch, create the spec from the template and push it for a pull request."
        />
        <span className="settings-desc">
          Prepended to the user's prompt when the workflow starts.
        </span>
      </label>
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
      <label>
        Customer ID (execution)
        <input
          className="settings-input"
          value={form.execution?.customerId ?? ''}
          onChange={(e) =>
            setForm((f) => ({
              ...f,
              execution: { ...(f.execution ?? {}), customerId: e.target.value },
            }))
          }
        />
      </label>
    </EditorForm>
  );
}
