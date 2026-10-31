import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { Select } from '../Select/Select';
import { chooseOption, optionLabels } from './index';

const options = [
  { value: 'a', label: 'Alpha' },
  { value: 'b', label: 'Beta' },
];

function setup(disabled = false) {
  const onChange = vi.fn();
  render(
    <Select label="Pick" value="a" options={options} onChange={onChange} disabled={disabled} />,
  );
  return { onChange, trigger: screen.getByRole('combobox', { name: 'Pick' }) };
}

describe('select test helpers', () => {
  it('chooseOption picks an option by label', () => {
    const { onChange, trigger } = setup();
    chooseOption(trigger, 'Beta');
    expect(onChange).toHaveBeenCalledWith('b');
  });

  it('optionLabels lists labels and closes the list', () => {
    const { trigger } = setup();
    expect(optionLabels(trigger)).toEqual(['Alpha', 'Beta']);
    expect(screen.queryByRole('listbox')).toBeNull();
  });

  it('throws when the select does not open', () => {
    const { trigger } = setup(true);
    expect(() => chooseOption(trigger, 'Beta')).toThrow('Select did not open');
  });

  it('throws when the trigger has no listbox reference', () => {
    const el = document.createElement('button');
    fireEvent.click(el);
    expect(() => optionLabels(el)).toThrow('Select did not open');
  });
});
