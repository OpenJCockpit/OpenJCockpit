import type { ReactNode } from 'react';
import type { Project } from '@openjcockpit/api';
import { HubProjectContext } from '@openjcockpit/hub-contract';

/** Hands the host's project list and selection to remotes as plain data, not as a query. */
export function HubProjectProvider({
  projects,
  selected,
  onSelect,
  children,
}: {
  projects: Project[];
  selected: Project | null;
  onSelect: (projectId: string) => void;
  children: ReactNode;
}) {
  return (
    <HubProjectContext.Provider value={{ projects, selected, select: onSelect }}>
      {children}
    </HubProjectContext.Provider>
  );
}
