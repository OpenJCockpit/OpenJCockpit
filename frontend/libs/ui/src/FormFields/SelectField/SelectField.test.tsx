import { render, screen } from '@testing-library/react';
import { chooseOption } from '../../testing';
import { describe, expect, it, vi } from 'vitest';
import { SelectField } from './SelectField';
import { fieldHintClass } from '../../Form/Form';

describe('SelectField', () => {
  it('SelectField omits the hint when none is given', () => {
    render(
      <SelectField
        label="Group"
        value=""
        onChange={vi.fn()}
        options={[{ value: '', label: 'None' }]}
      />,
    );

    expect(document.querySelector(`.${fieldHintClass}`)).toBeNull();
  });

  it('SelectField reports the selected value and renders a hint', () => {
    const onChange = vi.fn();
    render(
      <SelectField
        label="Group"
        value=""
        onChange={onChange}
        hint="Pick one"
        wide
        options={[
          { value: '', label: 'None' },
          { value: 'g1', label: 'G1' },
        ]}
      />,
    );

    chooseOption(screen.getByRole('combobox', { name: /Group/ }), 'G1');

    expect(onChange).toHaveBeenCalledWith('g1');
    expect(screen.getByText('Pick one')).toHaveClass(fieldHintClass);
  });
});
