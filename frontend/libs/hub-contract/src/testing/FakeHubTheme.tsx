import type { ReactNode } from 'react';
import { HubThemeContext, type ThemeMode, type ThemePreference } from '../theme';

export function FakeHubThemeProvider({
  preference = 'dark',
  resolved = preference === 'light' ? 'light' : 'dark',
  onChange = () => undefined,
  children,
}: {
  preference?: ThemePreference;
  resolved?: ThemeMode;
  onChange?: (preference: ThemePreference) => void;
  children: ReactNode;
}) {
  return (
    <HubThemeContext.Provider value={{ preference, resolved, setPreference: onChange }}>
      {children}
    </HubThemeContext.Provider>
  );
}
