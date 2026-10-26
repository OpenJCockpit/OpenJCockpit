import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { ToolbarButton, StatusMessage } from '@openjcockpit/ui';
import {
  createSubagentSpec,
  deleteSubagentSpec,
  generateSubagentSpec,
  updateSubagentSpec,
} from '@openjcockpit/api';
import type { SubagentSpec } from '@openjcockpit/api';
import { PromptGenerator } from '../shared/PromptGenerator';
import { SubagentEditor } from './SubagentEditor';
import { SubagentsTable } from './SubagentsTable';
import { subagentSpecsQueryKey, useSubagentSpecs } from './useSubagentSpecs';
import WorkflowDesignStyles from '../../WorkflowDesign.module.scss';

const EMPTY_SUBAGENT: SubagentSpec = {
  name: '',
  parentAgent: '',
  description: '',
  responsibilities: '',
  instructions: '',
  skillNames: [],
  mcpTools: [],
};

const NO_ITEMS: SubagentSpec[] = [];

export function SubagentsPanel() {
  const queryClient = useQueryClient();
  const query = useSubagentSpecs();
  const items = query.data ?? NO_ITEMS;
  const loaded = !query.isPending;
  const [editingName, setEditingName] = useState<string | null>(null);
  const [form, setForm] = useState<SubagentSpec>(EMPTY_SUBAGENT);
  const [saving, setSaving] = useState(false);
  const [actionError, setError] = useState<string | null>(null);
  const loadError = query.isError
    ? query.error instanceof Error
      ? query.error.message
      : 'Failed to load subagents'
    : null;
  const error = actionError ?? loadError;

  function updateItems(update: (prev: SubagentSpec[]) => SubagentSpec[]) {
    queryClient.setQueryData<SubagentSpec[]>(subagentSpecsQueryKey, (prev) => update(prev!));
  }

  if (!loaded) {
    return <StatusMessage variant="loading">Loading subagents...</StatusMessage>;
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
      updateItems((prev) =>
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
      updateItems((prev) => prev.filter((i) => i.name !== name));
    } catch (err) {
      setError('Failed to delete subagent definition.');
      console.warn(err);
    }
  }

  return (
    <div>
      <PromptGenerator kind="subagent" onGenerate={handleGenerate} />
      <div className={WorkflowDesignStyles.workflowDesignToolbar}>
        <ToolbarButton onClick={startCreate}>+ New subagent</ToolbarButton>
      </div>
      {error && <StatusMessage variant="error">{error}</StatusMessage>}

      {editingName && (
        <SubagentEditor
          isNew={editingName === 'new'}
          form={form}
          setForm={setForm}
          saving={saving}
          onSave={() => void handleSave()}
          onCancel={() => setEditingName(null)}
        />
      )}

      <SubagentsTable
        items={items}
        onEdit={startEdit}
        onDelete={(name) => void handleDelete(name)}
      />
    </div>
  );
}
