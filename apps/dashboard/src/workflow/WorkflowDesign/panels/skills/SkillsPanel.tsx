import { useEffect, useMemo, useRef, useState } from 'react';
import { ToolbarButton } from '../../../../components/ToolbarButton/ToolbarButton';
import { StatusMessage } from '../../../../components/StatusMessage/StatusMessage';
import {
  createSkillSpec,
  deleteSkillSpec,
  generateSkillSpec,
  getSkillCatalog,
  updateSkillSpec,
} from '../../../workflowApi';
import type { SkillCatalog, SkillSpec } from '../../../workflowTypes';
import { describeSourceOutcome } from '../../utils';
import { PromptGenerator } from '../shared/PromptGenerator';
import { SkillEditor } from './SkillEditor';
import { SkillsTable } from './SkillsTable';
import type { SkillRow } from './SkillsTable';
import { fieldErrorClass } from '../../../../ui/Form/Form';

const EMPTY_SKILL: SkillSpec = {
  name: '',
  description: '',
  inputContract: '',
  outputContract: '',
  executionInstructions: '',
  mcpTools: [],
  policyNotes: '',
};

const EMPTY_CATALOG: SkillCatalog = { localSkills: [], externalSkills: [], sources: [] };

export function SkillsPanel() {
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
        <ToolbarButton onClick={startCreate}>+ New skill</ToolbarButton>
        <button className="back-button" onClick={() => void loadCatalog()}>
          ⟳ Refresh
        </button>
      </div>
      {error && <StatusMessage variant="error">{error}</StatusMessage>}

      {editingName && (
        <SkillEditor
          isNew={editingName === 'new'}
          form={form}
          setForm={setForm}
          saving={saving}
          onSave={() => void handleSave()}
          onCancel={() => setEditingName(null)}
        />
      )}

      <div role="status" aria-live="polite">
        {loading
          ? 'Loading skills…'
          : statusMessages.map((message, index) => <div key={index}>{message}</div>)}
      </div>

      {loadError ? (
        <p className={fieldErrorClass} role="alert">
          {loadError}
        </p>
      ) : (
        <SkillsTable rows={rows} onEdit={startEdit} onDelete={(name) => void handleDelete(name)} />
      )}
    </div>
  );
}
