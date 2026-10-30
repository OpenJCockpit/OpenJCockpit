import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from '@openjcockpit/i18n';
import {
  createWorkflow,
  deleteWorkflow,
  exportWorkflows,
  importWorkflows,
  updateWorkflow,
} from '@openjcockpit/api';
import type {
  AgentDefinition,
  WorkflowDefinition,
  WorkflowExportBundle,
  WorkflowGroup,
  WorkflowOrb,
} from '@openjcockpit/api';
import {
  ToolbarButton,
  StatusMessage,
  FileImportButton,
  fieldErrorClass,
  fieldHintClass,
  formInputClass,
  cx,
} from '@openjcockpit/ui';
import { readFileText, downloadWorkflowBundle } from '../../utils';
import { WorkflowEditor } from './WorkflowEditor/WorkflowEditor';
import type { WorkflowDraft } from './WorkflowEditor/OrbEditor';
import { WorkflowsTable } from './WorkflowsTable';
import {
  useWorkflowsPanelData,
  workflowGroupsQueryKey,
  workflowsQueryKey,
} from './useWorkflowsPanelData';
import WorkflowDesignStyles from '../../WorkflowDesign.module.scss';

const EMPTY_WORKFLOW: WorkflowDraft = {
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

const NO_WORKFLOWS: WorkflowDefinition[] = [];
const NO_GROUPS: WorkflowGroup[] = [];
const NO_AGENTS: AgentDefinition[] = [];

export function WorkflowsPanel() {
  const { t } = useTranslation('workflow');
  const queryClient = useQueryClient();
  const data = useWorkflowsPanelData();
  const items = data.items ?? NO_WORKFLOWS;
  const groups = data.groups ?? NO_GROUPS;
  const availableAgents = data.availableAgents ?? NO_AGENTS;
  const { loaded, loadError: catalogueError } = data;
  const [groupFilter, setGroupFilter] = useState('all');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<WorkflowDraft>(EMPTY_WORKFLOW);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [orbError, setOrbError] = useState<string | null>(null);
  const [importViolations, setImportViolations] = useState<string[]>([]);

  if (!loaded) {
    return <StatusMessage variant="loading">{t('workflows.loading')}</StatusMessage>;
  }

  function updateItems(update: (prev: WorkflowDefinition[]) => WorkflowDefinition[]) {
    queryClient.setQueryData<WorkflowDefinition[]>(
      workflowsQueryKey,
      (prev) => prev && update(prev),
    );
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

  async function handleSave(editingId: string) {
    if (!form.groupId && !form.projectName.trim()) {
      setError(
        'Link the workflow to a group or enter a project name — without a group, a project is required.',
      );
      return;
    }
    if (form.agentIds.length === 0) {
      setError('Link at least one agent — a workflow without agents cannot run.');
      return;
    }
    setOrbError(null);
    const orbs: WorkflowOrb[] = [];
    for (const [index, { mode, ...orb }] of (form.workflowOrbs ?? []).entries()) {
      if (!mode) {
        setOrbError(
          `Workflow orb ${index + 1}: choose an execution mode (Sequential or Parallel) before saving.`,
        );
        return;
      }
      orbs.push({ ...orb, mode });
    }
    setSaving(true);
    setError(null);
    try {
      const request: WorkflowDefinition = {
        ...form,
        workflowOrbs: form.workflowOrbs && orbs,
        groupId: form.groupId || undefined,
      };
      const saved =
        editingId === 'new'
          ? await createWorkflow(request)
          : await updateWorkflow(editingId, request);
      updateItems((prev) =>
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
      updateItems((prev) => prev.filter((i) => i.id !== id));
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
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: workflowsQueryKey }),
        queryClient.invalidateQueries({ queryKey: workflowGroupsQueryKey }),
      ]);
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
      <div className={WorkflowDesignStyles.workflowDesignToolbar}>
        <ToolbarButton onClick={startCreate}>{t('workflows.newButton')}</ToolbarButton>
        <label className={WorkflowDesignStyles.workflowGroupFilter}>
          {t('workflows.group')}
          <select
            className={cx(formInputClass, WorkflowDesignStyles.workflowGroupFilterSelect)}
            value={groupFilter}
            onChange={(e) => setGroupFilter(e.target.value)}
          >
            <option value="all">{t('workflows.allGroups')}</option>
            <option value="none">{t('workflows.noGroup')}</option>
            {groups.map((g) => (
              <option key={g.id} value={g.id}>
                {g.name}
              </option>
            ))}
          </select>
        </label>
        <div className={WorkflowDesignStyles.workflowDesignToolbarSpacer} />
        <ToolbarButton variant="small" onClick={() => void handleExport()}>
          {t('workflows.export')}
        </ToolbarButton>
        <FileImportButton
          accept="application/json,.json"
          inputLabel={t('workflows.importLabel')}
          onFile={(file) => void handleImportFile(file)}
        >
          {t('workflows.import')}
        </FileImportButton>
      </div>
      {error && (
        <p id="workflow-save-error" className={fieldErrorClass} role="alert">
          {error}
        </p>
      )}
      {info && <StatusMessage>{info}</StatusMessage>}
      {importViolations.length > 0 && (
        <div className={cx(fieldHintClass, 'workflow-import-violations')} role="status">
          <p>{t('workflows.unresolved', { count: importViolations.length })}</p>
          <ul>
            {importViolations.map((violation) => (
              <li key={violation}>{violation}</li>
            ))}
          </ul>
        </div>
      )}

      {catalogueError && (
        <p className={fieldErrorClass} role="alert">
          {catalogueError}
        </p>
      )}
      {editingId && !catalogueError && (
        <WorkflowEditor
          isNew={editingId === 'new'}
          form={form}
          setForm={setForm}
          saving={saving}
          onSave={() => void handleSave(editingId)}
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
