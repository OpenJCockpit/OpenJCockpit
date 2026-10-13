import './WorkflowConfiguration.scss';
import { useState } from 'react';
import type { Project } from '../../types';
import {
  checkOpaHealth,
  getDocumentFolderConfig,
  getHermesConfig,
  getJiraConfig,
  getOpaConfig,
  saveDocumentFolderConfig,
  saveHermesConfig,
  saveJiraConfig,
  saveOpaConfig,
} from '../workflowApi';
import {
  mockDocumentFolderConfig,
  mockHermesConfig,
  mockJiraConfig,
  mockOpaConfig,
} from '../mockWorkflows';
import { StatusChip } from '../../components/StatusChip/StatusChip';
import type { StatusKind } from '../../types';
import {
  CheckboxField,
  IntegrationConfigForm,
  SecretField,
  TextField,
} from '../../components/FormFields/FormFields';

interface Props {
  project: Project | null;
}

const DEFAULT_FOLDER_BASE_PATH = '/documents';

// Folders can't contain spaces, so the folder name is always derived as a space-free
// CamelCase (PascalCase) version of the project name, e.g. "Absence Pro Solutions" -> "AbsenceProSolutions".
function toCamelCaseFolderName(input: string): string {
  return input
    .split(/[^a-zA-Z0-9]+/)
    .filter(Boolean)
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join('');
}

function basePathOf(fullPath: string): string {
  const trimmed = fullPath.replace(/\/+$/, '');
  const lastSlash = trimmed.lastIndexOf('/');
  return lastSlash > 0 ? trimmed.slice(0, lastSlash) : '';
}

// The folder name is always appended as the trailing segment of the location — never a
// separately editable part of the path — so the base and name are joined here rather than
// letting users type the full path directly.
function joinFolderPath(basePath: string, folderName: string): string {
  const trimmedBase = basePath.replace(/\/+$/, '');
  return trimmedBase ? `${trimmedBase}/${folderName}` : `/${folderName}`;
}

interface OpaFormState {
  enabled: boolean;
  baseUrl: string;
  policyPath: string;
  healthPath: string;
  timeoutSeconds: string;
  failMode: string;
  decisionLoggingEnabled: boolean;
  decisionLogExportEnabled: boolean;
  environment: string;
  customerLabel: string;
  projectLabel: string;
  authToken: string;
}

type OpaStatus =
  'not_configured' | 'disabled' | 'unreachable' | 'reachable' | 'enforcing' | 'unavailable';

function opaStatusFor(enabled: boolean, baseUrl: string, reachable: boolean | null): OpaStatus {
  if (!baseUrl.trim()) return 'not_configured';
  if (enabled) return reachable ? 'enforcing' : 'unavailable';
  if (reachable === null) return 'disabled';
  return reachable ? 'reachable' : 'unreachable';
}

const OPA_STATUS_LABELS: Record<OpaStatus, string> = {
  not_configured: 'Not configured',
  disabled: 'Disabled',
  unreachable: 'Configured but unreachable',
  reachable: 'Configured and reachable',
  enforcing: 'Enabled and enforcing',
  unavailable: 'Enabled but policy unavailable',
};

const OPA_STATUS_KINDS: Record<OpaStatus, StatusKind> = {
  not_configured: 'waiting',
  disabled: 'waiting',
  unreachable: 'changes',
  reachable: 'ok',
  enforcing: 'ok',
  unavailable: 'changes',
};

interface HermesFormState {
  enabled: boolean;
  endpointUrl: string;
  authToken: string;
  signalType: string;
  workflowId: string;
}

interface JiraFormState {
  enabled: boolean;
  baseUrl: string;
  projectKey: string;
  authToken: string;
  issueTypeMapping: string;
  workflowId: string;
}

interface FolderFormState {
  folderPath: string;
  fileTriggerEnabled: boolean;
  allowedDocumentTypes: string;
  workflowId: string;
}

