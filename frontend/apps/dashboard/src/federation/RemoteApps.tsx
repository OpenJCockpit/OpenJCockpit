import { createContext, useContext, type ReactNode } from 'react';
import { useTranslation } from '@openjcockpit/i18n';
import type { AppDefinition } from '@openjcockpit/hub-contract';
import { StatusMessage } from '@openjcockpit/ui';
import { RemoteFailuresContext, type RemoteApps } from './loadRemoteApps';

const RemoteAppsContext = createContext<readonly AppDefinition[]>([]);

export function RemoteAppsProvider({
  remotes,
  children,
}: {
  remotes: RemoteApps;
  children: ReactNode;
}) {
  return (
    <RemoteAppsContext.Provider value={remotes.apps}>
      <RemoteFailuresContext.Provider value={remotes.failed}>
        {children}
      </RemoteFailuresContext.Provider>
    </RemoteAppsContext.Provider>
  );
}

export function useRemoteApps(): readonly AppDefinition[] {
  return useContext(RemoteAppsContext);
}

/** Lists the remotes that failed to load; renders nothing when every remote is up. */
export function RemoteFailuresNotice() {
  const { t } = useTranslation('dashboard');
  const failed = useContext(RemoteFailuresContext);
  if (failed.length === 0) return null;
  return (
    <StatusMessage role="status">
      {t('unavailableApps', { names: failed.join(', ') })}
    </StatusMessage>
  );
}
