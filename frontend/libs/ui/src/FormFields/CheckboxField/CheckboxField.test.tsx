import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { CheckboxField } from './CheckboxField';

describe('CheckboxField', () => {
  it('CheckboxField reports the checked state', () => {
    const onChange = vi.fn();
    render(<CheckboxField label="Enabled" checked={false} onChange={onChange} />);

    fireEvent.click(screen.getByLabelText('Enabled'));

    expect(onChange).toHaveBeenCalledWith(true);
  });
});
