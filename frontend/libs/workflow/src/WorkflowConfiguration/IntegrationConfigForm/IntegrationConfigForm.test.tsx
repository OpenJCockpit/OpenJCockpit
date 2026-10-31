import { fireEvent, screen } from '@testing-library/react';
import { render } from '../../testUtils/render';
import { describe, expect, it, vi } from 'vitest';
import { IntegrationConfigForm } from './IntegrationConfigForm';

describe('IntegrationConfigForm', () => {
  it('IntegrationConfigForm saves and disables the button while saving', () => {
    const onSave = vi.fn();
    const { rerender } = render(
      <IntegrationConfigForm title="Jira" saveLabel="Save Jira" saving={false} onSave={onSave}>
        <span>fields</span>
      </IntegrationConfigForm>,
    );
    fireEvent.click(screen.getByRole('button', { name: 'Save Jira' }));
    expect(onSave).toHaveBeenCalledTimes(1);

    rerender(
      <IntegrationConfigForm title="Jira" saveLabel="Save Jira" saving onSave={onSave}>
        <span>fields</span>
      </IntegrationConfigForm>,
    );
    expect(screen.getByRole('button', { name: '⟳ Saving…' })).toBeDisabled();
  });
});
