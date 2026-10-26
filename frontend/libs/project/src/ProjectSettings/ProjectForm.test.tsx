import { fireEvent, render, screen } from '@testing-library/react';
import { createRef } from 'react';
import type { ComponentProps } from 'react';
import { describe, expect, it, vi } from 'vitest';
import type { ProjectRequest } from '@openjcockpit/api';
import { ProjectForm } from './ProjectForm';

const FORM: ProjectRequest = { name: 'Alpha' };

function setup(overrides: Partial<ComponentProps<typeof ProjectForm>> = {}) {
  const onChange = vi.fn();
  const onSave = vi.fn();
  const onCancel = vi.fn();
  const nameInputRef = createRef<HTMLInputElement>();
  render(
    <ProjectForm
      isNew
      form={FORM}
      onChange={onChange}
      saving={false}
      nameInvalid={false}
      nameInputRef={nameInputRef}
      onSave={onSave}
      onCancel={onCancel}
      {...overrides}
    />,
  );
  return { onChange, onSave, onCancel, nameInputRef };
}

/** Applies the updater passed to onChange to the current form, like the parent does. */
function applied(onChange: ReturnType<typeof vi.fn>): ProjectRequest {
  const updater = onChange.mock.calls.at(-1)![0] as (f: ProjectRequest) => ProjectRequest;
  return updater(FORM);
}

describe('ProjectForm', () => {
  it('titles the form by mode', () => {
    setup({ isNew: false });
    expect(screen.getByRole('heading', { name: 'Edit project' })).toBeInTheDocument();
  });

  it('shows the new-project title', () => {
    setup();
    expect(screen.getByRole('heading', { name: 'New project' })).toBeInTheDocument();
  });

  it.each([
    ['Name *', 'name', 'Beta'],
    ['Customer ID (stub)', 'customerId', 'acme'],
    ['Git URL', 'gitUrl', 'https://example.com/r'],
    ['Branch', 'defaultBranch', 'develop'],
    ['Environment', 'environment', 'staging'],
    ['Owner', 'owner', 'Team A'],
    ['Description', 'description', 'Hello'],
  ])('updates %s through onChange', (label, key, value) => {
    const { onChange } = setup();
    fireEvent.change(screen.getByLabelText(label), { target: { value } });
    expect(applied(onChange)).toEqual({ ...FORM, [key]: value });
  });

  it('toggles the new-project flag between 1 and 0', () => {
    const { onChange } = setup();
    const checkbox = screen.getByRole('checkbox');
    expect(checkbox).not.toBeChecked();
    fireEvent.click(checkbox);
    expect(applied(onChange).newProject).toBe(1);
  });

  it('reflects an existing new-project flag and unchecks to 0', () => {
    const { onChange } = setup({ form: { ...FORM, newProject: 1 } });
    const checkbox = screen.getByRole('checkbox');
    expect(checkbox).toBeChecked();
    fireEvent.click(checkbox);
    const updater = onChange.mock.calls.at(-1)![0] as (f: ProjectRequest) => ProjectRequest;
    expect(updater({ ...FORM, newProject: 1 }).newProject).toBe(0);
  });

  it('flags an invalid name for assistive tech', () => {
    setup({ nameInvalid: true });
    const name = screen.getByLabelText('Name *');
    expect(name).toHaveAttribute('aria-invalid', 'true');
    expect(name).toHaveAttribute('aria-describedby', 'project-name-error');
  });

  it('omits the invalid markers for a valid name', () => {
    setup();
    const name = screen.getByLabelText('Name *');
    expect(name).not.toHaveAttribute('aria-invalid');
    expect(name).not.toHaveAttribute('aria-describedby');
  });

  it('attaches the name input ref', () => {
    const { nameInputRef } = setup();
    expect(nameInputRef.current).toBe(screen.getByLabelText('Name *'));
  });

  it('wires Save and Cancel', () => {
    const { onSave, onCancel } = setup();
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onSave).toHaveBeenCalledOnce();
    expect(onCancel).toHaveBeenCalledOnce();
  });

  it('disables Save while saving', () => {
    setup({ saving: true });
    expect(screen.getByRole('button', { name: /Saving/ })).toBeDisabled();
  });
});
