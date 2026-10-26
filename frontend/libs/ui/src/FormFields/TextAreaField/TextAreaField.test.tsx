import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { TextAreaField } from './TextAreaField';
import styles from '../FormFields.module.scss';

describe('TextAreaField', () => {
  it('TextAreaField renders a hint only when provided', () => {
    const { rerender } = render(<TextAreaField label="Notes" value="" onChange={vi.fn()} />);
    expect(screen.queryByText('Be brief')).not.toBeInTheDocument();

    rerender(<TextAreaField label="Notes" value="" onChange={vi.fn()} hint="Be brief" />);
    expect(screen.getByText('Be brief')).toBeInTheDocument();
  });

  it('TextAreaField spans the full width when wide', () => {
    const onChange = vi.fn();
    render(<TextAreaField wide label="Notes" value="x" rows={3} onChange={onChange} />);

    fireEvent.change(screen.getByLabelText('Notes'), { target: { value: 'y' } });

    expect(onChange).toHaveBeenCalledWith('y');
    expect(screen.getByLabelText('Notes').closest('label')).toHaveClass(styles.formFieldWide);
  });
});
