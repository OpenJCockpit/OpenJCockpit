import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useSyncExternalStore,
  type ReactNode,
} from 'react';
import type {
  AppDefinition,
  QuickActionDefinition,
  TopbarDefinition,
  WidgetDefinition,
} from './appDefinition';
import { isAllowed, useHubSession } from './session';

export interface AppRegistry {
  allApps: readonly AppDefinition[];
  /** Apps the session may open. Hidden apps still mount their routes; the route guard answers 403. */
  visibleApps: readonly AppDefinition[];
  /** Visible apps shown in the nav rail. */
  navApps: readonly AppDefinition[];
  visibleTopbar: readonly TopbarDefinition[];
  visibleQuickActions: readonly QuickActionDefinition[];
  visibleWidgets: readonly WidgetDefinition[];
  /** Snapshot read; components should use `useBadgeFor` so they re-render on change. */
  badgeFor(appId: string): number | null;
  subscribeBadges(listener: () => void): () => void;
}

const RegistryContext = createContext<AppRegistry | null>(null);

type Listener = () => void;

/** Holds the latest badge per app so `useBadge` hooks run in leaf components, never in a loop. */
class BadgeStore {
  private readonly badges = new Map<string, number | null>();
  private readonly listeners = new Set<Listener>();

  subscribe = (listener: Listener) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  get = (appId: string) => this.badges.get(appId) ?? null;

  set(appId: string, value: number | null) {
    this.badges.set(appId, value);
    this.listeners.forEach((listener) => listener());
  }
}

function BadgeSource({
  appId,
  useBadge,
  store,
}: {
  appId: string;
  useBadge: () => number | null;
  store: BadgeStore;
}) {
  const value = useBadge();
  useEffect(() => {
    store.set(appId, value);
    return () => store.set(appId, null);
  }, [store, appId, value]);
  return null;
}

/** Sorts apps by `order` and answers what the session may see. Needs a `HubSession` above it. */
export function AppRegistryProvider({
  apps,
  children,
}: {
  apps: readonly AppDefinition[];
  children: ReactNode;
}) {
  const session = useHubSession();
  const store = useMemo(() => new BadgeStore(), []);
  // `has` is permission-list driven, so the list is the stable dependency for memoised results.
  const { permissions } = session;

  const registry = useMemo<AppRegistry>(() => {
    const allApps = [...apps].sort((a, b) => a.order - b.order);
    const visibleApps = allApps.filter((app) => isAllowed(session, app.requiredPermissions));
    const permitted = <T extends { requiredPermissions: readonly string[] }>(item: T) =>
      isAllowed(session, item.requiredPermissions);
    return {
      allApps,
      visibleApps,
      navApps: visibleApps.filter((app) => app.showInNav !== false),
      visibleTopbar: visibleApps.flatMap((app) => app.topbar ?? []).filter(permitted),
      visibleQuickActions: visibleApps.flatMap((app) => app.quickActions ?? []).filter(permitted),
      visibleWidgets: visibleApps.flatMap((app) => app.widgets ?? []).filter(permitted),
      badgeFor: store.get,
      subscribeBadges: store.subscribe,
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- `permissions` stands in for `session.has`
  }, [apps, permissions, store]);

  const sources = registry.visibleApps.flatMap((app) =>
    app.useBadge ? [{ appId: app.id, useBadge: app.useBadge }] : [],
  );

  return (
    <RegistryContext.Provider value={registry}>
      {sources.map((source) => (
        <BadgeSource key={source.appId} store={store} {...source} />
      ))}
      {children}
    </RegistryContext.Provider>
  );
}

export function useAppRegistry(): AppRegistry {
  const registry = useContext(RegistryContext);
  if (!registry) throw new Error('useAppRegistry needs an AppRegistryProvider above it.');
  return registry;
}

/** The live badge for one app; re-renders only when that badge changes. */
export function useBadgeFor(appId: string): number | null {
  const registry = useAppRegistry();
  const read = useCallback(() => registry.badgeFor(appId), [registry, appId]);
  return useSyncExternalStore(registry.subscribeBadges, read, read);
}
