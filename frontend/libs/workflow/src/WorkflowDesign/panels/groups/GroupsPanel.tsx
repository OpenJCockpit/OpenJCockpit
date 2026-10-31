import { useState } from 'react';
import { useTranslation } from '@openjcockpit/i18n';
import { Button, StatusMessage, fieldHintClass } from '@openjcockpit/ui';
import {
  createWorkflowGroup,
  deleteWorkflowGroup,
  listWorkflowGroups,
  updateWorkflowGroup,
} from '@openjcockpit/api';
import type { WorkflowGroup } from '@openjcockpit/api';
import { GroupEditor } from './GroupEditor';
import { GroupsTable } from './GroupsTable';
import { useConfirmDelete } from '../shared/useConfirmDelete';
import WorkflowDesignStyles from '../../WorkflowDesign.module.scss';

const EMPTY_GROUP: WorkflowGroup = { id: '', name: '', description: '', projectName: '' };

export function GroupsPanel() {
  const { t } = useTranslation('workflow');
  const { askDelete, dialog } = useConfirmDelete();
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
    return <StatusMessage variant="loading">{t('groups.loading')}</StatusMessage>;
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

  async function handleSave(editingId: string) {
    setSaving(true);
    setError(null);
    try {
      const saved =
        editingId === 'new'
          ? await createWorkflowGroup(form)
          : await updateWorkflowGroup(editingId, form);
      setItems((prev) =>
        editingId === 'new' ? [...prev, saved] : prev.map((i) => (i.id === saved.id ? saved : i)),
      );
      setEditingId(null);
    } catch (err) {
      setError(t('groups.saveFailed'));
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
      setError(t('groups.deleteFailed'));
      console.warn(err);
    }
  }

  return (
    <div>
      <div className={WorkflowDesignStyles.workflowDesignToolbar}>
        <Button variant="primary" onClick={startCreate}>
          {t('groups.newButton')}
        </Button>
      </div>
      <p className={fieldHintClass}>{t('groups.intro')}</p>
      {error && <StatusMessage variant="error">{error}</StatusMessage>}

      {editingId && (
        <GroupEditor
          isNew={editingId === 'new'}
          form={form}
          setForm={setForm}
          saving={saving}
          onSave={() => void handleSave(editingId)}
          onCancel={() => setEditingId(null)}
        />
      )}

      <GroupsTable
        items={items}
        onEdit={startEdit}
        onDelete={(id) =>
          askDelete('group', items.find((i) => i.id === id)?.name ?? id, () => handleDelete(id))
        }
      />
      {dialog}
    </div>
  );
}
