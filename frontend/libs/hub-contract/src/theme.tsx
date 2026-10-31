import { createContext, useContext } from 'react';

/** What the user chose; `system` follows the operating system. */
export type ThemePreference = 'light' | 'dark' | 'system';

/** What is actually shown. */
export type ThemeMode = 'light' | 'dark';

/** The host's theme, owned by the host so every remote reads and changes the same one. */
export interface HubTheme {
  preference: ThemePreference;
  resolved: ThemeMode;
  setPreference(preference: ThemePreference): void;
}

export const THEME_STORAGE_KEY = 'openjcockpit.theme';

export const THEME_PREFERENCES: readonly ThemePreference[] = ['light', 'dark', 'system'];

export function isThemePreference(value: unknown): value is ThemePreference {
  return THEME_PREFERENCES.some((preference) => preference === value);
}

export const HubThemeContext = createContext<HubTheme | null>(null);

export function useHubTheme(): HubTheme {
  const theme = useContext(HubThemeContext);
  if (!theme) {
    throw new Error(
      'useHubTheme needs a HubThemeContext provider. Check that @openjcockpit/hub-contract is a shared singleton.',
    );
  }
  return theme;
}
