import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { THEME_STORAGE_KEY, useHubTheme } from '@openjcockpit/hub-contract';
import { HubThemeProvider } from './HubThemeProvider';

function Probe() {
  const { preference, resolved, setPreference } = useHubTheme();
  return (
    <>
      <p>{`${preference}/${resolved}`}</p>
      <button type="button" onClick={() => setPreference('light')}>
        light
      </button>
    </>
  );
}

function mockSystem(dark: boolean) {
  const listeners = new Set<() => void>();
  let matches = dark;
  vi.stubGlobal('matchMedia', () => ({
    get matches() {
      return matches;
    },
    addEventListener: (_: string, fn: () => void) => listeners.add(fn),
    removeEventListener: (_: string, fn: () => void) => listeners.delete(fn),
  }));
  return (next: boolean) => {
    matches = next;
    listeners.forEach((fn) => fn());
  };
}

describe('HubThemeProvider', () => {
  beforeEach(() => window.localStorage.clear());
  afterEach(() => {
    vi.unstubAllGlobals();
    delete document.documentElement.dataset.theme;
    document.documentElement.style.colorScheme = '';
  });

  it('follows the system by default and reacts to OS changes', () => {
    const setSystem = mockSystem(true);
    render(
      <HubThemeProvider>
        <Probe />
      </HubThemeProvider>,
    );
    expect(screen.getByText('system/dark')).toBeInTheDocument();
    expect(document.documentElement.dataset.theme).toBe('dark');
    act(() => setSystem(false));
    expect(screen.getByText('system/light')).toBeInTheDocument();
    expect(document.documentElement.dataset.theme).toBe('light');
  });

  it('stores a chosen preference and restores it', () => {
    mockSystem(true);
    const first = render(
      <HubThemeProvider>
        <Probe />
      </HubThemeProvider>,
    );
    fireEvent.click(screen.getByRole('button', { name: 'light' }));
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('light');
    expect(document.documentElement.style.colorScheme).toBe('light');
    first.unmount();
    render(
      <HubThemeProvider>
        <Probe />
      </HubThemeProvider>,
    );
    expect(screen.getByText('light/light')).toBeInTheDocument();
  });

  it('ignores an unknown stored value and defaults to dark without matchMedia', () => {
    window.localStorage.setItem(THEME_STORAGE_KEY, 'blue');
    render(
      <HubThemeProvider>
        <Probe />
      </HubThemeProvider>,
    );
    expect(screen.getByText('system/dark')).toBeInTheDocument();
  });

  it('survives blocked storage', () => {
    mockSystem(true);
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    render(
      <HubThemeProvider>
        <Probe />
      </HubThemeProvider>,
    );
    fireEvent.click(screen.getByRole('button', { name: 'light' }));
    expect(screen.getByText('light/light')).toBeInTheDocument();
    vi.restoreAllMocks();
  });
});
