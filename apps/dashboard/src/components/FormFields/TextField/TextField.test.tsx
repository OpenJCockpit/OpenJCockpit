import { fireEvent, render, screen } from '@testing-library/react';
import { createRef } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { TextField } from './TextField';
import { TextAreaField } from '../TextAreaField/TextAreaField';

describe('TextField', () => {
  it('TextField reports the typed value and forwards the ref', () => {
    const onChange = vi.fn();
    const ref = createRef<HTMLInputElement>();
    render(<TextField ref={ref} label="Name" value="a" onChange={onChange} placeholder="p" />);

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'b' } });

    expect(onChange).toHaveBeenCalledWith('b');
    expect(ref.current).toBe(screen.getByPlaceholderText('p'));
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
});
