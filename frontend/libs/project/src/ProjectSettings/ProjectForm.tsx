import type { Ref } from 'react';
import type { ProjectRequest } from '@openjcockpit/api';
import {
  CheckboxField,
  TextAreaField,
  TextField,
  formActionsClass,
  formClass,
  formGridClass,
  formTitleClass,
  Button,
} from '@openjcockpit/ui';
import { useTranslation } from '@openjcockpit/i18n';

interface Props {
  isNew: boolean;
  form: ProjectRequest;
  onChange: (updater: (f: ProjectRequest) => ProjectRequest) => void;
  saving: boolean;
  nameInvalid: boolean;
  /** Message shown inline under the Name input while `nameInvalid`. */
  nameError?: string | null;
  nameInputRef: Ref<HTMLInputElement>;
  onSave: () => void;
  onCancel: () => void;
}

export function ProjectForm({
  isNew,
  form,
  onChange,
  saving,
  nameInvalid,
  nameError,
  nameInputRef,
  onSave,
  onCancel,
}: Props) {
  const { t } = useTranslation('project');
  return (
    <div className={formClass}>
      <h2 className={formTitleClass}>{isNew ? t('form.newTitle') : t('form.editTitle')}</h2>
      <div className={formGridClass}>
        <TextField
          label={t('form.name')}
          ref={nameInputRef}
          value={form.name}
          onChange={(v) => onChange((f) => ({ ...f, name: v }))}
          placeholder={t('form.namePlaceholder')}
          required
          error={nameInvalid ? nameError : undefined}
          errorId="project-name-error"
          aria-invalid={nameInvalid ? true : undefined}
          aria-describedby={nameInvalid && !nameError ? 'project-name-error' : undefined}
        />
        <TextField
          label={t('form.customerId')}
          value={form.customerId ?? ''}
          onChange={(v) => onChange((f) => ({ ...f, customerId: v }))}
          placeholder={t('form.customerIdPlaceholder')}
        />
        <TextField
          label={t('form.gitUrl')}
          value={form.gitUrl ?? ''}
          onChange={(v) => onChange((f) => ({ ...f, gitUrl: v }))}
          placeholder={t('form.gitUrlPlaceholder')}
        />
        <TextField
          label={t('form.branch')}
          value={form.defaultBranch ?? ''}
          onChange={(v) => onChange((f) => ({ ...f, defaultBranch: v }))}
          placeholder={t('form.branchPlaceholder')}
        />
        <TextField
          label={t('form.environment')}
          value={form.environment ?? ''}
          onChange={(v) => onChange((f) => ({ ...f, environment: v }))}
          placeholder={t('form.environmentPlaceholder')}
        />
        <TextField
          label={t('form.owner')}
          value={form.owner ?? ''}
          onChange={(v) => onChange((f) => ({ ...f, owner: v }))}
          placeholder={t('form.ownerPlaceholder')}
        />
        <TextAreaField
          label={t('form.description')}
          wide
          rows={3}
          value={form.description ?? ''}
          onChange={(v) => onChange((f) => ({ ...f, description: v }))}
          placeholder={t('form.descriptionPlaceholder')}
        />
        <CheckboxField
          label={t('form.newProjectCheckbox')}
          checked={form.newProject === 1}
          // The checked value is passed in, so it is read before any updater runs.
          onChange={(checked) => onChange((f) => ({ ...f, newProject: checked ? 1 : 0 }))}
        />
      </div>
      <div className={formActionsClass}>
        <Button variant="primary" onClick={onSave} disabled={saving}>
          {saving ? t('form.saving') : t('form.save')}
        </Button>
        <Button variant="secondary" onClick={onCancel}>
          {t('form.cancel')}
        </Button>
      </div>
    </div>
  );
}
