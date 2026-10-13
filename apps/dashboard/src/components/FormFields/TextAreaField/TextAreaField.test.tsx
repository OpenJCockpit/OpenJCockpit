import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { TextAreaField } from './TextAreaField';

describe('TextAreaField', () => {
  it('TextAreaField spans the full width when wide', () => {
    const onChange = vi.fn();
    render(<TextAreaField wide label="Notes" value="x" rows={3} onChange={onChange} />);

    fireEvent.change(screen.getByLabelText('Notes'), { target: { value: 'y' } });

    expect(onChange).toHaveBeenCalledWith('y');
    expect(screen.getByLabelText('Notes').closest('label')).toHaveClass('form-field--wide');
  });
});
