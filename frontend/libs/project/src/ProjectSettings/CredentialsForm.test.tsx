import { fireEvent, render, screen } from '@testing-library/react';
import type { ComponentProps } from 'react';
import { describe, expect, it, vi } from 'vitest';
import type { ProjectGitCredentialRequest } from '@openjcockpit/api';
import { CredentialsForm } from './CredentialsForm';

const FORM: ProjectGitCredentialRequest = { credentialType: 'NONE' };

function setup(overrides: Partial<ComponentProps<typeof CredentialsForm>> = {}) {
  const onChange = vi.fn();
  const onSave = vi.fn();
  const onCancel = vi.fn();
  render(
    <CredentialsForm
      form={FORM}
      onChange={onChange}
      hasSecret={false}
      loading={false}
      saving={false}
      onSave={onSave}
      onCancel={onCancel}
      {...overrides}
    />,
  );
  return { onChange, onSave, onCancel };
}

/** Applies the updater passed to onChange to a form, as the parent would. */
function applied(onChange: ReturnType<typeof vi.fn>, from = FORM) {
  const updater = onChange.mock.calls.at(-1)![0] as (
    f: ProjectGitCredentialRequest,
  ) => ProjectGitCredentialRequest;
  return updater(from);
}

describe('CredentialsForm', () => {
  it('renders empty text fields when optional values are missing', () => {
    setup();
    expect(screen.getByPlaceholderText('optional')).toHaveValue('');
    expect(screen.getByPlaceholderText('Required — no secret set yet')).toHaveValue('');
    expect(screen.getByPlaceholderText('https://github.example.com/api/v3')).toHaveValue('');
  });

  it('hints that an existing secret is kept when left empty', () => {
    setup({ hasSecret: true });
    expect(
      screen.getByPlaceholderText('Leave empty to keep the current secret'),
    ).toBeInTheDocument();
  });

  it('reports each field change as an updater on the form', () => {
    const { onChange } = setup();

    fireEvent.change(screen.getByDisplayValue('None'), { target: { value: 'GITHUB_APP' } });
    expect(applied(onChange)).toEqual({ credentialType: 'GITHUB_APP' });

    fireEvent.change(screen.getByPlaceholderText('optional'), { target: { value: 'bot' } });
    expect(applied(onChange)).toEqual({ credentialType: 'NONE', username: 'bot' });

    fireEvent.change(screen.getByPlaceholderText('Required — no secret set yet'), {
      target: { value: 'tok' },
    });
    expect(applied(onChange)).toEqual({ credentialType: 'NONE', secret: 'tok' });

    fireEvent.change(screen.getByPlaceholderText('https://github.example.com/api/v3'), {
      target: { value: 'https://gh.example.com' },
    });
    expect(applied(onChange)).toEqual({
      credentialType: 'NONE',
      githubApiUrl: 'https://gh.example.com',
    });
  });

  it('shows a loading message and disables saving while loading', () => {
    setup({ loading: true });
    expect(screen.getByText('Loading current credentials...')).toBeInTheDocument();
    expect(screen.queryByPlaceholderText('optional')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
  });

  it('shows progress while saving', () => {
    setup({ saving: true });
    expect(screen.getByRole('button', { name: /Verifying and saving/ })).toBeDisabled();
  });

  it('calls onSave and onCancel', () => {
    const { onSave, onCancel } = setup();
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onSave).toHaveBeenCalledTimes(1);
    expect(onCancel).toHaveBeenCalledTimes(1);
  });
});
