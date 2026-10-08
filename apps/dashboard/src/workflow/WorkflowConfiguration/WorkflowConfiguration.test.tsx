import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { WorkflowConfiguration } from './WorkflowConfiguration';
import * as workflowApi from '../workflowApi';
import type { DocumentFolderConfig, OpaConfig } from '../workflowTypes';
import type { Project } from '../../types';

vi.mock('../workflowApi');

function opaConfig(overrides: Partial<OpaConfig> = {}): OpaConfig {
  return {
    enabled: false,
    baseUrl: '',
    policyPath: '/v1/data/openjcockpit/workflow/decision',
    healthPath: '/health',
    timeoutSeconds: 3,
    failMode: 'FAIL_CLOSED',
    decisionLoggingEnabled: true,
    decisionLogExportEnabled: true,
    environment: '',
    customerLabel: '',
    projectLabel: '',
    hasAuthToken: false,
    ...overrides,
  };
}

describe('WorkflowConfiguration — OPA section', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(workflowApi.getOpaConfig).mockResolvedValue(opaConfig());
    vi.mocked(workflowApi.checkOpaHealth).mockResolvedValue(false);
    vi.mocked(workflowApi.saveOpaConfig).mockResolvedValue(opaConfig());
  });

  it('shows "Not configured" when no base URL has ever been set', async () => {
    await act(async () => {
      render(<WorkflowConfiguration project={null} />);
    });

    expect(await screen.findByText('Not configured')).toBeInTheDocument();
  });

  it('shows "Configured and reachable" when disabled, a base URL is set, and the health check succeeds', async () => {
    vi.mocked(workflowApi.getOpaConfig).mockResolvedValue(
      opaConfig({ baseUrl: 'http://localhost:8181' }),
    );
    vi.mocked(workflowApi.checkOpaHealth).mockResolvedValue(true);

    await act(async () => {
      render(<WorkflowConfiguration project={null} />);
    });

    expect(await screen.findByText('Configured and reachable')).toBeInTheDocument();
  });

  it('shows "Configured but unreachable" when disabled, a base URL is set, and the health check fails', async () => {
    vi.mocked(workflowApi.getOpaConfig).mockResolvedValue(
      opaConfig({ baseUrl: 'http://localhost:8181' }),
    );
    vi.mocked(workflowApi.checkOpaHealth).mockResolvedValue(false);

    await act(async () => {
      render(<WorkflowConfiguration project={null} />);
    });

    expect(await screen.findByText('Configured but unreachable')).toBeInTheDocument();
  });

  it('shows "Enabled and enforcing" when enabled and reachable', async () => {
    vi.mocked(workflowApi.getOpaConfig).mockResolvedValue(
      opaConfig({ enabled: true, baseUrl: 'http://localhost:8181' }),
    );
    vi.mocked(workflowApi.checkOpaHealth).mockResolvedValue(true);

    await act(async () => {
      render(<WorkflowConfiguration project={null} />);
    });

    expect(await screen.findByText('Enabled and enforcing')).toBeInTheDocument();
  });

  it('shows "Enabled but policy unavailable" when enabled but unreachable', async () => {
    vi.mocked(workflowApi.getOpaConfig).mockResolvedValue(
      opaConfig({ enabled: true, baseUrl: 'http://localhost:8181' }),
    );
    vi.mocked(workflowApi.checkOpaHealth).mockResolvedValue(false);

    await act(async () => {
      render(<WorkflowConfiguration project={null} />);
    });

    expect(await screen.findByText('Enabled but policy unavailable')).toBeInTheDocument();
  });

  it('saves the OPA configuration and shows a confirmation message', async () => {
    vi.mocked(workflowApi.getOpaConfig).mockResolvedValue(
      opaConfig({ baseUrl: 'http://localhost:8181' }),
    );
    vi.mocked(workflowApi.saveOpaConfig).mockResolvedValue(
      opaConfig({ enabled: true, baseUrl: 'http://localhost:8181' }),
    );

    await act(async () => {
      render(<WorkflowConfiguration project={null} />);
    });
    await screen.findByText('Configured but unreachable');

    await act(async () => {
      screen.getByText('Save OPA config').click();
    });

    await waitFor(() => {
      expect(workflowApi.saveOpaConfig).toHaveBeenCalledWith(
        expect.objectContaining({
          baseUrl: 'http://localhost:8181',
          failMode: 'FAIL_CLOSED',
        }),
      );
    });
    expect(await screen.findByText('OPA configuration saved.')).toBeInTheDocument();
  });

  it('shows an error message when saving fails', async () => {
    vi.mocked(workflowApi.saveOpaConfig).mockRejectedValue(new Error('network error'));

    await act(async () => {
      render(<WorkflowConfiguration project={null} />);
    });
    await screen.findByText('Not configured');

    await act(async () => {
      screen.getByText('Save OPA config').click();
    });

    expect(await screen.findByText('Failed to save OPA configuration.')).toBeInTheDocument();
  });

  it('still renders the OPA section when no project is selected', async () => {
    await act(async () => {
      render(<WorkflowConfiguration project={null} />);
    });

    expect(await screen.findByText('Open Policy Agent')).toBeInTheDocument();
    expect(
      screen.getByText(
        'Select a project from the dashboard header to configure its workflow input sources.',
      ),
    ).toBeInTheDocument();
  });
});

