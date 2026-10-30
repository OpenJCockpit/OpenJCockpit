import type { ComponentType } from 'react';
import type { RouteObject } from 'react-router';

/**
 * Version of this contract. A remote exports `contractVersion` next to `app`; the host rejects a
 * remote whose version is higher than its own. Bump only on a change a host must know about
 * (additive changes need no bump unless a host has to understand them).
 */
export const HUB_CONTRACT_VERSION = 1;

/** A module whose default export is a component, so the host can wrap it in `React.lazy`. */
export type LazyComponent = () => Promise<{ default: ComponentType }>;

export interface WidgetDefinition {
  id: string;
  titleKey: string;
  sourceAppId: string;
  requiredPermissions: readonly string[];
  component: LazyComponent;
  /** Permissions whose holders get this widget on their default Home; empty means everyone who may see it. */
  defaultFor: readonly string[];
  link: `/${string}`;
  descriptionKey?: string;
  cols?: number;
  rows?: number;
  categoryKey?: string;
  icon?: string;
  available?: boolean;
}

export interface QuickActionDefinition {
  id: string;
  labelKey: string;
  descriptionKey?: string;
  icon?: string;
  requiredPermissions: readonly string[];
  link: `/${string}`;
}

export interface TopbarDefinition {
  id: string;
  requiredPermissions: readonly string[];
  component: LazyComponent;
}

export interface AppDefinition {
  id: string;
  labelKey: string;
  icon: string;
  route: `/${string}`;
  order: number;
  /** Any-of; empty means every signed-in user. */
  requiredPermissions: readonly string[];
  showInNav?: boolean;
  /** A hook, called by the host in a stable position; returns the count or null. */
  useBadge?: () => number | null;
  /** Routes relative to `route`. */
  loadRoutes: () => Promise<RouteObject[]>;
  widgets?: WidgetDefinition[];
  quickActions?: QuickActionDefinition[];
  topbar?: TopbarDefinition[];
}

/** Structural check for what a remote exposes: a remote may export anything. */
export function isAppDefinition(value: unknown): value is AppDefinition {
  if (typeof value !== 'object' || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.id === 'string' &&
    candidate.id !== '' &&
    typeof candidate.route === 'string' &&
    candidate.route.startsWith('/') &&
    typeof candidate.loadRoutes === 'function'
  );
}
