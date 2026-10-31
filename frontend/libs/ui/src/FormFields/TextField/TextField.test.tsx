import { fireEvent, render, screen } from '@testing-library/react';
import { createRef } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { TextField } from './TextField';
import { TextAreaField } from '../TextAreaField/TextAreaField';
import styles from '../FormFields.module.scss';
import { formInputClass } from '../../Form/Form';

describe('TextField', () => {
  it('marks a required field once and exposes it as aria-required', () => {
    render(<TextField label="Name" value="" onChange={vi.fn()} required />);

    const input = screen.getByLabelText('Name *');
    expect(input).toHaveAttribute('aria-required', 'true');
    expect(screen.getAllByText('*')).toHaveLength(1);
  });

  it('shows an inline error that the input is invalid for and described by', () => {
    render(<TextField label="Name" value="" onChange={vi.fn()} error="Name is required" />);

    const alert = screen.getByRole('alert');
    const input = screen.getByRole('textbox');
    expect(alert).toHaveTextContent('Name is required');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input.getAttribute('aria-describedby')).toContain(alert.id);
  });

  it('keeps a caller-supplied aria-describedby next to the hint', () => {
    render(<TextField label="Name" value="" onChange={vi.fn()} hint="Hint" aria-describedby="x" />);

    const describedBy = screen.getByRole('textbox').getAttribute('aria-describedby') ?? '';
    expect(describedBy.split(' ')).toContain('x');
    expect(describedBy.split(' ')).toHaveLength(2);
  });

  it('TextField reports the typed value and forwards the ref', () => {
    const onChange = vi.fn();
    const ref = createRef<HTMLInputElement>();
    render(<TextField ref={ref} label="Name" value="a" onChange={onChange} placeholder="p" />);

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'b' } });

    expect(onChange).toHaveBeenCalledWith('b');
    expect(ref.current).toBe(screen.getByPlaceholderText('p'));
  });

  it('TextField renders a hint and spans the full width when wide', () => {
    render(<TextField label="Name" value="" onChange={vi.fn()} hint="Required" wide />);

    expect(screen.getByText('Required')).toBeInTheDocument();
    expect(screen.getByLabelText(/Name/).closest('label')).toHaveClass(styles.formFieldWide);
  });

  it('TextField and TextAreaField extend the shared input style with a custom className', () => {
    render(
      <>
        <TextField label="Name" value="" onChange={vi.fn()} className="mono" />
        <TextAreaField label="Notes" value="" onChange={vi.fn()} className="tall" />
        <TextField label="Plain" value="" onChange={vi.fn()} />
      </>,
    );

    expect(screen.getByLabelText('Name')).toHaveClass(formInputClass, 'mono');
    expect(screen.getByLabelText('Notes')).toHaveClass(formInputClass, 'tall');
    expect(screen.getByLabelText('Plain').className).toBe(formInputClass);
  });
});
