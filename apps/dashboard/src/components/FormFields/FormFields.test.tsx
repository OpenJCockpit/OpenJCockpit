import { fireEvent, render, screen } from '@testing-library/react';
import { createRef } from 'react';
import { describe, expect, it, vi } from 'vitest';
import {
  CheckboxField,
  IntegrationConfigForm,
  SecretField,
  TextAreaField,
  TextField,
} from './FormFields';

describe('FormFields', () => {
  it('TextField reports the typed value and forwards the ref', () => {
    const onChange = vi.fn();
    const ref = createRef<HTMLInputElement>();
    render(<TextField ref={ref} label="Name" value="a" onChange={onChange} placeholder="p" />);

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'b' } });

    expect(onChange).toHaveBeenCalledWith('b');
    expect(ref.current).toBe(screen.getByPlaceholderText('p'));
  });

  it('TextAreaField spans the full width when wide', () => {
    const onChange = vi.fn();
    render(<TextAreaField wide label="Notes" value="x" rows={3} onChange={onChange} />);

    fireEvent.change(screen.getByLabelText('Notes'), { target: { value: 'y' } });

    expect(onChange).toHaveBeenCalledWith('y');
    expect(screen.getByLabelText('Notes').closest('label')).toHaveStyle({
      gridColumn: '1 / -1',
    });
  });

  it('TextField and TextAreaField extend settings-input with a custom className', () => {
    render(
      <>
        <TextField label="Name" value="" onChange={vi.fn()} className="mono" />
        <TextAreaField label="Notes" value="" onChange={vi.fn()} className="tall" />
        <TextField label="Plain" value="" onChange={vi.fn()} />
      </>,
    );

    expect(screen.getByLabelText('Name')).toHaveClass('settings-input', 'mono');
    expect(screen.getByLabelText('Notes')).toHaveClass('settings-input', 'tall');
    expect(screen.getByLabelText('Plain').className).toBe('settings-input');
  });

  it('CheckboxField reports the checked state', () => {
    const onChange = vi.fn();
    render(<CheckboxField label="Enabled" checked={false} onChange={onChange} />);

    fireEvent.click(screen.getByLabelText('Enabled'));

    expect(onChange).toHaveBeenCalledWith(true);
  });

  it('SecretField hints that a stored secret is kept when left blank', () => {
    const { rerender } = render(
      <SecretField label="Token" hasValue={false} value="" onChange={vi.fn()} />,
    );
    expect(screen.queryByText(/leave blank to keep/)).toBeNull();

    rerender(<SecretField label="Token" hasValue value="" onChange={vi.fn()} />);
    expect(screen.getByText(/leave blank to keep/)).toBeTruthy();
    expect(screen.getByPlaceholderText('••••••••')).toHaveAttribute('type', 'password');
  });

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
