import type { Ref } from 'react';
import type { ProjectRequest } from '@openjcockpit/api';
import {
  TextAreaField,
  TextField,
  ToolbarButton,
  formActionsClass,
  formClass,
  formGridClass,
  formTitleClass,
  Button,
} from '@openjcockpit/ui';
import { useTranslation } from '@openjcockpit/i18n';
import styles from './ProjectSettings.module.scss';

interface Props {
  isNew: boolean;
  form: ProjectRequest;
  onChange: (updater: (f: ProjectRequest) => ProjectRequest) => void;
  saving: boolean;
  nameInvalid: boolean;
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
          aria-invalid={nameInvalid ? true : undefined}
          aria-describedby={nameInvalid ? 'project-name-error' : undefined}
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
        <label className={styles.newProjectCheckbox}>
          <input
            type="checkbox"
            checked={form.newProject === 1}
            onChange={(e) => {
              // Read now: the updater may run after React has restored the controlled value.
              const checked = e.target.checked;
              onChange((f) => ({ ...f, newProject: checked ? 1 : 0 }));
            }}
          />
          {t('form.newProjectCheckbox')}
        </label>
      </div>
      <div className={formActionsClass}>
        <ToolbarButton onClick={onSave} disabled={saving}>
          {saving ? t('form.saving') : t('form.save')}
        </ToolbarButton>
        <Button variant="secondary" onClick={onCancel}>
          {t('form.cancel')}
        </Button>
      </div>
    </div>
  );
}
