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
        <label>
          Enabled
          <input
            type="checkbox"
            checked={opaForm.enabled}
            onChange={(e) => setOpaForm((f) => ({ ...f, enabled: e.target.checked }))}
          />
        </label>
        <label>
          Base URL
          <input
            className="settings-input"
            value={opaForm.baseUrl}
            onChange={(e) => setOpaForm((f) => ({ ...f, baseUrl: e.target.value }))}
            placeholder="http://localhost:8181"
          />
        </label>
        <label>
          Health check path
          <input
            className="settings-input"
            value={opaForm.healthPath}
            onChange={(e) => setOpaForm((f) => ({ ...f, healthPath: e.target.value }))}
            placeholder="/health"
          />
        </label>
        <label>
          Policy decision endpoint
          <input
            className="settings-input"
            value={opaForm.policyPath}
            onChange={(e) => setOpaForm((f) => ({ ...f, policyPath: e.target.value }))}
            placeholder="/v1/data/openjcockpit/workflow/decision"
          />
        </label>
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
        <label>
          Environment label
          <input
            className="settings-input"
            value={opaForm.environment}
            onChange={(e) => setOpaForm((f) => ({ ...f, environment: e.target.value }))}
            placeholder="production"
          />
        </label>
        <label>
          Customer/tenant label
          <input
            className="settings-input"
            value={opaForm.customerLabel}
            onChange={(e) => setOpaForm((f) => ({ ...f, customerLabel: e.target.value }))}
            placeholder="noordzee"
          />
        </label>
        <label>
          Project label
          <input
            className="settings-input"
            value={opaForm.projectLabel}
            onChange={(e) => setOpaForm((f) => ({ ...f, projectLabel: e.target.value }))}
            placeholder="onboarding"
          />
        </label>
        <label>
          Decision logging enabled
          <input
            type="checkbox"
            checked={opaForm.decisionLoggingEnabled}
            onChange={(e) =>
              setOpaForm((f) => ({ ...f, decisionLoggingEnabled: e.target.checked }))
            }
          />
        </label>
        <label>
          Decision log export enabled
          <input
            type="checkbox"
            checked={opaForm.decisionLogExportEnabled}
            onChange={(e) =>
              setOpaForm((f) => ({ ...f, decisionLogExportEnabled: e.target.checked }))
            }
          />
        </label>
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

      <div className="settings-form">
        <h2 className="settings-form-title">Hermes Agent integration</h2>
        <div className="settings-form-grid">
          <label>
            Enabled
            <input
              type="checkbox"
              checked={hermesForm.enabled}
              onChange={(e) => setHermesForm((f) => ({ ...f, enabled: e.target.checked }))}
            />
          </label>
          <label>
            Signal source / endpoint
            <input
              className="settings-input"
              value={hermesForm.endpointUrl}
              onChange={(e) => setHermesForm((f) => ({ ...f, endpointUrl: e.target.value }))}
              placeholder="https://hermes.example.com/signals"
            />
          </label>
          <label>
            Signal type
            <input
              className="settings-input"
              value={hermesForm.signalType}
              onChange={(e) => setHermesForm((f) => ({ ...f, signalType: e.target.value }))}
              placeholder="issue.created"
            />
          </label>
          <label>
            Workflow ID to start
            <input
              className="settings-input"
              value={hermesForm.workflowId}
              onChange={(e) => setHermesForm((f) => ({ ...f, workflowId: e.target.value }))}
              placeholder="wf-onboarding"
            />
          </label>
          <label style={{ gridColumn: '1 / -1' }}>
            Auth token{' '}
            {hasAuthTokens.hermes && (
              <span className="settings-desc">(already configured — leave blank to keep)</span>
            )}
            <input
              className="settings-input"
              type="password"
              value={hermesForm.authToken}
              onChange={(e) => setHermesForm((f) => ({ ...f, authToken: e.target.value }))}
              placeholder={hasAuthTokens.hermes ? '••••••••' : ''}
            />
          </label>
        </div>
        <button
          className="button button--start"
          onClick={() => void handleSaveHermes()}
          disabled={saving === 'hermes'}
        >
          {saving === 'hermes' ? '⟳ Saving…' : 'Save Hermes config'}
        </button>
      </div>

      <div className="settings-form">
        <h2 className="settings-form-title">Jira connector</h2>
        <div className="settings-form-grid">
          <label>
            Enabled
            <input
              type="checkbox"
              checked={jiraForm.enabled}
              onChange={(e) => setJiraForm((f) => ({ ...f, enabled: e.target.checked }))}
            />
          </label>
          <label>
            Jira base URL
            <input
              className="settings-input"
              value={jiraForm.baseUrl}
              onChange={(e) => setJiraForm((f) => ({ ...f, baseUrl: e.target.value }))}
              placeholder="https://your-org.atlassian.net"
            />
          </label>
          <label>
            Project key
            <input
              className="settings-input"
              value={jiraForm.projectKey}
              onChange={(e) => setJiraForm((f) => ({ ...f, projectKey: e.target.value }))}
              placeholder="NL"
            />
          </label>
          <label>
            Issue type mapping
            <input
              className="settings-input"
              value={jiraForm.issueTypeMapping}
              onChange={(e) => setJiraForm((f) => ({ ...f, issueTypeMapping: e.target.value }))}
              placeholder="Bug=bugfix-workflow"
            />
          </label>
          <label>
            Workflow ID to start
            <input
              className="settings-input"
              value={jiraForm.workflowId}
              onChange={(e) => setJiraForm((f) => ({ ...f, workflowId: e.target.value }))}
              placeholder="wf-onboarding"
            />
          </label>
          <label style={{ gridColumn: '1 / -1' }}>
            Auth token{' '}
            {hasAuthTokens.jira && (
              <span className="settings-desc">(already configured — leave blank to keep)</span>
            )}
            <input
              className="settings-input"
              type="password"
              value={jiraForm.authToken}
              onChange={(e) => setJiraForm((f) => ({ ...f, authToken: e.target.value }))}
              placeholder={hasAuthTokens.jira ? '••••••••' : ''}
            />
          </label>
        </div>
        <button
          className="button button--start"
          onClick={() => void handleSaveJira()}
          disabled={saving === 'jira'}
        >
          {saving === 'jira' ? '⟳ Saving…' : 'Save Jira config'}
        </button>
      </div>

      <div className="settings-form">
        <h2 className="settings-form-title">Project document folder</h2>
        <div className="settings-form-grid">
          <label>
            Project name
            <input className="settings-input" value={project.name} disabled />
          </label>
          <label>
            Folder name (CamelCase, no spaces — derived from the project name)
            <input className="settings-input" value={folderName} disabled />
          </label>
          <label>
            Folder base location
            <input
              className="settings-input"
              value={folderBasePath}
              onChange={(e) => handleFolderBasePathChange(e.target.value)}
              placeholder="/documents"
            />
          </label>
          <label>
            Folder location (folder name is always appended automatically)
            <input className="settings-input" value={folderForm.folderPath} disabled />
          </label>
          <label>
            Allowed document types
            <input
              className="settings-input"
              value={folderForm.allowedDocumentTypes}
              onChange={(e) =>
                setFolderForm((f) => ({ ...f, allowedDocumentTypes: e.target.value }))
              }
              placeholder="pdf,docx,md"
            />
          </label>
          <label>
            Workflow ID to start on new file
            <input
              className="settings-input"
              value={folderForm.workflowId}
              onChange={(e) => setFolderForm((f) => ({ ...f, workflowId: e.target.value }))}
              placeholder="wf-onboarding"
            />
          </label>
          <label>
            File trigger enabled
            <input
              type="checkbox"
              checked={folderForm.fileTriggerEnabled}
              onChange={(e) =>
                setFolderForm((f) => ({ ...f, fileTriggerEnabled: e.target.checked }))
              }
            />
          </label>
        </div>
        <button
          className="button button--start"
          onClick={() => void handleSaveFolder()}
          disabled={saving === 'folder'}
        >
          {saving === 'folder' ? '⟳ Saving…' : 'Save document folder config'}
        </button>
      </div>
    </section>
  );
}
