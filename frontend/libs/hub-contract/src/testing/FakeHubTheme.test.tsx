import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useHubTheme } from '../theme';
import { FakeHubThemeProvider } from './FakeHubTheme';

function Probe() {
  const { preference, resolved, setPreference } = useHubTheme();
  return (
    <button onClick={() => setPreference('light')}>
      {preference}/{resolved}
    </button>
  );
}

describe('FakeHubThemeProvider', () => {
  it('defaults to dark and resolves the preference', () => {
    render(
      <FakeHubThemeProvider>
        <Probe />
      </FakeHubThemeProvider>,
    );
    expect(screen.getByRole('button')).toHaveTextContent('dark/dark');
  });

  it('resolves light, honours an explicit resolved mode and reports changes', () => {
    const onChange = vi.fn();
    const { rerender } = render(
      <FakeHubThemeProvider preference="light" onChange={onChange}>
        <Probe />
      </FakeHubThemeProvider>,
    );
    expect(screen.getByRole('button')).toHaveTextContent('light/light');
    fireEvent.click(screen.getByRole('button'));
    expect(onChange).toHaveBeenCalledWith('light');

    rerender(
      <FakeHubThemeProvider preference="system" resolved="light">
        <Probe />
      </FakeHubThemeProvider>,
    );
    expect(screen.getByRole('button')).toHaveTextContent('system/light');
    fireEvent.click(screen.getByRole('button'));
  });
});
