import { useState } from 'react';
import { ToolbarButton, StatusMessage, fieldHintClass } from '@openjcockpit/ui';
import {
  createWorkflowGroup,
  deleteWorkflowGroup,
  listWorkflowGroups,
  updateWorkflowGroup,
} from '@openjcockpit/api';
import type { WorkflowGroup } from '@openjcockpit/api';
import { GroupEditor } from './GroupEditor';
import { GroupsTable } from './GroupsTable';
import WorkflowDesignStyles from '../../WorkflowDesign.module.scss';

const EMPTY_GROUP: WorkflowGroup = { id: '', name: '', description: '', projectName: '' };

export function GroupsPanel() {
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
    return <StatusMessage variant="loading">Loading groups...</StatusMessage>;
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
      <div className={WorkflowDesignStyles.workflowDesignToolbar}>
        <ToolbarButton onClick={startCreate}>+ New group</ToolbarButton>
      </div>
      <p className={fieldHintClass}>
        Groups bundle workflows designed for a specific meaning (for example onboarding, compliance
        or reporting). Deleting a group unlinks its workflows; the workflows themselves are kept.
      </p>
      {error && <StatusMessage variant="error">{error}</StatusMessage>}

      {editingId && (
        <GroupEditor
          isNew={editingId === 'new'}
          form={form}
          setForm={setForm}
          saving={saving}
          onSave={() => void handleSave()}
          onCancel={() => setEditingId(null)}
        />
      )}

      <GroupsTable items={items} onEdit={startEdit} onDelete={(id) => void handleDelete(id)} />
    </div>
  );
}
