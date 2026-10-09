import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../auth/roles', () => ({ isOpenJCockpitAdmin: vi.fn() }));
vi.mock('../specQueueApi', () => ({ updateSpecQueueSettings: vi.fn() }));

import { isOpenJCockpitAdmin } from '../../auth/roles';
import { updateSpecQueueSettings } from '../specQueueApi';
import { AutoMergeSettingToggle } from './AutoMergeSettingToggle';

function setup(autoMergeAllowed: boolean) {
  const onChanged = vi.fn();
  render(
    <AutoMergeSettingToggle
      projectId="p1"
      autoMergeAllowed={autoMergeAllowed}
      onChanged={onChanged}
    />,
  );
  return { onChanged };
}

describe('AutoMergeSettingToggle', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(isOpenJCockpitAdmin).mockReturnValue(true);
  });

  it('is disabled with an explanation for non-admins', () => {
    vi.mocked(isOpenJCockpitAdmin).mockReturnValue(false);
    setup(false);
    expect(
      screen.getByRole('checkbox', { name: 'Allow auto-merge for this project' }),
    ).toBeDisabled();
    expect(screen.getByTestId('auto-merge-setting-description')).toHaveTextContent(
      'Only administrators',
    );
  });

  it('asks for confirmation before enabling, then saves', async () => {
    vi.mocked(updateSpecQueueSettings).mockResolvedValue({ autoMergeAllowed: true });
    const { onChanged } = setup(false);
    fireEvent.click(screen.getByTestId('auto-merge-setting-toggle'));
    expect(updateSpecQueueSettings).not.toHaveBeenCalled();
    expect(screen.getByRole('dialog', { name: 'Allow auto-merge?' })).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('confirm-action-confirm'));
    await waitFor(() => expect(onChanged).toHaveBeenCalledWith({ autoMergeAllowed: true }));
    expect(updateSpecQueueSettings).toHaveBeenCalledWith('p1', true);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('makes no call when the confirmation is cancelled', () => {
    setup(false);
    fireEvent.click(screen.getByTestId('auto-merge-setting-toggle'));
    fireEvent.click(screen.getByTestId('confirm-action-cancel'));
    expect(updateSpecQueueSettings).not.toHaveBeenCalled();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('disables immediately without confirmation', async () => {
    vi.mocked(updateSpecQueueSettings).mockResolvedValue({ autoMergeAllowed: false });
    const { onChanged } = setup(true);
    fireEvent.click(screen.getByTestId('auto-merge-setting-toggle'));
    await waitFor(() => expect(onChanged).toHaveBeenCalledWith({ autoMergeAllowed: false }));
    expect(updateSpecQueueSettings).toHaveBeenCalledWith('p1', false);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('shows a server refusal inside the dialog and keeps the setting', async () => {
    vi.mocked(updateSpecQueueSettings).mockRejectedValue(
      new Error('Only administrators can do it'),
    );
    const { onChanged } = setup(false);
    fireEvent.click(screen.getByTestId('auto-merge-setting-toggle'));
    fireEvent.click(screen.getByTestId('confirm-action-confirm'));
    expect(await screen.findByTestId('confirm-action-error')).toHaveTextContent(
      'Only administrators can do it',
    );
    expect(onChanged).not.toHaveBeenCalled();
    expect(screen.queryByTestId('auto-merge-setting-error')).not.toBeInTheDocument();
  });

  it('shows a failed disable as an inline alert', async () => {
    vi.mocked(updateSpecQueueSettings).mockRejectedValue(new Error('boom'));
    setup(true);
    fireEvent.click(screen.getByTestId('auto-merge-setting-toggle'));
    expect(await screen.findByTestId('auto-merge-setting-error')).toHaveTextContent('boom');
  });
});
