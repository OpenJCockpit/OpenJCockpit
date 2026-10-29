import { fireEvent, screen } from '@testing-library/react';
import { renderWithI18n as render } from '@openjcockpit/i18n/testing';
import { describe, expect, it, vi } from 'vitest';
import { SecretField } from './SecretField';

describe('SecretField', () => {
  it('SecretField hints that a stored secret is kept when left blank', () => {
    const { rerender } = render(
      <SecretField label="Token" hasValue={false} value="" onChange={vi.fn()} />,
    );
    expect(screen.queryByText(/leave blank to keep/)).toBeNull();

    rerender(<SecretField label="Token" hasValue value="" onChange={vi.fn()} />);
    expect(screen.getByText(/leave blank to keep/)).toBeTruthy();
    expect(screen.getByPlaceholderText('••••••••')).toHaveAttribute('type', 'password');
  });

  it('reports typed input', () => {
    const onChange = vi.fn();
    render(<SecretField label="Token" hasValue={false} value="" onChange={onChange} />);
    fireEvent.change(screen.getByLabelText('Token'), { target: { value: 'abc' } });
    expect(onChange).toHaveBeenCalledWith('abc');
  });
});
