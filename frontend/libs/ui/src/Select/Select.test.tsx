import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { Select } from './Select';

const options = [
  { value: 'a', label: 'Alpha' },
  { value: 'b', label: 'Beta' },
  { value: 'c', label: 'Gamma' },
];

function setup(value = 'a') {
  const onChange = vi.fn();
  render(<Select label="Pick" value={value} options={options} onChange={onChange} />);
  return { onChange, trigger: screen.getByRole('combobox', { name: 'Pick' }) };
}

describe('Select', () => {
  it('shows the selected label and opens a listbox on click', () => {
    const { trigger } = setup('b');
    expect(trigger).toHaveTextContent('Beta');
    expect(screen.queryByRole('listbox')).toBeNull();
    fireEvent.click(trigger);
    expect(screen.getAllByRole('option')).toHaveLength(3);
    expect(screen.getByRole('option', { name: 'Beta' })).toHaveAttribute('aria-selected', 'true');
  });

  it('reports a clicked option and closes', () => {
    const { onChange, trigger } = setup();
    fireEvent.click(trigger);
    fireEvent.click(screen.getByRole('option', { name: 'Gamma' }));
    expect(onChange).toHaveBeenCalledWith('c');
    expect(screen.queryByRole('listbox')).toBeNull();
  });

  it('does not report re-selecting the current value', () => {
    const { onChange, trigger } = setup();
    fireEvent.click(trigger);
    fireEvent.click(screen.getByRole('option', { name: 'Alpha' }));
    expect(onChange).not.toHaveBeenCalled();
  });

  it('supports keyboard selection and Escape', () => {
    const { onChange, trigger } = setup();
    fireEvent.keyDown(trigger, { key: 'ArrowDown' });
    fireEvent.keyDown(trigger, { key: 'ArrowDown' });
    fireEvent.keyDown(trigger, { key: 'Enter' });
    expect(onChange).toHaveBeenCalledWith('b');

    fireEvent.keyDown(trigger, { key: 'ArrowDown' });
    expect(screen.getByRole('listbox')).toBeInTheDocument();
    fireEvent.keyDown(trigger, { key: 'Escape' });
    expect(screen.queryByRole('listbox')).toBeNull();
  });

  it('closes on outside pointer down', () => {
    const { trigger } = setup();
    fireEvent.click(trigger);
    fireEvent.pointerDown(document.body);
    expect(screen.queryByRole('listbox')).toBeNull();
  });

  it('falls back to the first option for an unknown value', () => {
    const { trigger } = setup('zzz');
    expect(trigger).toHaveTextContent('Alpha');
    expect(trigger).toHaveAttribute('data-value', 'zzz');
  });

  it('renders an empty label and ignores selection when there are no options', () => {
    const onChange = vi.fn();
    render(<Select label="Pick" value="a" options={[]} onChange={onChange} />);
    const trigger = screen.getByRole('combobox', { name: 'Pick' });
    expect(trigger).toHaveTextContent('');
    fireEvent.keyDown(trigger, { key: 'Enter' });
    fireEvent.keyDown(trigger, { key: 'Enter' });
    expect(onChange).not.toHaveBeenCalled();
    expect(screen.queryByRole('listbox')).toBeNull();
  });

  it('stays open on pointer down inside and closes on trigger click', () => {
    const { trigger } = setup();
    fireEvent.click(trigger);
    fireEvent.pointerDown(trigger);
    expect(screen.getByRole('listbox')).toBeInTheDocument();
    fireEvent.click(trigger);
    expect(screen.queryByRole('listbox')).toBeNull();
  });

  it('moves the active option with ArrowUp, Home and End, clamping at the ends', () => {
    const { trigger } = setup('b');
    fireEvent.keyDown(trigger, { key: 'ArrowUp' });
    const activeId = () => trigger.getAttribute('aria-activedescendant');
    const optionId = (name: string) => screen.getByRole('option', { name }).id;
    expect(activeId()).toBe(optionId('Beta'));
    fireEvent.keyDown(trigger, { key: 'ArrowUp' });
    fireEvent.keyDown(trigger, { key: 'ArrowUp' });
    expect(activeId()).toBe(optionId('Alpha'));
    fireEvent.keyDown(trigger, { key: 'End' });
    expect(activeId()).toBe(optionId('Gamma'));
    fireEvent.keyDown(trigger, { key: 'ArrowDown' });
    expect(activeId()).toBe(optionId('Gamma'));
    fireEvent.keyDown(trigger, { key: 'Home' });
    expect(activeId()).toBe(optionId('Alpha'));
  });

  it('ignores Home, End and Escape while closed', () => {
    const { trigger } = setup();
    for (const key of ['Home', 'End', 'Escape']) {
      expect(fireEvent.keyDown(trigger, { key })).toBe(true);
    }
    expect(screen.queryByRole('listbox')).toBeNull();
  });

  it('opens and selects with the space bar', () => {
    const { onChange, trigger } = setup();
    fireEvent.keyDown(trigger, { key: ' ' });
    fireEvent.keyDown(trigger, { key: 'ArrowDown' });
    fireEvent.keyDown(trigger, { key: ' ' });
    expect(onChange).toHaveBeenCalledWith('b');
  });

  it('closes on Tab without preventing default', () => {
    const { trigger } = setup();
    fireEvent.click(trigger);
    expect(fireEvent.keyDown(trigger, { key: 'Tab' })).toBe(true);
    expect(screen.queryByRole('listbox')).toBeNull();
  });

  it('ignores other keys', () => {
    const { trigger } = setup();
    fireEvent.keyDown(trigger, { key: 'x' });
    expect(screen.queryByRole('listbox')).toBeNull();
  });

  it('highlights the hovered option and selects it with Enter', () => {
    const { onChange, trigger } = setup();
    fireEvent.click(trigger);
    const gamma = screen.getByRole('option', { name: 'Gamma' });
    fireEvent.pointerEnter(gamma);
    expect(trigger).toHaveAttribute('aria-activedescendant', gamma.id);
    fireEvent.keyDown(trigger, { key: 'Enter' });
    expect(onChange).toHaveBeenCalledWith('c');
  });
});
