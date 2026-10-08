import './WorkflowDesign.scss';
import { useEffect, useMemo, useRef, useState } from 'react';
import { fetchAgentDefinitions } from '../../api';
import type { AgentDefinition } from '../../types';
import {
  createAgentSpec,
  createSkillSpec,
  createSubagentSpec,
  createWorkflow,
  createWorkflowGroup,
  deleteAgentSpec,
  deleteSkillSpec,
  deleteSubagentSpec,
  deleteWorkflow,
  deleteWorkflowGroup,
  exportWorkflows,
  generateAgentSpec,
  generateSkillSpec,
  generateSubagentSpec,
  getSkillCatalog,
  importWorkflows,
  listAgentSpecs,
  listSubagentSpecs,
  listWorkflowGroups,
  listWorkflows,
  updateAgentSpec,
  updateSkillSpec,
  updateSubagentSpec,
  updateWorkflow,
  updateWorkflowGroup,
} from '../workflowApi';
import type {
  AgentSpec,
  ExternalSkill,
  SkillCatalog,
  SkillCatalogSourceStatus,
  SkillSpec,
  SubagentSpec,
  WorkflowDefinition,
  WorkflowExportBundle,
  WorkflowGroup,
  WorkflowOrb,
} from '../workflowTypes';
import { candidateOrbTargets } from '../workflowVisibility';

type DesignTab = 'workflows' | 'groups' | 'agents' | 'subagents' | 'skills';

function toList(value: string): string[] {
  return value
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
}

const EMPTY_WORKFLOW: WorkflowDefinition = {
  id: '',
  name: '',
  projectName: '',
  groupId: '',
  description: '',
  agentIds: [],
  subagentNames: [],
  skillNames: [],
  mcpTools: [],
  trigger: { dashboardButtonEnabled: true, hermesSignalEnabled: false, fileDeliveryEnabled: false },
  execution: { customerId: '', repositoryUrl: '', timeoutSeconds: 600 },
  approvalGate: undefined,
  status: 'ACTIVE',
};

const EMPTY_GROUP: WorkflowGroup = { id: '', name: '', description: '', projectName: '' };

const EMPTY_AGENT: AgentSpec = {
  name: '',
  description: '',
  role: '',
  instructions: '',
  subagentNames: [],
  skillNames: [],
  mcpTools: [],
};

const EMPTY_SUBAGENT: SubagentSpec = {
  name: '',
  parentAgent: '',
  description: '',
  responsibilities: '',
  instructions: '',
  skillNames: [],
  mcpTools: [],
};

const EMPTY_SKILL: SkillSpec = {
  name: '',
  description: '',
  inputContract: '',
  outputContract: '',
  executionInstructions: '',
  mcpTools: [],
  policyNotes: '',
};

export function WorkflowDesign() {
  const [tab, setTab] = useState<DesignTab>('workflows');

  return (
    <section className="workflow-design">
      <nav className="workflow-tab-nav workflow-tab-nav--secondary" aria-label="Design secties">
        <button
          className={`workflow-tab ${tab === 'workflows' ? 'workflow-tab--active' : ''}`}
          onClick={() => setTab('workflows')}
        >
          Workflows
        </button>
        <button
          className={`workflow-tab ${tab === 'groups' ? 'workflow-tab--active' : ''}`}
          onClick={() => setTab('groups')}
        >
          Groups
        </button>
        <button
          className={`workflow-tab ${tab === 'agents' ? 'workflow-tab--active' : ''}`}
          onClick={() => setTab('agents')}
        >
          Agents
        </button>
        <button
          className={`workflow-tab ${tab === 'subagents' ? 'workflow-tab--active' : ''}`}
          onClick={() => setTab('subagents')}
        >
          Subagents
        </button>
        <button
          className={`workflow-tab ${tab === 'skills' ? 'workflow-tab--active' : ''}`}
          onClick={() => setTab('skills')}
        >
          Skills
        </button>
      </nav>

      {tab === 'workflows' && <WorkflowsPanel />}
      {tab === 'groups' && <GroupsPanel />}
      {tab === 'agents' && <AgentsPanel />}
      {tab === 'subagents' && <SubagentsPanel />}
      {tab === 'skills' && <SkillsPanel />}
    </section>
  );
}

function readFileText(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsText(file);
  });
}

