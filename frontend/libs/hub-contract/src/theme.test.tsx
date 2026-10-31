import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { HubThemeContext, isThemePreference, useHubTheme } from './theme';

function Probe() {
  const { preference, resolved } = useHubTheme();
  return <p>{`${preference}/${resolved}`}</p>;
}

describe('useHubTheme', () => {
  it('returns the host theme', () => {
    render(
      <HubThemeContext.Provider
        value={{ preference: 'system', resolved: 'dark', setPreference: () => undefined }}
      >
        <Probe />
      </HubThemeContext.Provider>,
    );
    expect(screen.getByText('system/dark')).toBeInTheDocument();
  });

  it('fails loudly without a host', () => {
    const error = console.error;
    console.error = () => undefined;
    try {
      expect(() => render(<Probe />)).toThrow(/HubThemeContext/);
    } finally {
      console.error = error;
    }
  });
});

describe('isThemePreference', () => {
  it('accepts only known preferences', () => {
    expect(isThemePreference('system')).toBe(true);
    expect(isThemePreference('blue')).toBe(false);
    expect(isThemePreference(null)).toBe(false);
  });
});