export function WorkflowConfiguration({ project }: Props) {
  const [loaded, setLoaded] = useState(false);
  const [hasAuthTokens, setHasAuthTokens] = useState({ hermes: false, jira: false });
  const [hermesForm, setHermesForm] = useState<HermesFormState>({
    enabled: false,
    endpointUrl: '',
    authToken: '',
    signalType: '',
    workflowId: '',
  });
  const [jiraForm, setJiraForm] = useState<JiraFormState>({
    enabled: false,
    baseUrl: '',
    projectKey: '',
    authToken: '',
    issueTypeMapping: '',
    workflowId: '',
  });
  const [folderForm, setFolderForm] = useState<FolderFormState>({
    folderPath: '',
    fileTriggerEnabled: false,
    allowedDocumentTypes: '',
    workflowId: '',
  });
  const [folderName, setFolderName] = useState('');
  const [folderBasePath, setFolderBasePath] = useState(DEFAULT_FOLDER_BASE_PATH);
  const [saving, setSaving] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [savedMessage, setSavedMessage] = useState<string | null>(null);

  const [opaLoaded, setOpaLoaded] = useState(false);
  const [opaHasAuthToken, setOpaHasAuthToken] = useState(false);
  const [opaReachable, setOpaReachable] = useState<boolean | null>(null);
  const [opaForm, setOpaForm] = useState<OpaFormState>({
    enabled: false,
    baseUrl: '',
    policyPath: '',
    healthPath: '',
    timeoutSeconds: '3',
    failMode: 'FAIL_CLOSED',
    decisionLoggingEnabled: true,
    decisionLogExportEnabled: true,
    environment: '',
    customerLabel: '',
    projectLabel: '',
    authToken: '',
  });
  const [opaSaving, setOpaSaving] = useState(false);
  const [opaError, setOpaError] = useState<string | null>(null);
  const [opaSavedMessage, setOpaSavedMessage] = useState<string | null>(null);

  if (!opaLoaded) {
    void getOpaConfig()
      .catch(() => mockOpaConfig)
      .then((config) => {
        setOpaForm({
          enabled: config.enabled,
          baseUrl: config.baseUrl,
          policyPath: config.policyPath,
          healthPath: config.healthPath,
          timeoutSeconds: String(config.timeoutSeconds),
          failMode: config.failMode,
          decisionLoggingEnabled: config.decisionLoggingEnabled,
          decisionLogExportEnabled: config.decisionLogExportEnabled,
          environment: config.environment,
          customerLabel: config.customerLabel,
          projectLabel: config.projectLabel,
          authToken: '',
        });
        setOpaHasAuthToken(config.hasAuthToken);
        setOpaLoaded(true);
        void checkOpaHealth().then(setOpaReachable);
      });
  }

  async function handleSaveOpa() {
    setOpaSaving(true);
    setOpaError(null);
    setOpaSavedMessage(null);
    try {
      const result = await saveOpaConfig({
        enabled: opaForm.enabled,
        baseUrl: opaForm.baseUrl,
        policyPath: opaForm.policyPath,
        healthPath: opaForm.healthPath,
        timeoutSeconds: Number(opaForm.timeoutSeconds) || undefined,
        failMode: opaForm.failMode,
        decisionLoggingEnabled: opaForm.decisionLoggingEnabled,
        decisionLogExportEnabled: opaForm.decisionLogExportEnabled,
        environment: opaForm.environment,
        customerLabel: opaForm.customerLabel,
        projectLabel: opaForm.projectLabel,
        authToken: opaForm.authToken || null,
      });
      setOpaHasAuthToken(result.hasAuthToken);
      setOpaForm((f) => ({ ...f, authToken: '' }));
      setOpaSavedMessage('OPA configuration saved.');
      setOpaReachable(await checkOpaHealth());
    } catch (err) {
      setOpaError('Failed to save OPA configuration.');
      console.warn(err);
    } finally {
      setOpaSaving(false);
    }
  }

  const opaStatus = opaStatusFor(opaForm.enabled, opaForm.baseUrl, opaReachable);

  const opaSection = (
    <div className="settings-form">
      <h2 className="settings-form-title">
        Open Policy Agent{' '}
        <StatusChip label={OPA_STATUS_LABELS[opaStatus]} kind={OPA_STATUS_KINDS[opaStatus]} />
      </h2>
      <p className="settings-desc">
        When enabled and reachable, OPA is the mandatory runtime policy gate for workflow starts,
        agent/subagent/skill activation, and MCP tool invocation. This configuration is global — it
        applies to every project.
      </p>
      {opaError && <p className="settings-error">{opaError}</p>}
      {opaSavedMessage && <p className="workflow-saved-message">{opaSavedMessage}</p>}
      <div className="settings-form-grid">
        <CheckboxField
          label="Enabled"
          checked={opaForm.enabled}
          onChange={(v) => setOpaForm((f) => ({ ...f, enabled: v }))}
        />
        <TextField
          label="Base URL"
          value={opaForm.baseUrl}
          onChange={(v) => setOpaForm((f) => ({ ...f, baseUrl: v }))}
          placeholder="http://localhost:8181"
        />
        <TextField
          label="Health check path"
          value={opaForm.healthPath}
          onChange={(v) => setOpaForm((f) => ({ ...f, healthPath: v }))}
          placeholder="/health"
        />
        <TextField
          label="Policy decision endpoint"
          value={opaForm.policyPath}
          onChange={(v) => setOpaForm((f) => ({ ...f, policyPath: v }))}
          placeholder="/v1/data/openjcockpit/workflow/decision"
        />
        <label>
          Timeout (seconds)
          <input
            className="settings-input"
            type="number"
            min="1"
            value={opaForm.timeoutSeconds}
            onChange={(e) => setOpaForm((f) => ({ ...f, timeoutSeconds: e.target.value }))}
          />
        </label>
        <label>
          Fail mode
          <select
            className="settings-input"
            value={opaForm.failMode}
            onChange={(e) => setOpaForm((f) => ({ ...f, failMode: e.target.value }))}
          >
            <option value="FAIL_CLOSED">
              FAIL_CLOSED — block sensitive actions when unreachable
            </option>
            <option value="FAIL_OPEN">FAIL_OPEN — allow but log as policy-check-unavailable</option>
          </select>
        </label>
        <TextField
          label="Environment label"
          value={opaForm.environment}
          onChange={(v) => setOpaForm((f) => ({ ...f, environment: v }))}
          placeholder="production"
        />
        <TextField
          label="Customer/tenant label"
          value={opaForm.customerLabel}
          onChange={(v) => setOpaForm((f) => ({ ...f, customerLabel: v }))}
          placeholder="noordzee"
        />
        <TextField
          label="Project label"
          value={opaForm.projectLabel}
          onChange={(v) => setOpaForm((f) => ({ ...f, projectLabel: v }))}
          placeholder="onboarding"
        />
        <CheckboxField
          label="Decision logging enabled"
          checked={opaForm.decisionLoggingEnabled}
          onChange={(v) => setOpaForm((f) => ({ ...f, decisionLoggingEnabled: v }))}
        />
        <CheckboxField
          label="Decision log export enabled"
          checked={opaForm.decisionLogExportEnabled}
          onChange={(v) => setOpaForm((f) => ({ ...f, decisionLogExportEnabled: v }))}
        />
        <label style={{ gridColumn: '1 / -1' }}>
          Auth token{' '}
          {opaHasAuthToken && (
            <span className="settings-desc">(already configured — leave blank to keep)</span>
          )}
          <input
            className="settings-input"
            type="password"
            value={opaForm.authToken}
            onChange={(e) => setOpaForm((f) => ({ ...f, authToken: e.target.value }))}
            placeholder={opaHasAuthToken ? '••••••••' : ''}
          />
        </label>
      </div>
      <button
        className="button button--start"
        onClick={() => void handleSaveOpa()}
        disabled={opaSaving}
      >
        {opaSaving ? '⟳ Saving…' : 'Save OPA config'}
      </button>
    </div>
  );

  if (!project) {
    return (
      <section className="workflow-configuration">
        {opaSection}
        <p className="settings-desc">
          Select a project from the dashboard header to configure its workflow input sources.
        </p>
      </section>
    );
  }

  if (!loaded) {
    void Promise.all([
      getHermesConfig(project.id).catch(() => mockHermesConfig),
      getJiraConfig(project.id).catch(() => mockJiraConfig),
      getDocumentFolderConfig(project.id).catch(() => ({
        ...mockDocumentFolderConfig,
        folderName: project.name,
      })),
    ]).then(([hermes, jira, folder]) => {
      setHermesForm({
        enabled: hermes.enabled,
        endpointUrl: hermes.endpointUrl ?? '',
        authToken: '',
        signalType: hermes.signalType ?? '',
        workflowId: hermes.workflowId ?? '',
      });
      setJiraForm({
        enabled: jira.enabled,
        baseUrl: jira.baseUrl ?? '',
        projectKey: jira.projectKey ?? '',
        authToken: '',
        issueTypeMapping: jira.issueTypeMapping ?? '',
        workflowId: jira.workflowId ?? '',
      });
      const camelFolderName = toCamelCaseFolderName(folder.folderName || project.name);
      const loadedPath = folder.folderPath ?? '';
      const basePath = loadedPath
        ? basePathOf(loadedPath) || DEFAULT_FOLDER_BASE_PATH
        : DEFAULT_FOLDER_BASE_PATH;
      setFolderForm({
        folderPath: joinFolderPath(basePath, camelFolderName),
        fileTriggerEnabled: folder.fileTriggerEnabled,
        allowedDocumentTypes: folder.allowedDocumentTypes ?? '',
        workflowId: folder.workflowId ?? '',
      });
      setFolderName(camelFolderName);
      setFolderBasePath(basePath);
      setHasAuthTokens({ hermes: hermes.hasAuthToken, jira: jira.hasAuthToken });
      setLoaded(true);
    });
    return (
      <section className="workflow-configuration">
        {opaSection}
        <div className="auth-loading">Loading configuration...</div>
      </section>
    );
  }

  async function handleSaveHermes() {
    if (!project) return;
    setSaving('hermes');
    setError(null);
    setSavedMessage(null);
    try {
      const result = await saveHermesConfig(project.id, {
        enabled: hermesForm.enabled,
        endpointUrl: hermesForm.endpointUrl,
        authToken: hermesForm.authToken || null,
        signalType: hermesForm.signalType,
        workflowId: hermesForm.workflowId,
      });
      setHasAuthTokens((prev) => ({ ...prev, hermes: result.hasAuthToken }));
      setHermesForm((f) => ({ ...f, authToken: '' }));
      setSavedMessage('Hermes configuration saved.');
    } catch (err) {
      setError('Failed to save Hermes configuration.');
      console.warn(err);
    } finally {
      setSaving(null);
    }
  }

  async function handleSaveJira() {
    if (!project) return;
    setSaving('jira');
    setError(null);
    setSavedMessage(null);
    try {
      const result = await saveJiraConfig(project.id, {
        enabled: jiraForm.enabled,
        baseUrl: jiraForm.baseUrl,
        projectKey: jiraForm.projectKey,
        authToken: jiraForm.authToken || null,
        issueTypeMapping: jiraForm.issueTypeMapping,
        workflowId: jiraForm.workflowId,
      });
      setHasAuthTokens((prev) => ({ ...prev, jira: result.hasAuthToken }));
      setJiraForm((f) => ({ ...f, authToken: '' }));
      setSavedMessage('Jira configuration saved.');
    } catch (err) {
      setError('Failed to save Jira configuration.');
      console.warn(err);
    } finally {
      setSaving(null);
    }
  }

  function handleFolderBasePathChange(basePath: string) {
    setFolderBasePath(basePath);
    setFolderForm((f) => ({ ...f, folderPath: joinFolderPath(basePath, folderName) }));
  }

  async function handleSaveFolder() {
    if (!project) return;
    setSaving('folder');
    setError(null);
    setSavedMessage(null);
    try {
      await saveDocumentFolderConfig(project.id, {
        folderPath: folderForm.folderPath,
        fileTriggerEnabled: folderForm.fileTriggerEnabled,
        allowedDocumentTypes: folderForm.allowedDocumentTypes,
        workflowId: folderForm.workflowId,
      });
      setSavedMessage('Document folder configuration saved.');
    } catch (err) {
      setError('Failed to save document folder configuration.');
      console.warn(err);
    } finally {
      setSaving(null);
    }
  }

  return (
    <section className="workflow-configuration">
      {opaSection}

      {error && <p className="settings-error">{error}</p>}
      {savedMessage && <p className="workflow-saved-message">{savedMessage}</p>}

      <IntegrationConfigForm
        title="Hermes Agent integration"
        saveLabel="Save Hermes config"
        saving={saving === 'hermes'}
        onSave={() => void handleSaveHermes()}
      >
        <CheckboxField
          label="Enabled"
          checked={hermesForm.enabled}
          onChange={(v) => setHermesForm((f) => ({ ...f, enabled: v }))}
        />
        <TextField
          label="Signal source / endpoint"
          value={hermesForm.endpointUrl}
          onChange={(v) => setHermesForm((f) => ({ ...f, endpointUrl: v }))}
          placeholder="https://hermes.example.com/signals"
        />
        <TextField
          label="Signal type"
          value={hermesForm.signalType}
          onChange={(v) => setHermesForm((f) => ({ ...f, signalType: v }))}
          placeholder="issue.created"
        />
        <TextField
          label="Workflow ID to start"
          value={hermesForm.workflowId}
          onChange={(v) => setHermesForm((f) => ({ ...f, workflowId: v }))}
          placeholder="wf-onboarding"
        />
        <SecretField
          label="Auth token"
          hasValue={hasAuthTokens.hermes}
          value={hermesForm.authToken}
          onChange={(v) => setHermesForm((f) => ({ ...f, authToken: v }))}
        />
      </IntegrationConfigForm>

      <IntegrationConfigForm
        title="Jira connector"
        saveLabel="Save Jira config"
        saving={saving === 'jira'}
        onSave={() => void handleSaveJira()}
      >
        <CheckboxField
          label="Enabled"
          checked={jiraForm.enabled}
          onChange={(v) => setJiraForm((f) => ({ ...f, enabled: v }))}
        />
        <TextField
          label="Jira base URL"
          value={jiraForm.baseUrl}
          onChange={(v) => setJiraForm((f) => ({ ...f, baseUrl: v }))}
          placeholder="https://your-org.atlassian.net"
        />
        <TextField
          label="Project key"
          value={jiraForm.projectKey}
          onChange={(v) => setJiraForm((f) => ({ ...f, projectKey: v }))}
          placeholder="NL"
        />
        <TextField
          label="Issue type mapping"
          value={jiraForm.issueTypeMapping}
          onChange={(v) => setJiraForm((f) => ({ ...f, issueTypeMapping: v }))}
          placeholder="Bug=bugfix-workflow"
        />
        <TextField
          label="Workflow ID to start"
          value={jiraForm.workflowId}
          onChange={(v) => setJiraForm((f) => ({ ...f, workflowId: v }))}
          placeholder="wf-onboarding"
        />
        <SecretField
          label="Auth token"
          hasValue={hasAuthTokens.jira}
          value={jiraForm.authToken}
          onChange={(v) => setJiraForm((f) => ({ ...f, authToken: v }))}
        />
      </IntegrationConfigForm>

      <IntegrationConfigForm
        title="Project document folder"
        saveLabel="Save document folder config"
        saving={saving === 'folder'}
        onSave={() => void handleSaveFolder()}
      >
        <label>
          Project name
          <input className="settings-input" value={project.name} disabled />
        </label>
        <label>
          Folder name (CamelCase, no spaces — derived from the project name)
          <input className="settings-input" value={folderName} disabled />
        </label>
        <TextField
          label="Folder base location"
          value={folderBasePath}
          onChange={(v) => handleFolderBasePathChange(v)}
          placeholder="/documents"
        />
        <label>
          Folder location (folder name is always appended automatically)
          <input className="settings-input" value={folderForm.folderPath} disabled />
        </label>
        <TextField
          label="Allowed document types"
          value={folderForm.allowedDocumentTypes}
          onChange={(v) => setFolderForm((f) => ({ ...f, allowedDocumentTypes: v }))}
          placeholder="pdf,docx,md"
        />
        <TextField
          label="Workflow ID to start on new file"
          value={folderForm.workflowId}
          onChange={(v) => setFolderForm((f) => ({ ...f, workflowId: v }))}
          placeholder="wf-onboarding"
        />
        <CheckboxField
          label="File trigger enabled"
          checked={folderForm.fileTriggerEnabled}
          onChange={(v) => setFolderForm((f) => ({ ...f, fileTriggerEnabled: v }))}
        />
      </IntegrationConfigForm>
    </section>
  );
}
