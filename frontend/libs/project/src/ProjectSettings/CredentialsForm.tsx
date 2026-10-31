import type { ProjectGitCredentialRequest } from '@openjcockpit/api';
import { useTranslation } from '@openjcockpit/i18n';
import {
  SelectField,
  TextField,
  fieldHintClass,
  formActionsClass,
  formClass,
  formGridClass,
  formTitleClass,
  Button,
} from '@openjcockpit/ui';

interface Props {
  form: ProjectGitCredentialRequest;
  onChange: (updater: (f: ProjectGitCredentialRequest) => ProjectGitCredentialRequest) => void;
  hasSecret: boolean;
  loading: boolean;
  saving: boolean;
  onSave: () => void;
  onCancel: () => void;
}

export function CredentialsForm({
  form,
  onChange,
  hasSecret,
  loading,
  saving,
  onSave,
  onCancel,
}: Props) {
  const { t } = useTranslation('project');
  return (
    <div className={formClass}>
      <h2 className={formTitleClass}>{t('credentials.title')}</h2>
      {loading ? (
        <p className={fieldHintClass}>{t('credentials.loading')}</p>
      ) : (
        <>
          <div className={formGridClass}>
            <SelectField
              label={t('credentials.type')}
              value={form.credentialType}
              onChange={(v) =>
                onChange((f) => ({
                  ...f,
                  credentialType: v as ProjectGitCredentialRequest['credentialType'],
                }))
              }
              options={(
                ['NONE', 'HTTPS_TOKEN', 'GITHUB_PAT', 'USERNAME_PASSWORD', 'GITHUB_APP'] as const
              ).map((type) => ({ value: type, label: t(`credentials.types.${type}`) }))}
            />
            <TextField
              label={t('credentials.username')}
              value={form.username ?? ''}
              onChange={(v) => onChange((f) => ({ ...f, username: v }))}
              placeholder={t('credentials.usernamePlaceholder')}
            />
            <TextField
              label={t('credentials.secret')}
              type="password"
              value={form.secret ?? ''}
              onChange={(v) => onChange((f) => ({ ...f, secret: v }))}
              placeholder={
                hasSecret
                  ? t('credentials.secretKeepPlaceholder')
                  : t('credentials.secretRequiredPlaceholder')
              }
            />
            <TextField
              label={t('credentials.githubApiUrl')}
              value={form.githubApiUrl ?? ''}
              onChange={(v) => onChange((f) => ({ ...f, githubApiUrl: v }))}
              placeholder={t('credentials.githubApiUrlPlaceholder')}
            />
          </div>
          <p className={fieldHintClass}>{t('credentials.hint')}</p>
        </>
      )}
      <div className={formActionsClass}>
        <Button variant="primary" onClick={onSave} disabled={saving || loading}>
          {saving ? t('credentials.saving') : t('credentials.save')}
        </Button>
        <Button variant="secondary" onClick={onCancel}>
          {t('credentials.cancel')}
        </Button>
      </div>
    </div>
  );
}
