import { fireEvent, screen, waitFor } from '@testing-library/react';
import { chooseOption } from '@openjcockpit/ui/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '@openjcockpit/api';
import type { OpaConfig } from '@openjcockpit/api';
import { clickAsync, renderAsync } from '../../testUtils/dom';
import { OpaSettings } from './OpaSettings';

vi.mock('@openjcockpit/api', async (importOriginal) =>
  (await import('../../testUtils/mockApi')).automockApiClients(await importOriginal()),
);

const config: OpaConfig = {
  enabled: false,
  baseUrl: 'http://opa:8181',
  policyPath: '/v1/data/decision',
  healthPath: '/health',
  timeoutSeconds: 3,
  failMode: 'FAIL_CLOSED',
  decisionLoggingEnabled: true,
  decisionLogExportEnabled: true,
  environment: 'prod',
  customerLabel: 'acme',
  projectLabel: 'onboarding',
  hasAuthToken: true,
};

async function openSettings() {
  await renderAsync(<OpaSettings />);
  await screen.findByDisplayValue('http://opa:8181');
}

describe('OpaSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, 'warn').mockImplementation(() => {});
    vi.mocked(api.getOpaConfig).mockResolvedValue(config);
    vi.mocked(api.saveOpaConfig).mockResolvedValue(config);
    vi.mocked(api.checkOpaHealth).mockResolvedValue(true);
  });

  it('shows an error when the configuration cannot be loaded', async () => {
    vi.mocked(api.getOpaConfig).mockRejectedValue(new Error('x'));
    await renderAsync(<OpaSettings />);
    expect(await screen.findByText('Failed to load the OPA configuration.')).toBeInTheDocument();
  });

  it('populates every field from the server config', async () => {
    await openSettings();
    expect(screen.getByLabelText('Health check path')).toHaveValue('/health');
    expect(screen.getByLabelText('Policy decision endpoint')).toHaveValue('/v1/data/decision');
    expect(screen.getByLabelText('Timeout (seconds)')).toHaveValue(3);
    expect(screen.getByLabelText('Fail mode')).toHaveTextContent('FAIL_CLOSED');
    expect(screen.getByLabelText('Environment label')).toHaveValue('prod');
    expect(screen.getByLabelText('Customer/tenant label')).toHaveValue('acme');
    expect(screen.getByLabelText('Project label')).toHaveValue('onboarding');
    expect(screen.getByLabelText('Enabled')).not.toBeChecked();
    expect(screen.getByLabelText('Decision logging enabled')).toBeChecked();
    expect(screen.getByLabelText(/Auth token/)).toHaveValue('');
  });

  it('sends every edited field on save', async () => {
    await openSettings();
    fireEvent.click(screen.getByLabelText('Enabled'));
    fireEvent.change(screen.getByLabelText('Base URL'), { target: { value: 'http://new:1' } });
    fireEvent.change(screen.getByLabelText('Health check path'), { target: { value: '/h' } });
    fireEvent.change(screen.getByLabelText('Policy decision endpoint'), {
      target: { value: '/p' },
    });
    fireEvent.change(screen.getByLabelText('Timeout (seconds)'), { target: { value: '9' } });
    chooseOption(screen.getByLabelText('Fail mode'), /^FAIL_OPEN/);
    fireEvent.change(screen.getByLabelText('Environment label'), { target: { value: 'stg' } });
    fireEvent.change(screen.getByLabelText('Customer/tenant label'), { target: { value: 'c' } });
    fireEvent.change(screen.getByLabelText('Project label'), { target: { value: 'p' } });
    fireEvent.click(screen.getByLabelText('Decision logging enabled'));
    fireEvent.click(screen.getByLabelText('Decision log export enabled'));
    fireEvent.change(screen.getByLabelText(/Auth token/), { target: { value: 'tok' } });
    await clickAsync(screen.getByRole('button', { name: 'Save OPA configuration' }));

    await waitFor(() => expect(api.saveOpaConfig).toHaveBeenCalledTimes(1));
    expect(api.saveOpaConfig).toHaveBeenCalledWith({
      enabled: true,
      baseUrl: 'http://new:1',
      policyPath: '/p',
      healthPath: '/h',
      timeoutSeconds: 9,
      failMode: 'FAIL_OPEN',
      decisionLoggingEnabled: false,
      decisionLogExportEnabled: false,
      environment: 'stg',
      customerLabel: 'c',
      projectLabel: 'p',
      authToken: 'tok',
    });
  });

  it('omits the timeout when it is not a positive number and sends no token when blank', async () => {
    await openSettings();
    fireEvent.change(screen.getByLabelText('Timeout (seconds)'), { target: { value: '' } });
    await clickAsync(screen.getByRole('button', { name: 'Save OPA configuration' }));
    await waitFor(() => expect(api.saveOpaConfig).toHaveBeenCalled());
    expect(api.saveOpaConfig).toHaveBeenCalledWith(
      expect.objectContaining({ timeoutSeconds: undefined, authToken: null }),
    );
  });

  it('re-checks health after saving', async () => {
    await openSettings();
    const before = vi.mocked(api.checkOpaHealth).mock.calls.length;
    await clickAsync(screen.getByRole('button', { name: 'Save OPA configuration' }));
    await screen.findByText('OPA configuration saved.');
    expect(vi.mocked(api.checkOpaHealth).mock.calls.length).toBeGreaterThan(before);
  });
});
