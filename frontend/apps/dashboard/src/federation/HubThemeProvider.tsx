import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  HubThemeContext,
  isThemePreference,
  THEME_STORAGE_KEY,
  type ThemeMode,
  type ThemePreference,
} from '@openjcockpit/hub-contract';

const DARK_QUERY = '(prefers-color-scheme: dark)';

function readPreference(): ThemePreference {
  try {
    const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
    return isThemePreference(stored) ? stored : 'system';
  } catch {
    return 'system';
  }
}

function systemMode(): ThemeMode {
  return window.matchMedia?.(DARK_QUERY).matches === false ? 'light' : 'dark';
}

/**
 * Owns the theme for the host and every remote: keeps the user's choice, resolves `system`
 * against the OS setting and marks `<html>` so styles and native controls follow.
 */
export function HubThemeProvider({ children }: { children: ReactNode }) {
  const [preference, setPreferenceState] = useState(readPreference);
  const [system, setSystem] = useState(systemMode);
  const resolved: ThemeMode = preference === 'system' ? system : preference;

  useEffect(() => {
    const query = window.matchMedia?.(DARK_QUERY);
    if (!query) return undefined;
    const onChange = () => setSystem(systemMode());
    query.addEventListener('change', onChange);
    return () => query.removeEventListener('change', onChange);
  }, []);

  useEffect(() => {
    document.documentElement.dataset.theme = resolved;
    document.documentElement.style.colorScheme = resolved;
  }, [resolved]);

  const setPreference = useCallback((next: ThemePreference) => {
    setPreferenceState(next);
    try {
      window.localStorage.setItem(THEME_STORAGE_KEY, next);
    } catch {
      // The choice still applies for this session when storage is blocked.
    }
  }, []);

  const value = useMemo(
    () => ({ preference, resolved, setPreference }),
    [preference, resolved, setPreference],
  );
  return <HubThemeContext.Provider value={value}>{children}</HubThemeContext.Provider>;
}
