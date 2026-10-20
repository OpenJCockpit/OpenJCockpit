import { useEffect, useState } from 'react';
import { ToolbarButton, StatusMessage } from '@openjcockpit/ui';
import {
  createSubagentSpec,
  deleteSubagentSpec,
  generateSubagentSpec,
  isAbortError,
  listSubagentSpecs,
  updateSubagentSpec,
} from '@openjcockpit/api';
import type { SubagentSpec } from '@openjcockpit/api';
import { PromptGenerator } from '../shared/PromptGenerator';
import { SubagentEditor } from './SubagentEditor';
import { SubagentsTable } from './SubagentsTable';
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

export function SubagentsPanel() {
  const [items, setItems] = useState<SubagentSpec[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [editingName, setEditingName] = useState<string | null>(null);
  const [form, setForm] = useState<SubagentSpec>(EMPTY_SUBAGENT);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    listSubagentSpecs(controller.signal)
      .then((s) => {
        setItems(s);
        setLoaded(true);
      })
      .catch((err) => {
        if (isAbortError(err)) return;
        setError(err instanceof Error ? err.message : 'Failed to load subagents');
        setLoaded(true);
      });
    return () => controller.abort();
  }, []);

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
