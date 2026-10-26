import { fireEvent, screen, waitFor } from '@testing-library/react';
import type { ReactElement } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Mock } from 'vitest';
import * as api from '@openjcockpit/api';
import { clickAsync, renderAsync } from '../testUtils/dom';
import { HermesSettings } from './HermesSettings/HermesSettings';
import { JiraSettings } from './JiraSettings/JiraSettings';

vi.mock('@openjcockpit/api', async (importOriginal) =>
  (await import('../testUtils/mockApi')).automockApiClients(await importOriginal()),
);

interface Integration {
  name: string;
  render: () => ReactElement;
  get: Mock;
  save: Mock;
  config: Record<string, unknown>;
  /** A field that is populated from the server config, with its placeholder and value. */
  populated: Array<[placeholder: string, value: string]>;
  /** A field the test edits: its placeholder, new value and request key. */
  edit: [placeholder: string, value: string, key: string];
  savedMessage: string;
  loadError: string;
  saveError: string;
  saveLabel: string;
}

const integrations: Integration[] = [
  {
    name: 'JiraSettings',
    render: () => <JiraSettings projectId="p1" />,
    get: vi.mocked(api.getJiraConfig),
    save: vi.mocked(api.saveJiraConfig),
    config: {
      enabled: true,
      baseUrl: 'https://acme.atlassian.net',
      projectKey: 'NL',
      issueTypeMapping: 'Bug=fix',
      workflowId: 'wf-1',
      hasAuthToken: true,
    },
    populated: [
      ['https://your-org.atlassian.net', 'https://acme.atlassian.net'],
      ['NL', 'NL'],
      ['Bug=bugfix-workflow', 'Bug=fix'],
      ['wf-onboarding', 'wf-1'],
    ],
    edit: ['NL', 'ABC', 'projectKey'],
    savedMessage: 'Jira configuration saved.',
    loadError: 'Failed to load the Jira configuration.',
    saveError: 'Failed to save Jira configuration.',
    saveLabel: 'Save Jira config',
  },
  {
    name: 'HermesSettings',
    render: () => <HermesSettings projectId="p1" />,
    get: vi.mocked(api.getHermesConfig),
    save: vi.mocked(api.saveHermesConfig),
    config: {
      enabled: true,
      endpointUrl: 'https://hermes.example.com/signals',
      signalType: 'issue.created',
      workflowId: 'wf-1',
      hasAuthToken: true,
    },
    populated: [
      ['https://hermes.example.com/signals', 'https://hermes.example.com/signals'],
      ['issue.created', 'issue.created'],
      ['wf-onboarding', 'wf-1'],
    ],
    edit: ['issue.created', 'issue.updated', 'signalType'],
    savedMessage: 'Hermes configuration saved.',
    loadError: 'Failed to load the Hermes configuration.',
    saveError: 'Failed to save Hermes configuration.',
    saveLabel: 'Save Hermes config',
  },
];