function downloadWorkflowBundle(bundle: WorkflowExportBundle) {
  const blob = new Blob([JSON.stringify(bundle, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = 'workflow-export.json';
  link.click();
  URL.revokeObjectURL(url);
}

function WorkflowsPanel() {
  const [items, setItems] = useState<WorkflowDefinition[]>([]);
  const [groups, setGroups] = useState<WorkflowGroup[]>([]);
  const [availableAgents, setAvailableAgents] = useState<AgentDefinition[]>([]);
  const [groupFilter, setGroupFilter] = useState('all');
  const [loaded, setLoaded] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<WorkflowDefinition>(EMPTY_WORKFLOW);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [catalogueError, setCatalogueError] = useState<string | null>(null);
  const [orbError, setOrbError] = useState<string | null>(null);
  const [importViolations, setImportViolations] = useState<string[]>([]);
  const importInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    let cancelled = false;
    Promise.all([listWorkflows(), listWorkflowGroups(), fetchAgentDefinitions()])
      .then(([w, g, a]) => {
        if (cancelled) return;
        setItems(w);
        setGroups(g);
        setAvailableAgents(a);
        setLoaded(true);
      })
      .catch((err) => {
        if (cancelled) return;
        setCatalogueError(
          err instanceof Error ? err.message : 'Failed to load the pipeline agent catalogue.',
        );
        setLoaded(true);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (!loaded) {
    return <div className="auth-loading">Loading workflows...</div>;
  }

  function toggleAgentId(id: string, checked: boolean) {
    setForm((f) => {
      const nextIds = checked
        ? [...f.agentIds, id]
        : f.agentIds.filter((existing) => existing !== id);
      const sequenceOrderOf = (agentId: string): number => {
        const agent = availableAgents.find((a) => a.id === agentId);
        return agent ? agent.sequenceOrder : Number.MAX_SAFE_INTEGER;
      };
      const sortedIds = nextIds
        .map((agentId, index) => ({ agentId, index }))
        .sort(
          (a, b) => sequenceOrderOf(a.agentId) - sequenceOrderOf(b.agentId) || a.index - b.index,
        )
        .map((entry) => entry.agentId);
      return { ...f, agentIds: sortedIds };
    });
  }

  function groupName(groupId?: string): string {
    if (!groupId) return '—';
    return groups.find((g) => g.id === groupId)?.name ?? groupId;
  }

  // Stages currently selected in the "Pipeline agents" picker, in pipeline order —
  // the gate placement dropdown offers exactly these and nothing else (AC-02).
  function selectedStagesInOrder(agentIds: string[]): AgentDefinition[] {
    return availableAgents
      .filter((agent) => agentIds.includes(agent.id))
      .sort((a, b) => a.sequenceOrder - b.sequenceOrder);
  }

  function defaultGatePlacement(agentIds: string[]): string {
    const ordered = selectedStagesInOrder(agentIds);
    if (ordered.some((agent) => agent.id === 'realisation')) return 'realisation';
    return ordered.length > 0 ? ordered[ordered.length - 1].id : '';
  }

  const orbCandidates = candidateOrbTargets(form, items, groups);

  function addOrb() {
    setForm((f) => ({
      ...f,
      workflowOrbs: [
        ...(f.workflowOrbs ?? []),
        { workflowId: '', mode: undefined as unknown as WorkflowOrb['mode'], placementStage: '' },
      ],
    }));
  }
  function removeOrb(index: number) {
    setForm((f) => ({
      ...f,
      workflowOrbs: (f.workflowOrbs ?? []).filter((_, i) => i !== index),
    }));
  }
  function updateOrb(index: number, patch: Partial<WorkflowOrb>) {
    setForm((f) => ({
      ...f,
      workflowOrbs: (f.workflowOrbs ?? []).map((orb, i) =>
        i === index ? { ...orb, ...patch } : orb,
      ),
    }));
  }

  const visibleItems =
    groupFilter === 'all'
      ? items
      : groupFilter === 'none'
        ? items.filter((i) => !i.groupId)
        : items.filter((i) => i.groupId === groupFilter);

  const unknownAgentIds = form.agentIds.filter(
    (id) => !availableAgents.some((agent) => agent.id === id),
  );

  function startCreate() {
    setEditingId('new');
    setForm(EMPTY_WORKFLOW);
    setError(null);
    setInfo(null);
  }
  function startEdit(item: WorkflowDefinition) {
    setEditingId(item.id);
    setForm(item);
    setError(null);
    setInfo(null);
  }
  function cancelEdit() {
    setEditingId(null);
  }

  async function handleSave() {
    if (!form.groupId && !form.projectName.trim()) {
      setError(
        'Link the workflow to a group or enter a project name — without a group, a project is required.',
      );
      return;
    }
    if (form.agentIds.length === 0) {
      setError('Select at least one pipeline agent — a workflow with no stages cannot run.');
      return;
    }
    setOrbError(null);
    const missingModeIndex = (form.workflowOrbs ?? []).findIndex((orb) => !orb.mode);
    if (missingModeIndex !== -1) {
      setOrbError(
        `Workflow orb ${missingModeIndex + 1}: choose an execution mode (Sequential or Parallel) before saving.`,
      );
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const request = { ...form, groupId: form.groupId || undefined };
      const saved =
        editingId === 'new'
          ? await createWorkflow(request)
          : await updateWorkflow(editingId!, request);
      setItems((prev) =>
        editingId === 'new' ? [...prev, saved] : prev.map((i) => (i.id === saved.id ? saved : i)),
      );
      setEditingId(null);
    } catch (err) {
      setOrbError(null);
      // Surfaces the server's actual message (e.g. the approval-gate placement validation
      // naming the offending stage) instead of a generic failure (AC-03).
      setError(err instanceof Error ? err.message : 'Failed to save workflow.');
      console.warn(err);
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(id: string) {
    try {
      await deleteWorkflow(id);
      setItems((prev) => prev.filter((i) => i.id !== id));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete workflow.');
      console.warn(err);
    }
  }

  async function handleExport() {
    setError(null);
    setInfo(null);
    try {
      downloadWorkflowBundle(await exportWorkflows());
    } catch (err) {
      setError('Failed to export workflows.');
      console.warn(err);
    }
  }

  async function handleImportFile(file: File) {
    setError(null);
    setInfo(null);
    setImportViolations([]);
    try {
      const bundle = JSON.parse(await readFileText(file)) as WorkflowExportBundle;
      const result = await importWorkflows(bundle);
      const [w, g] = await Promise.all([listWorkflows(), listWorkflowGroups()]);
      setItems(w);
      setGroups(g);
      setInfo(
        `Imported ${result.workflowsImported} workflow(s) and ${result.groupsImported} group(s).`,
      );
      setImportViolations(result.violations ?? []);
    } catch (err) {
      setError('Failed to import workflows from JSON.');
      console.warn(err);
    }
  }

  return (
    <div>
      <div className="workflow-design-toolbar">
        <button className="button button--start" onClick={startCreate}>
          + New workflow
        </button>
        <label className="workflow-group-filter">
          Group
          <select
            className="settings-input"
            value={groupFilter}
            onChange={(e) => setGroupFilter(e.target.value)}
          >
            <option value="all">All groups</option>
            <option value="none">No group</option>
            {groups.map((g) => (
              <option key={g.id} value={g.id}>
                {g.name}
              </option>
            ))}
          </select>
        </label>
        <div className="workflow-design-toolbar__spacer" />
        <button className="button button--small" onClick={() => void handleExport()}>
          ⬇ Export JSON
        </button>
        <button className="button button--small" onClick={() => importInputRef.current?.click()}>
          ⬆ Import JSON
        </button>
        <input
          ref={importInputRef}
          type="file"
          accept="application/json,.json"
          style={{ display: 'none' }}
          aria-label="Import workflows JSON"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) void handleImportFile(file);
            e.target.value = '';
          }}
        />
      </div>
      {error && (
        <p id="workflow-save-error" className="settings-error" role="alert">
          {error}
        </p>
      )}
      {info && <p className="settings-desc">{info}</p>}
      {importViolations.length > 0 && (
        <div className="settings-desc workflow-import-violations" role="status">
          <p>{importViolations.length} reference(s) could not be resolved:</p>
          <ul>
            {importViolations.map((violation) => (
              <li key={violation}>{violation}</li>
            ))}
          </ul>
        </div>
      )}

      {catalogueError && (
        <p className="settings-error" role="alert">
          {catalogueError}
        </p>
      )}
      {editingId && !catalogueError && (
        <div className="settings-form">
          <h2 className="settings-form-title">
            {editingId === 'new' ? 'New workflow' : 'Edit workflow'}
          </h2>
          <div className="settings-form-grid">
            <label>
              Workflow ID
              <input
                className="settings-input"
                value={form.id}
                disabled={editingId !== 'new'}
                onChange={(e) => setForm((f) => ({ ...f, id: e.target.value }))}
                placeholder="Auto-generated if left blank"
              />
              {editingId === 'new' && (
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
            <label style={{ gridColumn: '1 / -1' }}>
              Description
              <textarea
                className="settings-input"
                rows={2}
                value={form.description}
                onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              />
            </label>
            <div style={{ gridColumn: '1 / -1' }}>
              <span>Pipeline agents</span>
              <div className="workflow-agent-picker">
                {availableAgents.map((agent) => (
                  <label key={agent.id} className="workflow-agent-picker__option">
                    <input
                      type="checkbox"
                      checked={form.agentIds.includes(agent.id)}
                      onChange={(e) => toggleAgentId(agent.id, e.target.checked)}
                    />
                    <span className="workflow-agent-picker__name">{agent.name}</span>
                    <span className="settings-desc">{agent.description}</span>
                  </label>
                ))}
              </div>
              <span className="settings-desc">
                Select the pipeline stages this workflow runs, in order. Include "Realisation Agent"
                to have the workflow write real code from the implementation plan and push it to a
                branch — without it, the workflow stops after writing the plan document.
              </span>
              {unknownAgentIds.length > 0 && (
                <p className="settings-desc" role="status">
                  Unrecognised pipeline agent id(s) ignored in the picker:{' '}
                  {unknownAgentIds.join(', ')}. They remain stored and will round-trip on save.
                </p>
              )}
            </div>
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
                Pauses the run after the chosen stage for a human Accept/Deny decision (and, only
                after Realisation, Accept with comments to steer a re-run).
              </span>
            </label>
            {form.approvalGate?.enabled && (
              <div style={{ gridColumn: '1 / -1' }}>
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
                    {selectedStagesInOrder(form.agentIds).map((agent) => (
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
            <div style={{ gridColumn: '1 / -1' }}>
              <span>Workflow orbs</span>
              {orbError && (
                <p id="workflow-orb-error" role="alert" className="settings-error">
                  {orbError}
                </p>
              )}
              {(form.workflowOrbs ?? []).map((orb, index) => (
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
                      {orbCandidates.map((w) => (
                        <option key={w.id} value={w.id}>
                          {w.name}
                        </option>
                      ))}
                    </select>
                  </label>
                  <fieldset aria-describedby={orbError ? 'workflow-orb-error' : undefined}>
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
                      {selectedStagesInOrder(form.agentIds).map((agent) => (
                        <option key={agent.id} value={agent.id}>
                          {agent.name}
                        </option>
                      ))}
                    </select>
                  </label>
                  <button
                    type="button"
                    className="button button--small"
                    onClick={() => removeOrb(index)}
                  >
                    Remove orb
                  </button>
                </div>
              ))}
              <button
                type="button"
                className="button button--small"
                onClick={addOrb}
                disabled={(form.workflowOrbs ?? []).length >= 5}
              >
                + Add orb
              </button>
              <span className="settings-desc">
                A workflow orb triggers another workflow as part of this one. Up to 5 orbs are
                allowed (the server enforces the actual configured limit).
              </span>
            </div>
            <label>
              Ask for prompt on start
              <input
                type="checkbox"
                checked={form.promptRequired ?? false}
                onChange={(e) => setForm((f) => ({ ...f, promptRequired: e.target.checked }))}
              />
              <span className="settings-desc">
                Shows a themed prompt dialog when this workflow is started (e.g. spec creation from
                prompt + RAG).
              </span>
            </label>
            <label style={{ gridColumn: '1 / -1' }}>
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
          </div>
          <div style={{ display: 'flex', gap: 12 }}>
            <button
              className="button button--start"
              onClick={() => void handleSave()}
              disabled={saving}
              aria-describedby={error ? 'workflow-save-error' : undefined}
            >
              {saving ? '⟳ Saving…' : 'Save'}
            </button>
            <button className="back-button" onClick={cancelEdit}>
              Cancel
            </button>
          </div>
        </div>
      )}

      <table className="settings-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>Name</th>
            <th>Project</th>
            <th>Group</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {visibleItems.map((item) => (
            <tr key={item.id}>
              <td>{item.id}</td>
              <td>{item.name}</td>
              <td>{item.projectName}</td>
              <td>{groupName(item.groupId)}</td>
              <td>{item.status}</td>
              <td>
                <button className="back-button" onClick={() => startEdit(item)}>
                  Edit
                </button>
                <button className="back-button" onClick={() => void handleDelete(item.id)}>
                  Delete
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function GroupsPanel() {
  const [items, setItems] = useState<WorkflowGroup[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<WorkflowGroup>(EMPTY_GROUP);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!loaded) {
    void listWorkflowGroups().then((g) => {
      setItems(g);
      setLoaded(true);
    });
    return <div className="auth-loading">Loading groups...</div>;
  }

  function startCreate() {
    setEditingId('new');
    setForm(EMPTY_GROUP);
    setError(null);
  }
  function startEdit(item: WorkflowGroup) {
    setEditingId(item.id);
    setForm(item);
    setError(null);
  }

  async function handleSave() {
    setSaving(true);
    setError(null);
    try {
      const saved =
        editingId === 'new'
          ? await createWorkflowGroup(form)
          : await updateWorkflowGroup(editingId!, form);
      setItems((prev) =>
        editingId === 'new' ? [...prev, saved] : prev.map((i) => (i.id === saved.id ? saved : i)),
      );
      setEditingId(null);
    } catch (err) {
      setError('Failed to save workflow group.');
      console.warn(err);
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(id: string) {
    try {
      await deleteWorkflowGroup(id);
      setItems((prev) => prev.filter((i) => i.id !== id));
    } catch (err) {
      setError('Failed to delete workflow group.');
      console.warn(err);
    }
  }

  return (
    <div>
      <div className="workflow-design-toolbar">
        <button className="button button--start" onClick={startCreate}>
          + New group
        </button>
      </div>
      <p className="settings-desc">
        Groups bundle workflows designed for a specific meaning (for example onboarding, compliance
        or reporting). Deleting a group unlinks its workflows; the workflows themselves are kept.
      </p>
      {error && <p className="settings-error">{error}</p>}

      {editingId && (
        <div className="settings-form">
          <h2 className="settings-form-title">
            {editingId === 'new' ? 'New group' : 'Edit group'}
          </h2>
          <div className="settings-form-grid">
            <label>
              Group ID
              <input
                className="settings-input"
                value={form.id}
                disabled={editingId !== 'new'}
                onChange={(e) => setForm((f) => ({ ...f, id: e.target.value }))}
                placeholder="Auto-generated if left blank"
              />
            </label>
            <label>
              Group name
              <input
                className="settings-input"
                value={form.name}
                onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                placeholder="Onboarding flows"
              />
            </label>
            <label>
              Project name
              <input
                className="settings-input"
                value={form.projectName ?? ''}
                onChange={(e) => setForm((f) => ({ ...f, projectName: e.target.value }))}
                placeholder="Leave empty for a global group"
              />
              <span className="settings-desc">
                Empty = global: workflows in this group are available for every project.
              </span>
            </label>
            <label style={{ gridColumn: '1 / -1' }}>
              Description
              <textarea
                className="settings-input"
                rows={2}
                value={form.description}
                onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              />
            </label>
          </div>
          <div style={{ display: 'flex', gap: 12 }}>
            <button
              className="button button--start"
              onClick={() => void handleSave()}
              disabled={saving}
            >
              {saving ? '⟳ Saving…' : 'Save'}
            </button>
            <button className="back-button" onClick={() => setEditingId(null)}>
              Cancel
            </button>
          </div>
        </div>
      )}

      <table className="settings-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>Name</th>
            <th>Project</th>
            <th>Description</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.id}>
              <td>{item.id}</td>
              <td>{item.name}</td>
              <td>{item.projectName || 'Global (all projects)'}</td>
              <td>{item.description}</td>
              <td>
                <button className="back-button" onClick={() => startEdit(item)}>
                  Edit
                </button>
                <button className="back-button" onClick={() => void handleDelete(item.id)}>
                  Delete
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

const PROMPT_EXAMPLES: Record<'agent' | 'subagent' | 'skill', string> = {
  agent:
    'An agent that owns the full code review lifecycle for a pull request: it pulls the diff and the ' +
    'linked ticket, delegates static security analysis to a security-scanner subagent and style checks to a ' +
    'linting subagent, synthesizes their findings into a single pass/fail verdict with a prioritized comment ' +
    'list, and posts the review back to the PR.',
  subagent:
    'A subagent that performs static security analysis on a code diff: given the changed files and the ' +
    'language, it checks for injection risks, hardcoded secrets, and unsafe deserialization, then returns a ' +
    'structured list of findings with file, line, severity, and remediation advice for its parent agent.',
  skill:
    'A skill that turns a pull request diff into a structured changelog entry: given the unified diff and ' +
    'commit messages as input, it returns a short human-readable summary, a list of breaking changes, and a ' +
    'suggested semantic version bump (patch/minor/major) as output.',
};

function PromptGenerator({
  kind,
  onGenerate,
}: {
  kind: 'agent' | 'subagent' | 'skill';
  onGenerate: (prompt: string) => Promise<void>;
}) {
  const [prompt, setPrompt] = useState('');
  const [generating, setGenerating] = useState(false);

  async function handleGenerate() {
    setGenerating(true);
    try {
      await onGenerate(prompt.trim());
    } finally {
      setGenerating(false);
    }
  }

  return (
    <div className="workflow-prompt-generator">
      <label>
        Describe the {kind} you want in plain language
        <textarea
          className="settings-input"
          rows={3}
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          placeholder={PROMPT_EXAMPLES[kind]}
        />
      </label>
      <button
        className="button button--start"
        onClick={() => void handleGenerate()}
        disabled={generating}
      >
        {generating ? '⟳ Generating…' : '✨ Generate draft'}
      </button>
      <p className="settings-desc">
        Draft generation uses the platform's LLM. The more specific you are about inputs, outputs,
        and delegation (see the example above), the higher-quality the draft. Leave the field empty
        to generate an example {kind} instead. Review and edit the generated draft before saving.
      </p>
    </div>
  );
}

function AgentsPanel() {
  const [items, setItems] = useState<AgentSpec[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [editingName, setEditingName] = useState<string | null>(null);
  const [form, setForm] = useState<AgentSpec>(EMPTY_AGENT);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  if (!loaded) {
    void listAgentSpecs().then((a) => {
      setItems(a);
      setLoaded(true);
    });
    return <div className="auth-loading">Loading agents...</div>;
  }

  function startCreate() {
    setEditingName('new');
    setForm(EMPTY_AGENT);
    setError(null);
    setInfo(null);
  }
  function startEdit(item: AgentSpec) {
    setEditingName(item.name);
    setForm(item);
    setError(null);
    setInfo(null);
  }

  async function handleGenerate(prompt: string) {
    setInfo(null);
    try {
      const draft = await generateAgentSpec(prompt);
      setEditingName('new');
      setForm(draft);
      await generateMissingSubagents(draft);
    } catch (err) {
      setError('Failed to generate agent draft.');
      console.warn(err);
    }
  }

  async function generateMissingSubagents(agent: AgentSpec) {
    if (agent.subagentNames.length === 0) return;
    try {
      const existingNames = new Set((await listSubagentSpecs()).map((s) => s.name));
      const missingNames = agent.subagentNames.filter((name) => !existingNames.has(name));
      if (missingNames.length === 0) return;

      const created: string[] = [];
      for (const name of missingNames) {
        try {
          const concept =
            `A subagent named "${name}" that supports the agent "${agent.name}" ` +
            `(${agent.description || agent.role}). Its purpose within that agent: ${agent.instructions}`;
          const subagentDraft = await generateSubagentSpec(concept);
          await createSubagentSpec({ ...subagentDraft, name, parentAgent: agent.name });
          created.push(name);
        } catch (err) {
          console.warn(`Failed to generate suggested subagent "${name}"`, err);
        }
      }
      if (created.length > 0) {
        setInfo(
          `Also generated ${created.length} suggested subagent(s) that didn't exist yet: ` +
            `${created.join(', ')}. Review them in the Subagents tab.`,
        );
      }
    } catch (err) {
      console.warn('Failed to check for existing subagents', err);
    }
  }

  async function handleSave() {
    setSaving(true);
    setError(null);
    try {
      const saved =
        editingName === 'new'
          ? await createAgentSpec(form)
          : await updateAgentSpec(editingName!, form);
      setItems((prev) =>
        editingName === 'new'
          ? [...prev, saved]
          : prev.map((i) => (i.name === saved.name ? saved : i)),
      );
      setEditingName(null);
    } catch (err) {
      setError('Failed to save agent definition.');
      console.warn(err);
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(name: string) {
    try {
      await deleteAgentSpec(name);
      setItems((prev) => prev.filter((i) => i.name !== name));
    } catch (err) {
      setError('Failed to delete agent definition.');
      console.warn(err);
    }
  }

  return (
    <div>
      <PromptGenerator kind="agent" onGenerate={handleGenerate} />
      <div className="workflow-design-toolbar">
        <button className="button button--start" onClick={startCreate}>
          + New agent
        </button>
      </div>
      {error && <p className="settings-error">{error}</p>}
      {info && <p className="settings-desc">{info}</p>}

      {editingName && (
        <div className="settings-form">
          <h2 className="settings-form-title">
            {editingName === 'new' ? 'New agent' : 'Edit agent'}
          </h2>
          <div className="settings-form-grid">
            <label>
              Name
              <input
                className="settings-input"
                value={form.name}
                disabled={editingName !== 'new'}
                onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              />
            </label>
            <label>
              Role
              <input
                className="settings-input"
                value={form.role}
                onChange={(e) => setForm((f) => ({ ...f, role: e.target.value }))}
              />
            </label>
            <label style={{ gridColumn: '1 / -1' }}>
              Description
              <input
                className="settings-input"
                value={form.description}
                onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              />
            </label>
            <label style={{ gridColumn: '1 / -1' }}>
              Instructions
              <textarea
                className="settings-input"
                rows={3}
                value={form.instructions}
                onChange={(e) => setForm((f) => ({ ...f, instructions: e.target.value }))}
              />
            </label>
            <label>
              Linked subagents
              <input
                className="settings-input"
                value={form.subagentNames.join(', ')}
                onChange={(e) => setForm((f) => ({ ...f, subagentNames: toList(e.target.value) }))}
              />
            </label>
            <label>
              Linked skills
              <input
                className="settings-input"
                value={form.skillNames.join(', ')}
                onChange={(e) => setForm((f) => ({ ...f, skillNames: toList(e.target.value) }))}
              />
            </label>
            <label>
              Workflow association
              <input
                className="settings-input"
                value={form.workflowId ?? ''}
                onChange={(e) => setForm((f) => ({ ...f, workflowId: e.target.value }))}
              />
            </label>
          </div>
          <div style={{ display: 'flex', gap: 12 }}>
            <button
              className="button button--start"
              onClick={() => void handleSave()}
              disabled={saving}
            >
              {saving ? '⟳ Saving…' : 'Save'}
            </button>
            <button className="back-button" onClick={() => setEditingName(null)}>
              Cancel
            </button>
          </div>
        </div>
      )}

      <table className="settings-table">
        <thead>
          <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Role</th>
            <th>Subagents</th>
            <th>Skills</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.name}>
              <td>{item.name}</td>
              <td>{item.builtIn ? 'Built-in' : 'Custom'}</td>
              <td>{item.role}</td>
              <td>{item.subagentNames.join(', ')}</td>
              <td>{item.skillNames.join(', ')}</td>
              <td>
                {!item.builtIn && (
                  <>
                    <button className="back-button" onClick={() => startEdit(item)}>
                      Edit
                    </button>
                    <button className="back-button" onClick={() => void handleDelete(item.name)}>
                      Delete
                    </button>
                  </>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function SubagentsPanel() {
  const [items, setItems] = useState<SubagentSpec[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [editingName, setEditingName] = useState<string | null>(null);
  const [form, setForm] = useState<SubagentSpec>(EMPTY_SUBAGENT);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!loaded) {
    void listSubagentSpecs().then((s) => {
      setItems(s);
      setLoaded(true);
    });
    return <div className="auth-loading">Loading subagents...</div>;
  }

  function startCreate() {
    setEditingName('new');
    setForm(EMPTY_SUBAGENT);
    setError(null);
  }
  function startEdit(item: SubagentSpec) {
    setEditingName(item.name);
    setForm(item);
    setError(null);
  }

  async function handleGenerate(prompt: string) {
    try {
      const draft = await generateSubagentSpec(prompt);
      setEditingName('new');
      setForm(draft);
    } catch (err) {
      setError('Failed to generate subagent draft.');
      console.warn(err);
    }
  }

  async function handleSave() {
    setSaving(true);
    setError(null);
    try {
      const saved =
        editingName === 'new'
          ? await createSubagentSpec(form)
          : await updateSubagentSpec(editingName!, form);
      setItems((prev) =>
        editingName === 'new'
          ? [...prev, saved]
          : prev.map((i) => (i.name === saved.name ? saved : i)),
      );
      setEditingName(null);
    } catch (err) {
      setError('Failed to save subagent definition.');
      console.warn(err);
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(name: string) {
    try {
      await deleteSubagentSpec(name);
      setItems((prev) => prev.filter((i) => i.name !== name));
    } catch (err) {
      setError('Failed to delete subagent definition.');
      console.warn(err);
    }
  }

  return (
    <div>
      <PromptGenerator kind="subagent" onGenerate={handleGenerate} />
      <div className="workflow-design-toolbar">
        <button className="button button--start" onClick={startCreate}>
          + New subagent
        </button>
      </div>
      {error && <p className="settings-error">{error}</p>}

      {editingName && (
        <div className="settings-form">
          <h2 className="settings-form-title">
            {editingName === 'new' ? 'New subagent' : 'Edit subagent'}
          </h2>
          <div className="settings-form-grid">
            <label>
              Name
              <input
                className="settings-input"
                value={form.name}
                disabled={editingName !== 'new'}
                onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              />
            </label>
            <label>
              Parent agent
              <input
                className="settings-input"
                value={form.parentAgent}
                onChange={(e) => setForm((f) => ({ ...f, parentAgent: e.target.value }))}
              />
            </label>
            <label style={{ gridColumn: '1 / -1' }}>
              Description
              <input
                className="settings-input"
                value={form.description}
                onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              />
            </label>
            <label style={{ gridColumn: '1 / -1' }}>
              Responsibilities
              <input
                className="settings-input"
                value={form.responsibilities}
                onChange={(e) => setForm((f) => ({ ...f, responsibilities: e.target.value }))}
              />
            </label>
            <label style={{ gridColumn: '1 / -1' }}>
              Instructions
              <textarea
                className="settings-input"
                rows={3}
                value={form.instructions}
                onChange={(e) => setForm((f) => ({ ...f, instructions: e.target.value }))}
              />
            </label>
            <label>
              Linked skills
              <input
                className="settings-input"
                value={form.skillNames.join(', ')}
                onChange={(e) => setForm((f) => ({ ...f, skillNames: toList(e.target.value) }))}
              />
            </label>
            <label>
              Workflow association
              <input
                className="settings-input"
                value={form.workflowId ?? ''}
                onChange={(e) => setForm((f) => ({ ...f, workflowId: e.target.value }))}
              />
            </label>
          </div>
          <div style={{ display: 'flex', gap: 12 }}>
            <button
              className="button button--start"
              onClick={() => void handleSave()}
              disabled={saving}
            >
              {saving ? '⟳ Saving…' : 'Save'}
            </button>
            <button className="back-button" onClick={() => setEditingName(null)}>
              Cancel
            </button>
          </div>
        </div>
      )}

      <table className="settings-table">
        <thead>
          <tr>
            <th>Name</th>
            <th>Parent agent</th>
            <th>Skills</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.name}>
              <td>{item.name}</td>
              <td>{item.parentAgent}</td>
              <td>{item.skillNames.join(', ')}</td>
              <td>
                <button className="back-button" onClick={() => startEdit(item)}>
                  Edit
                </button>
                <button className="back-button" onClick={() => void handleDelete(item.name)}>
                  Delete
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

const EMPTY_CATALOG: SkillCatalog = { localSkills: [], externalSkills: [], sources: [] };

type LocalSkillRow = { key: string; readOnly: false; skill: SkillSpec };
type ExternalSkillRow = { key: string; readOnly: true; skill: ExternalSkill };
type SkillRow = LocalSkillRow | ExternalSkillRow;

function describeSourceOutcome(source: SkillCatalogSourceStatus): string {
  switch (source.outcome) {
    case 'AUTH_FAILED':
      return `${source.name}: authentication was rejected (HTTP ${source.httpStatus ?? 'unknown'}).`;
    case 'TIMEOUT':
      return `${source.name}: timed out.`;
    case 'UNREACHABLE':
      return `${source.name}: could not be reached.`;
    case 'INVALID_RESPONSE':
      return `${source.name}: returned an invalid response.`;
    case 'BLOCKED_BY_POLICY':
      return `${source.name}: blocked by egress policy.`;
    case 'CONFIG_ERROR':
      return `${source.name}: configuration error — check its API key.`;
    default:
      return `${source.name}: unknown status.`;
  }
}

function SkillsPanel() {
  const [catalog, setCatalog] = useState<SkillCatalog | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [editingName, setEditingName] = useState<string | null>(null);
  const [form, setForm] = useState<SkillSpec>(EMPTY_SKILL);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const mountedRef = useRef(true);

  async function loadCatalog() {
    setLoading(true);
    setLoadError(null);
    setCatalog(null);
    try {
      const result = await getSkillCatalog();
      if (!mountedRef.current) return;
      setCatalog(result);
    } catch (err) {
      if (!mountedRef.current) return;
      setLoadError(err instanceof Error ? err.message : 'Failed to load skill catalog.');
    } finally {
      if (mountedRef.current) setLoading(false);
    }
  }

  useEffect(() => {
    mountedRef.current = true;
    void loadCatalog();
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const rows = useMemo<SkillRow[]>(() => {
    if (!catalog) return [];
    const localRows: SkillRow[] = catalog.localSkills.map((skill) => ({
      key: `local:${skill.name}`,
      readOnly: false,
      skill,
    }));
    const externalRows: SkillRow[] = [];
    for (const source of catalog.sources) {
      if (source.kind !== 'MARKETPLACE') continue;
      for (const skill of catalog.externalSkills) {
        if (skill.marketplaceId === source.marketplaceId) {
          externalRows.push({
            key: `mp:${skill.marketplaceId}:${skill.name}`,
            readOnly: true,
            skill,
          });
        }
      }
    }
    return [...localRows, ...externalRows];
  }, [catalog]);

  const statusMessages = useMemo(() => {
    if (!catalog) return [];
    return catalog.sources
      .filter((source) => source.outcome !== 'SUCCESS')
      .map(describeSourceOutcome);
  }, [catalog]);

  function startCreate() {
    setEditingName('new');
    setForm(EMPTY_SKILL);
    setError(null);
  }
  function startEdit(skill: SkillSpec) {
    setEditingName(skill.name);
    setForm(skill);
    setError(null);
  }

  async function handleGenerate(prompt: string) {
    try {
      const draft = await generateSkillSpec(prompt);
      setEditingName('new');
      setForm(draft);
    } catch (err) {
      setError('Failed to generate skill draft.');
      console.warn(err);
    }
  }

  async function handleSave() {
    setSaving(true);
    setError(null);
    try {
      const saved =
        editingName === 'new'
          ? await createSkillSpec(form)
          : await updateSkillSpec(editingName!, form);
      setCatalog((prev) => {
        const base = prev ?? EMPTY_CATALOG;
        const localSkills =
          editingName === 'new'
            ? [...base.localSkills, saved]
            : base.localSkills.map((s) => (s.name === saved.name ? saved : s));
        return { ...base, localSkills };
      });
      setEditingName(null);
    } catch (err) {
      setError('Failed to save skill definition.');
      console.warn(err);
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(name: string) {
    setError(null);
    try {
      await deleteSkillSpec(name);
      setCatalog((prev) =>
        prev ? { ...prev, localSkills: prev.localSkills.filter((s) => s.name !== name) } : prev,
      );
    } catch (err) {
      setError('Failed to delete skill definition.');
      console.warn(err);
    }
  }

  return (
    <div>
      <PromptGenerator kind="skill" onGenerate={handleGenerate} />
      <div className="workflow-design-toolbar">
        <button className="button button--start" onClick={startCreate}>
          + New skill
        </button>
        <button className="back-button" onClick={() => void loadCatalog()}>
          ⟳ Refresh
        </button>
      </div>
      {error && <p className="settings-error">{error}</p>}

      {editingName && (
        <div className="settings-form">
          <h2 className="settings-form-title">
            {editingName === 'new' ? 'New skill' : 'Edit skill'}
          </h2>
          <div className="settings-form-grid">
            <label>
              Name
              <input
                className="settings-input"
                value={form.name}
                disabled={editingName !== 'new'}
                onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              />
            </label>
            <label style={{ gridColumn: '1 / -1' }}>
              Description
              <input
                className="settings-input"
                value={form.description}
                onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              />
            </label>
            <label>
              Input contract
              <input
                className="settings-input"
                value={form.inputContract}
                onChange={(e) => setForm((f) => ({ ...f, inputContract: e.target.value }))}
              />
            </label>
            <label>
              Output contract
              <input
                className="settings-input"
                value={form.outputContract}
                onChange={(e) => setForm((f) => ({ ...f, outputContract: e.target.value }))}
              />
            </label>
            <label style={{ gridColumn: '1 / -1' }}>
              Execution instructions
              <textarea
                className="settings-input"
                rows={3}
                value={form.executionInstructions}
                onChange={(e) => setForm((f) => ({ ...f, executionInstructions: e.target.value }))}
              />
            </label>
            <label style={{ gridColumn: '1 / -1' }}>
              Policy / validation notes
              <input
                className="settings-input"
                value={form.policyNotes ?? ''}
                onChange={(e) => setForm((f) => ({ ...f, policyNotes: e.target.value }))}
              />
            </label>
          </div>
          <div style={{ display: 'flex', gap: 12 }}>
            <button
              className="button button--start"
              onClick={() => void handleSave()}
              disabled={saving}
            >
              {saving ? '⟳ Saving…' : 'Save'}
            </button>
            <button className="back-button" onClick={() => setEditingName(null)}>
              Cancel
            </button>
          </div>
        </div>
      )}

      <div role="status" aria-live="polite">
        {loading
          ? 'Loading skills…'
          : statusMessages.map((message, index) => <div key={index}>{message}</div>)}
      </div>

      {loadError ? (
        <p className="settings-error" role="alert">
          {loadError}
        </p>
      ) : (
        <table className="settings-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Marketplace</th>
              <th>Input</th>
              <th>Output</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) =>
              row.readOnly ? (
                <tr key={row.key}>
                  <td>{row.skill.name}</td>
                  <td>{row.skill.marketplaceName}</td>
                  <td>
                    <span className="settings-desc">Not provided</span>
                  </td>
                  <td>
                    <span className="settings-desc">Not provided</span>
                  </td>
                  <td>Read-only</td>
                </tr>
              ) : (
                <tr key={row.key}>
                  <td>{row.skill.name}</td>
                  <td>Local</td>
                  <td>{row.skill.inputContract}</td>
                  <td>{row.skill.outputContract}</td>
                  <td>
                    <button className="back-button" onClick={() => startEdit(row.skill)}>
                      Edit
                    </button>
                    <button
                      className="back-button"
                      onClick={() => void handleDelete(row.skill.name)}
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              ),
            )}
          </tbody>
        </table>
      )}
    </div>
  );
}
