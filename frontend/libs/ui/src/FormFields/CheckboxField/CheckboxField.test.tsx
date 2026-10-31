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

  it('renders a hint and forwards input attributes', () => {
    render(
      <CheckboxField
        label="Enabled"
        hint="Takes effect on save"
        checked
        disabled
        data-testid="box"
        onChange={vi.fn()}
      />,
    );

    expect(screen.getByText('Takes effect on save')).toBeInTheDocument();
    expect(screen.getByTestId('box')).toBeDisabled();
    expect(screen.getByLabelText(/Enabled/)).toBeChecked();
  });
});
