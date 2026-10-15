import type { ProjectGitCredentialRequest } from '../../types';
import { SelectField } from '../FormFields/SelectField/SelectField';
import { TextField } from '../FormFields/TextField/TextField';
import { ToolbarButton } from '../ToolbarButton/ToolbarButton';
import { fieldHintClass, formClass, formGridClass, formTitleClass } from '../../ui/Form/Form';
import { Button } from '../../ui/Button/Button';

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
  return (
    <div className={formClass}>
      <h2 className={formTitleClass}>Git credentials</h2>
      {loading ? (
        <p className={fieldHintClass}>Loading current credentials...</p>
      ) : (
        <>
          <div className={formGridClass}>
            <SelectField
              label="Type"
              value={form.credentialType}
              onChange={(v) =>
                onChange((f) => ({
                  ...f,
                  credentialType: v as ProjectGitCredentialRequest['credentialType'],
                }))
              }
            >
              <option value="NONE">None</option>
              <option value="HTTPS_TOKEN">HTTPS Token</option>
              <option value="GITHUB_PAT">GitHub PAT</option>
              <option value="USERNAME_PASSWORD">Username + password</option>
              <option value="GITHUB_APP">GitHub App</option>
            </SelectField>
            <TextField
              label="Username"
              value={form.username ?? ''}
              onChange={(v) => onChange((f) => ({ ...f, username: v }))}
              placeholder="optional"
            />
            <TextField
              label="Secret / token / password"
              type="password"
              value={form.secret ?? ''}
              onChange={(v) => onChange((f) => ({ ...f, secret: v }))}
              placeholder={
                hasSecret
                  ? 'Leave empty to keep the current secret'
                  : 'Required — no secret set yet'
              }
            />
            <TextField
              label="GitHub API URL (GitHub Enterprise)"
              value={form.githubApiUrl ?? ''}
              onChange={(v) => onChange((f) => ({ ...f, githubApiUrl: v }))}
              placeholder="https://github.example.com/api/v3"
            />
          </div>
          <p className={fieldHintClass}>
            On save, a Git check is first run with these credentials; they are only stored if the
            check succeeds.
          </p>
        </>
      )}
      <div style={{ display: 'flex', gap: 12, marginTop: 16 }}>
        <ToolbarButton onClick={onSave} disabled={saving || loading}>
          {saving ? '⟳ Verifying and saving…' : 'Save'}
        </ToolbarButton>
        <Button variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </div>
  );
}
