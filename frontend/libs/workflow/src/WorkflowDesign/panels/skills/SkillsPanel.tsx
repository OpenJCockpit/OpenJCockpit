import { useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { ToolbarButton, StatusMessage, fieldErrorClass, Button } from '@openjcockpit/ui';
import {
  createSkillSpec,
  deleteSkillSpec,
  generateSkillSpec,
  updateSkillSpec,
} from '@openjcockpit/api';
import type { SkillCatalog, SkillSpec } from '@openjcockpit/api';
import { describeSourceOutcome } from '../../utils';
import { PromptGenerator } from '../shared/PromptGenerator';
import { SkillEditor } from './SkillEditor';
import { SkillsTable } from './SkillsTable';
import { skillCatalogQueryKey, useSkillCatalog } from './useSkillCatalog';
import type { SkillRow } from './SkillsTable';
import WorkflowDesignStyles from '../../WorkflowDesign.module.scss';

const EMPTY_SKILL: SkillSpec = {
  name: '',
  description: '',
  inputContract: '',
  outputContract: '',
  executionInstructions: '',
  mcpTools: [],
  policyNotes: '',
};

export function SkillsPanel() {
  const queryClient = useQueryClient();
  const query = useSkillCatalog();
  const [editingName, setEditingName] = useState<string | null>(null);
  const [form, setForm] = useState<SkillSpec>(EMPTY_SKILL);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // A refresh clears the table and any load error until the new catalog arrives.
  const loading = query.isFetching;
  const catalog = loading ? undefined : query.data;
  const loadError =
    !loading && query.isError
      ? query.error instanceof Error
        ? query.error.message
        : 'Failed to load skill catalog.'
      : null;

  function updateCatalog(update: (base: SkillCatalog) => SkillCatalog) {
    queryClient.setQueryData<SkillCatalog>(skillCatalogQueryKey, (prev) => prev && update(prev));
  }

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

  async function handleSave(editingName: string) {
    setSaving(true);
    setError(null);
    try {
      const saved =
        editingName === 'new'
          ? await createSkillSpec(form)
          : await updateSkillSpec(editingName, form);
      updateCatalog((base) => {
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
      updateCatalog((base) => ({
        ...base,
        localSkills: base.localSkills.filter((s) => s.name !== name),
      }));
    } catch (err) {
      setError('Failed to delete skill definition.');
      console.warn(err);
    }
  }

  return (
    <div>
      <PromptGenerator kind="skill" onGenerate={handleGenerate} />
      <div className={WorkflowDesignStyles.workflowDesignToolbar}>
        <ToolbarButton onClick={startCreate}>+ New skill</ToolbarButton>
        <Button variant="secondary" onClick={() => void query.refetch()}>
          ⟳ Refresh
        </Button>
      </div>
      {error && <StatusMessage variant="error">{error}</StatusMessage>}

      {editingName && (
        <SkillEditor
          isNew={editingName === 'new'}
          form={form}
          setForm={setForm}
          saving={saving}
          onSave={() => void handleSave(editingName)}
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
