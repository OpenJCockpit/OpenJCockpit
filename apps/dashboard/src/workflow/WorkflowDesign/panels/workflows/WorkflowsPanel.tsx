import { useEffect, useRef, useState } from 'react';
import { fetchAgentDefinitions } from '../../../../api';
import type { AgentDefinition } from '../../../../types';
import {
  createWorkflow,
  deleteWorkflow,
  exportWorkflows,
  importWorkflows,
  listWorkflowGroups,
  listWorkflows,
  updateWorkflow,
} from '../../../workflowApi';
import type {
  WorkflowDefinition,
  WorkflowExportBundle,
  WorkflowGroup,
} from '../../../workflowTypes';
import { readFileText, downloadWorkflowBundle } from '../../utils';
import { WorkflowEditor } from './WorkflowEditor/WorkflowEditor';
import { WorkflowsTable } from './WorkflowsTable';

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

export function WorkflowsPanel() {
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

  function groupName(groupId?: string): string {
    if (!groupId) return '—';
    return groups.find((g) => g.id === groupId)?.name ?? groupId;
  }

  const visibleItems =
    groupFilter === 'all'
      ? items
      : groupFilter === 'none'
        ? items.filter((i) => !i.groupId)
        : items.filter((i) => i.groupId === groupFilter);

  function startCreate() {
    setEditingId('new');
    setForm(EMPTY_WORKFLOW);
    setError(null);
    setInfo(null);
  }
  function cancelEdit() {
    setEditingId(null);
  }
  function startEdit(item: WorkflowDefinition) {
    setEditingId(item.id);
    setForm(item);
    setError(null);
    setInfo(null);
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
          className="workflow-design-hidden-input"
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
        <WorkflowEditor
          isNew={editingId === 'new'}
          form={form}
          setForm={setForm}
          saving={saving}
          onSave={() => void handleSave()}
          onCancel={cancelEdit}
          groups={groups}
          items={items}
          availableAgents={availableAgents}
          orbError={orbError}
          saveError={error}
        />
      )}

      <WorkflowsTable
        items={visibleItems}
        groupName={groupName}
        onEdit={startEdit}
        onDelete={(id) => void handleDelete(id)}
      />
    </div>
  );
}
