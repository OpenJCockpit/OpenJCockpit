import { createContext, useContext, type ReactNode } from 'react';
import type { AppDefinition } from '@openjcockpit/hub-contract';
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