describe.each(integrations)('$name', (i) => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, 'warn').mockImplementation(() => {});
    i.get.mockResolvedValue(i.config);
    i.save.mockResolvedValue(i.config);
  });

  it('shows a loading message until the config arrives', async () => {
    i.get.mockReturnValue(new Promise(() => {}));
    await renderAsync(i.render());
    expect(screen.getByText('Loading configuration...')).toBeInTheDocument();
  });

  it('shows an error when the config cannot be loaded', async () => {
    i.get.mockRejectedValue(new Error('x'));
    await renderAsync(i.render());
    expect(await screen.findByText(i.loadError)).toBeInTheDocument();
  });

  it('populates the form from the server config and never shows the stored token', async () => {
    await renderAsync(i.render());
    for (const [placeholder, value] of i.populated) {
      expect(await screen.findByPlaceholderText(placeholder)).toHaveValue(value);
    }
    expect(screen.getByRole('checkbox')).toBeChecked();
    expect(screen.getByPlaceholderText('••••••••')).toHaveValue('');
    expect(screen.getByText(/already configured/)).toBeInTheDocument();
  });

  it('defaults missing optional fields to empty', async () => {
    i.get.mockResolvedValue({ enabled: false, hasAuthToken: false });
    await renderAsync(i.render());
    const [placeholder] = i.populated[0]!;
    expect(await screen.findByPlaceholderText(placeholder)).toHaveValue('');
    expect(screen.getByRole('checkbox')).not.toBeChecked();
    expect(screen.queryByText(/already configured/)).not.toBeInTheDocument();
  });

  it('saves the edited form, sending a null token when it was left blank', async () => {
    await renderAsync(i.render());
    const [placeholder, value, key] = i.edit;
    fireEvent.change(await screen.findByPlaceholderText(placeholder), { target: { value } });
    await clickAsync(screen.getByRole('button', { name: i.saveLabel }));

    await waitFor(() => expect(i.save).toHaveBeenCalledTimes(1));
    expect(i.save).toHaveBeenCalledWith(
      'p1',
      expect.objectContaining({ enabled: true, authToken: null, workflowId: 'wf-1' }),
    );
    expect(i.save.mock.calls[0]![1]).toMatchObject({ [key]: value });
    expect(await screen.findByText(i.savedMessage)).toBeInTheDocument();
  });

  it('sends a typed auth token and toggles enabled', async () => {
    await renderAsync(i.render());
    fireEvent.change(await screen.findByPlaceholderText('••••••••'), {
      target: { value: 's3cret' },
    });
    fireEvent.click(screen.getByRole('checkbox'));
    await clickAsync(screen.getByRole('button', { name: i.saveLabel }));

    expect(i.save).toHaveBeenCalledWith(
      'p1',
      expect.objectContaining({ enabled: false, authToken: 's3cret' }),
    );
  });

  it('shows an error and no success message when saving fails', async () => {
    i.save.mockRejectedValue(new Error('500'));
    await renderAsync(i.render());
    await screen.findByPlaceholderText(i.edit[0]);
    await clickAsync(screen.getByRole('button', { name: i.saveLabel }));

    expect(await screen.findByText(i.saveError)).toBeInTheDocument();
    expect(screen.queryByText(i.savedMessage)).not.toBeInTheDocument();
  });

  it('clears a previous error when saving again', async () => {
    i.save.mockRejectedValueOnce(new Error('500')).mockResolvedValue(i.config);
    await renderAsync(i.render());
    await screen.findByPlaceholderText(i.edit[0]);
    await clickAsync(screen.getByRole('button', { name: i.saveLabel }));
    await screen.findByText(i.saveError);

    await clickAsync(screen.getByRole('button', { name: i.saveLabel }));
    expect(await screen.findByText(i.savedMessage)).toBeInTheDocument();
    expect(screen.queryByText(i.saveError)).not.toBeInTheDocument();
  });
});

describe('JiraSettings field edits', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.getJiraConfig).mockResolvedValue({
      enabled: false,
      hasAuthToken: false,
    } as never);
    vi.mocked(api.saveJiraConfig).mockResolvedValue({
      enabled: false,
      hasAuthToken: false,
    } as never);
  });

  it('sends every edited field', async () => {
    await renderAsync(<JiraSettings projectId="p1" />);
    const set = async (placeholder: string, value: string) =>
      fireEvent.change(await screen.findByPlaceholderText(placeholder), { target: { value } });
    await set('https://your-org.atlassian.net', 'https://x.atlassian.net');
    await set('NL', 'KEY');
    await set('Bug=bugfix-workflow', 'Bug=wf');
    await set('wf-onboarding', 'wf-9');
    await clickAsync(screen.getByRole('button', { name: 'Save Jira config' }));
    expect(api.saveJiraConfig).toHaveBeenCalledWith('p1', {
      enabled: false,
      baseUrl: 'https://x.atlassian.net',
      projectKey: 'KEY',
      authToken: null,
      issueTypeMapping: 'Bug=wf',
      workflowId: 'wf-9',
    });
  });
});

describe('HermesSettings field edits', () => {
  it('sends every edited field', async () => {
    vi.clearAllMocks();
    vi.mocked(api.getHermesConfig).mockResolvedValue({
      enabled: false,
      hasAuthToken: false,
    } as never);
    vi.mocked(api.saveHermesConfig).mockResolvedValue({
      enabled: false,
      hasAuthToken: false,
    } as never);
    await renderAsync(<HermesSettings projectId="p1" />);
    const set = async (placeholder: string, value: string) =>
      fireEvent.change(await screen.findByPlaceholderText(placeholder), { target: { value } });
    await set('https://hermes.example.com/signals', 'https://h.example/s');
    await set('issue.created', 'pr.opened');
    await set('wf-onboarding', 'wf-9');
    await clickAsync(screen.getByRole('button', { name: 'Save Hermes config' }));
    expect(api.saveHermesConfig).toHaveBeenCalledWith('p1', {
      enabled: false,
      endpointUrl: 'https://h.example/s',
      authToken: null,
      signalType: 'pr.opened',
      workflowId: 'wf-9',
    });
  });
});
