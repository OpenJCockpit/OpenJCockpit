import type { ComponentType } from 'react';
import type { RouteObject } from 'react-router';

/**
 * Version of this contract. A remote exports `contractVersion` next to `app`; the host rejects a
 * remote whose version is higher than its own. Bump only on a change a host must know about
 * (additive changes need no bump unless a host has to understand them).
 */
export const HUB_CONTRACT_VERSION = 2;

/** A module whose default export is a component, so the host can wrap it in `React.lazy`. */
export type LazyComponent = () => Promise<{ default: ComponentType }>;

/**
 * `defaultFor` value for a widget that stays off the default Home and is only added by choice.
 * It names a permission nobody holds.
 */
export const OPT_IN_ONLY: readonly string[] = ['hub:opt-in-only'];

export interface WidgetDefinition {
  id: string;
  titleKey: string;
  sourceAppId: string;
  requiredPermissions: readonly string[];
  component: LazyComponent;
  /** Permissions whose holders get this widget on their default Home; empty means everyone who may see it, `OPT_IN_ONLY` means nobody. */
  defaultFor: readonly string[];
  link: `/${string}`;
  descriptionKey?: string;
  /** Default width on the Home grid, in columns of its 5-column grid (1 to 5; 2 when omitted). */
  cols?: number;
  /** Default height on the Home grid, in rows (2 to 8; 2 when omitted). */
  rows?: number;
  /** Narrowest the user may make the tile, in columns (1 to 5; 2 when omitted). */
  minCols?: number;
  /** Shortest the user may make the tile, in rows (2 when omitted). */
  minRows?: number;
  categoryKey?: string;
  icon?: string;
  available?: boolean;
}

/**
 * A section of the host's Settings page. The host owns the URL (`/settings/<id>`), the sub-navigation,
 * the heading and the project guard; the component renders only its own form content.
 */
/** Which host screen shows a section: the global Settings, or the selected project's settings. */
export type SettingsScreen = 'settings' | 'project';

export interface SettingsSectionDefinition {
  /** Unique across all apps; also the URL segment: /settings/<id> or /project-settings/<id>. */
  id: string;
  sourceAppId: string;
  titleKey: string;
  descriptionKey?: string;
  icon?: string;
  /** Any-of; empty means every signed-in user. */
  requiredPermissions: readonly string[];
  /** Sort key inside a group. */
  order: number;
  /** Screen the section appears on; defaults to the global Settings. */
  screen?: SettingsScreen;
  /** Group heading key, e.g. 'settings.groups.project'. Sections without one go in a default group. */
  groupKey?: string;
  /** Needs a selected project; the host shows the project-required empty state otherwise. */
  requiresProject?: boolean;
  /** Content only: no header, no back button, no page chrome. Registers its own translations. */
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
  /** Translated tooltip saying what the badge counts, when the page does not make that obvious. */
  badgeLabelKey?: string;
  /** Routes relative to `route`. */
  loadRoutes: () => Promise<RouteObject[]>;
  widgets?: WidgetDefinition[];
  settings?: SettingsSectionDefinition[];
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