const PROJECT: Project = {
  id: 'project-1',
  name: 'Absence Pro Solutions',
  active: 1,
  newProject: 0,
  gitStatus: 'ACCESSIBLE',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

function folderConfig(overrides: Partial<DocumentFolderConfig> = {}): DocumentFolderConfig {
  return {
    projectId: 'project-1',
    projectName: 'Absence Pro Solutions',
    folderName: '',
    folderPath: '',
    fileTriggerEnabled: false,
    allowedDocumentTypes: '',
    workflowId: '',
    ...overrides,
  };
}

describe('WorkflowConfiguration — Project document folder section', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(workflowApi.getOpaConfig).mockResolvedValue({
      enabled: false,
      baseUrl: '',
      policyPath: '',
      healthPath: '',
      timeoutSeconds: 3,
      failMode: 'FAIL_CLOSED',
      decisionLoggingEnabled: true,
      decisionLogExportEnabled: true,
      environment: '',
      customerLabel: '',
      projectLabel: '',
      hasAuthToken: false,
    });
    vi.mocked(workflowApi.checkOpaHealth).mockResolvedValue(false);
    vi.mocked(workflowApi.getHermesConfig).mockResolvedValue({
      projectId: 'project-1',
      enabled: false,
      endpointUrl: '',
      signalType: '',
      workflowId: '',
      hasAuthToken: false,
    });
    vi.mocked(workflowApi.getJiraConfig).mockResolvedValue({
      projectId: 'project-1',
      enabled: false,
      baseUrl: '',
      projectKey: '',
      issueTypeMapping: '',
      workflowId: '',
      hasAuthToken: false,
    });
    vi.mocked(workflowApi.getDocumentFolderConfig).mockResolvedValue(folderConfig());
    vi.mocked(workflowApi.saveDocumentFolderConfig).mockResolvedValue(folderConfig());
  });

  it('derives the folder name as CamelCase without spaces from the project name', async () => {
    await act(async () => {
      render(<WorkflowConfiguration project={PROJECT} />);
    });

    expect(await screen.findByDisplayValue('AbsenceProSolutions')).toBeDisabled();
  });

  it('defaults the folder location to /documents/<CamelCaseFolderName>', async () => {
    await act(async () => {
      render(<WorkflowConfiguration project={PROJECT} />);
    });

    expect(await screen.findByDisplayValue('/documents/AbsenceProSolutions')).toBeDisabled();
    expect(screen.getByDisplayValue('/documents')).toBeInTheDocument();
  });

  it('recomputes the folder location when the base path is edited', async () => {
    await act(async () => {
      render(<WorkflowConfiguration project={PROJECT} />);
    });
    await screen.findByDisplayValue('/documents');

    fireEvent.change(screen.getByDisplayValue('/documents'), {
      target: { value: '/data/customer-docs' },
    });

    expect(
      await screen.findByDisplayValue('/data/customer-docs/AbsenceProSolutions'),
    ).toBeInTheDocument();
  });

  it('derives the base path from a previously saved folder path', async () => {
    vi.mocked(workflowApi.getDocumentFolderConfig).mockResolvedValue(
      folderConfig({ folderPath: '/data/old/AbsenceProSolutions' }),
    );

    await act(async () => {
      render(<WorkflowConfiguration project={PROJECT} />);
    });

    expect(await screen.findByDisplayValue('/data/old')).toBeInTheDocument();
    expect(screen.getByDisplayValue('/data/old/AbsenceProSolutions')).toBeInTheDocument();
  });

  it('saves the composed folder path, not the raw base path', async () => {
    await act(async () => {
      render(<WorkflowConfiguration project={PROJECT} />);
    });
    await screen.findByDisplayValue('/documents');

    fireEvent.change(screen.getByDisplayValue('/documents'), {
      target: { value: '/data/customer-docs' },
    });
    await screen.findByDisplayValue('/data/customer-docs/AbsenceProSolutions');

    await act(async () => {
      screen.getByText('Save document folder config').click();
    });

    await waitFor(() => {
      expect(workflowApi.saveDocumentFolderConfig).toHaveBeenCalledWith(
        'project-1',
        expect.objectContaining({
          folderPath: '/data/customer-docs/AbsenceProSolutions',
        }),
      );
    });
  });
});
