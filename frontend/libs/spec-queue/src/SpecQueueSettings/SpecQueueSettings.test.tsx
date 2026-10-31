import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@openjcockpit/api', async (importOriginal) =>
  (await import('../testUtils')).automockApiClients(await importOriginal()),
);
// The permission check is the only part of the contract these tests need to steer.
vi.mock('@openjcockpit/hub-contract', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@openjcockpit/hub-contract')>()),
  useCan: vi.fn(() => false),
}));

import type { Project, SpecQueue } from '@openjcockpit/api';
import * as api from '@openjcockpit/api';
import { useCan } from '@openjcockpit/hub-contract';
import { FakeHubProjectProvider } from '@openjcockpit/hub-contract/testing';
import { withI18n } from '@openjcockpit/i18n/testing';
import { specQueueResources } from '../i18n';
import { SpecQueueSettings } from './SpecQueueSettings';

const PROJECT = { id: 'p1', name: 'Proj' } as Project;
const QUEUE_KEY = ['spec-queue', 'p1', 'queue'];

const queue = (autoMergeAllowed = false) =>
  ({
    projectId: 'p1',
    state: 'ACTIVE',
    autoMergeAllowed,
    items: [],
    recentlyFinished: [],
    runner: { enabled: true, configured: true },
  }) as SpecQueue;

function renderSettings(
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } }),
) {
  render(
    withI18n(
      <QueryClientProvider client={client}>
        <FakeHubProjectProvider selected={PROJECT}>
          <SpecQueueSettings />
        </FakeHubProjectProvider>
      </QueryClientProvider>,
      specQueueResources,
    ),
  );
  return client;
}

describe('SpecQueueSettings', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(useCan).mockReturnValue(false);
    vi.mocked(api.getSpecQueue).mockResolvedValue(queue());
  });

  it('shows the read-only project toggle with an explanation for non-admins', async () => {
    renderSettings();
    expect(await screen.findByTestId('auto-merge-setting-toggle')).toBeDisabled();
    expect(screen.getByTestId('auto-merge-setting-description')).toHaveTextContent(
      'Only administrators',
    );
  });

  it('reports a queue that cannot be loaded', async () => {
    vi.mocked(api.getSpecQueue).mockRejectedValue(new Error('down'));
    renderSettings();
    expect(await screen.findByRole('alert')).toHaveTextContent('The queue could not be loaded');
  });

  describe('saving the setting', () => {
    const enable = async (client?: QueryClient) => {
      vi.mocked(useCan).mockReturnValue(true);
      vi.mocked(api.updateSpecQueueSettings).mockResolvedValue({ autoMergeAllowed: true });
      const used = renderSettings(client);
      fireEvent.click(await screen.findByTestId('auto-merge-setting-toggle'));
      fireEvent.click(screen.getByTestId('confirm-action-confirm'));
      return used;
    };

    it('writes the saved value into the queue cache the queue page reads', async () => {
      const client = await enable();
      await waitFor(() => expect(screen.getByTestId('auto-merge-setting-toggle')).toBeChecked());
      expect(client.getQueryData<SpecQueue>(QUEUE_KEY)?.autoMergeAllowed).toBe(true);
    });

    it('leaves an evicted queue cache untouched', async () => {
      const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
      vi.mocked(api.updateSpecQueueSettings).mockImplementation(async () => {
        client.removeQueries({ queryKey: QUEUE_KEY });
        return { autoMergeAllowed: true };
      });
      vi.mocked(useCan).mockReturnValue(true);
      renderSettings(client);
      fireEvent.click(await screen.findByTestId('auto-merge-setting-toggle'));
      fireEvent.click(screen.getByTestId('confirm-action-confirm'));
      await waitFor(() => expect(api.updateSpecQueueSettings).toHaveBeenCalled());
      await act(async () => new Promise((resolve) => setTimeout(resolve, 0)));
      expect(client.getQueryData(QUEUE_KEY)).toBeUndefined();
    });
  });
});
