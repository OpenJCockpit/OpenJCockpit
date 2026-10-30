import type { ReactNode } from 'react';
import { HubProjectContext, type HubProject } from '../project';

export function FakeHubProjectProvider({
  projects = [],
  selected = null,
  onSelect = () => undefined,
  children,
}: {
  projects?: readonly HubProject[];
  selected?: HubProject | null;
  onSelect?: (id: string) => void;
  children: ReactNode;
}) {
  return (
    <HubProjectContext.Provider value={{ projects, selected, select: onSelect }}>
      {children}
    </HubProjectContext.Provider>
  );
}
