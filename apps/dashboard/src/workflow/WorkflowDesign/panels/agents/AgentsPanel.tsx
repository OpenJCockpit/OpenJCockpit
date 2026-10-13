import { useState } from 'react';
import {
  createAgentSpec,
  createSubagentSpec,
  deleteAgentSpec,
  generateAgentSpec,
  generateSubagentSpec,
  listAgentSpecs,
  listSubagentSpecs,
  updateAgentSpec,
} from '../../../workflowApi';
import type { AgentSpec } from '../../../workflowTypes';
import { PromptGenerator } from '../shared/PromptGenerator';
import { AgentEditor } from './AgentEditor';
import { AgentsTable } from './AgentsTable';

const EMPTY_AGENT: AgentSpec = {
  name: '',
  description: '',
  role: '',
  instructions: '',
  subagentNames: [],
  skillNames: [],
  mcpTools: [],
};

export function AgentsPanel() {
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
        <AgentEditor
          isNew={editingName === 'new'}
          form={form}
          setForm={setForm}
          saving={saving}
          onSave={() => void handleSave()}
          onCancel={() => setEditingName(null)}
        />
      )}

      <AgentsTable items={items} onEdit={startEdit} onDelete={(name) => void handleDelete(name)} />
    </div>
  );
}
