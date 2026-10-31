import { act, fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '@openjcockpit/api';
import { pseudoLocalize, untranslatedText } from '@openjcockpit/i18n/testing';
import { clickAsync } from '../testUtils/dom';
import { withQueryClient } from '../testUtils/queryClient';
import { HermesSettings } from './HermesSettings/HermesSettings';
import { WorkflowConfiguration } from '../testUtils/SettingsHarness';

vi.mock('@openjcockpit/api', async (importOriginal) =>
  (await import('../testUtils/mockApi')).automockApiClients(await importOriginal()),
);

// In the pseudo locale every translated string is wrapped in `[…~~~]`, so anything left over
// bypassed i18n: hard-coded save errors, success messages and status labels.
describe('workflow configuration in the pseudo locale', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, 'warn').mockImplementation(() => {});
  });

  async function renderPseudo(ui: React.ReactElement) {
    await act(async () => {
      render(withQueryClient(ui, { pseudo: true }));
    });
  }

  it('translates the OPA status and the A2A section', async () => {
    vi.mocked(api.listA2AServices).mockResolvedValue([]);
    vi.mocked(api.getOpaConfig).mockResolvedValue({
      enabled: false,
      baseUrl: 'http://localhost:8181',
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
    });
    vi.mocked(api.checkOpaHealth).mockResolvedValue(false);

    await renderPseudo(<WorkflowConfiguration project={null} />);
    await screen.findAllByText(/^\[.*~+\]$/);

    expect(untranslatedText(document.body)).toEqual([]);
  });

  describe('Hermes settings', () => {
    const config = {
      projectId: 'p1',
      enabled: true,
      endpointUrl: 'https://hermes.example.com/signals',
      signalType: 'issue.created',
      workflowId: 'wf-1',
      hasAuthToken: false,
    };

    it('translates the saved message', async () => {
      vi.mocked(api.getHermesConfig).mockResolvedValue(config);
      vi.mocked(api.saveHermesConfig).mockResolvedValue(config);
      await renderPseudo(<HermesSettings projectId="p1" />);

      await clickAsync(await screen.findByRole('button', { name: /^\[.*~+\]$/ }));

      expect(
        await screen.findByText(pseudoLocalize('Hermes configuration saved.')),
      ).toBeInTheDocument();
      expect(untranslatedText(document.body)).toEqual([]);
    });

    it('translates the save error', async () => {
      vi.mocked(api.getHermesConfig).mockResolvedValue(config);
      vi.mocked(api.saveHermesConfig).mockRejectedValue(new Error('boom'));
      await renderPseudo(<HermesSettings projectId="p1" />);

      fireEvent.click(await screen.findByRole('button', { name: /^\[.*~+\]$/ }));

      expect(
        await screen.findByText(pseudoLocalize('Failed to save the Hermes configuration.')),
      ).toBeInTheDocument();
      expect(untranslatedText(document.body)).toEqual([]);
    });
  });
});
