import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { SelectField } from './SelectField';
import { fieldHintClass } from '@openjcockpit/ui';

describe('SelectField', () => {
  it('SelectField reports the selected value and renders a hint', () => {
    const onChange = vi.fn();
    render(
      <SelectField label="Group" value="" onChange={onChange} hint="Pick one" wide>
        <option value="">None</option>
        <option value="g1">G1</option>
      </SelectField>,
    );

    fireEvent.change(screen.getByLabelText(/Group/), { target: { value: 'g1' } });

    expect(onChange).toHaveBeenCalledWith('g1');
    expect(screen.getByText('Pick one')).toHaveClass(fieldHintClass);
  });
});
