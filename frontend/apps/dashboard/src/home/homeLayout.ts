import type { WidgetDefinition } from '@openjcockpit/hub-contract';

/** One placed widget: its place in the list is its place in the grid; sizes are grid spans. */
export interface LayoutItem {
  id: string;
  cols: number;
  rows: number;
}

/** The Home grid is this many columns wide; a tile spans 1 to GRID_COLUMNS of them. */
export const GRID_COLUMNS = 5;
export const COL_STEPS = [1, 2, 3, 4, 5] as const;
/** Smallest tile a widget gets unless it asks for less or more with `minCols` / `minRows`. */
export const MIN_COLS = 2;
export const MIN_ROWS = 2;
export const MAX_ROWS = 8;

const DEFAULT_COLS = 2;
const DEFAULT_ROWS = MIN_ROWS;

/** The smallest size a widget may be shown at. */
export function sizeLimits(widget?: Pick<WidgetDefinition, 'minCols' | 'minRows'>) {
  return {
    minCols: clamp(Math.round(widget?.minCols ?? MIN_COLS), 1, GRID_COLUMNS),
    minRows: clamp(Math.round(widget?.minRows ?? MIN_ROWS), 1, MAX_ROWS),
  };
}
// v2: sizes became spans of a 5-column grid; layouts saved against the old 12 columns are not reused.
const STORAGE_PREFIX = 'openjcockpit.homeLayout.v2.';

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

/** Snaps to the closest allowed column span so stored or contributed sizes cannot break the grid. */
function snapCols(cols: number, min = MIN_COLS): number {
  return COL_STEPS.filter((step) => step >= min).reduce((best, step) =>
    Math.abs(step - cols) < Math.abs(best - cols) ? step : best,
  );
}

function place(widget: WidgetDefinition): LayoutItem {
  const { minCols, minRows } = sizeLimits(widget);
  return {
    id: widget.id,
    cols: snapCols(widget.cols ?? DEFAULT_COLS, minCols),
    rows: clamp(Math.round(widget.rows ?? DEFAULT_ROWS), minRows, MAX_ROWS),
  };
}

/**
 * The layout a user starts with: widgets whose `defaultFor` names one of their permissions, or
 * every visible widget that leaves `defaultFor` empty.
 */
export function defaultLayout(
  widgets: readonly WidgetDefinition[],
  has: (permission: string) => boolean,
): LayoutItem[] {
  return widgets
    .filter((w) => w.available !== false)
    .filter((w) => w.defaultFor.length === 0 || w.defaultFor.some(has))
    .map(place);
}

/** Keeps a stored layout usable: drops widgets that are gone or hidden, duplicates and bad sizes. */
export function resolveLayout(
  stored: readonly LayoutItem[],
  widgets: readonly WidgetDefinition[],
): LayoutItem[] {
  const known = new Map(widgets.filter((w) => w.available !== false).map((w) => [w.id, w]));
  const seen = new Set<string>();
  const result: LayoutItem[] = [];
  for (const item of stored) {
    const widget = known.get(item.id);
    if (!widget || seen.has(item.id)) continue;
    seen.add(item.id);
    const { minCols, minRows } = sizeLimits(widget);
    result.push({
      id: item.id,
      cols: snapCols(item.cols, minCols),
      rows: clamp(Math.round(item.rows), minRows, MAX_ROWS),
    });
  }
  return result;
}

/** Moves an item one place; at either end the layout is returned unchanged. */
export function moveItem(layout: readonly LayoutItem[], id: string, delta: -1 | 1): LayoutItem[] {
  const from = layout.findIndex((item) => item.id === id);
  const to = from + delta;
  if (from < 0 || to < 0 || to >= layout.length) return [...layout];
  const next = [...layout];
  [next[from], next[to]] = [next[to]!, next[from]!];
  return next;
}

/** Drops an item into the place of another one (drag and drop); unknown ids leave the layout as is. */
export function moveItemTo(
  layout: readonly LayoutItem[],
  id: string,
  targetId: string,
): LayoutItem[] {
  const from = layout.findIndex((item) => item.id === id);
  const to = layout.findIndex((item) => item.id === targetId);
  if (from < 0 || to < 0 || from === to) return [...layout];
  const next = [...layout];
  const [moved] = next.splice(from, 1);
  next.splice(to, 0, moved!);
  return next;
}

export function removeItem(layout: readonly LayoutItem[], id: string): LayoutItem[] {
  return layout.filter((item) => item.id !== id);
}

export function addItem(layout: readonly LayoutItem[], widget: WidgetDefinition): LayoutItem[] {
  return layout.some((item) => item.id === widget.id) ? [...layout] : [...layout, place(widget)];
}

/** Adds a widget, in front of `beforeId` when given and present, otherwise at the end. */
export function insertItem(
  layout: readonly LayoutItem[],
  widget: WidgetDefinition,
  beforeId?: string,
): LayoutItem[] {
  if (layout.some((item) => item.id === widget.id)) return [...layout];
  const at = layout.findIndex((item) => item.id === beforeId);
  const next = [...layout];
  next.splice(at < 0 ? next.length : at, 0, place(widget));
  return next;
}

/** Sets an item's size from a (possibly fractional) span, snapped to the allowed steps. */
export function resizeTo(
  layout: readonly LayoutItem[],
  id: string,
  cols: number,
  rows: number,
  widget?: Pick<WidgetDefinition, 'minCols' | 'minRows'>,
): LayoutItem[] {
  const { minCols, minRows } = sizeLimits(widget);
  const next = { cols: snapCols(cols, minCols), rows: clamp(Math.round(rows), minRows, MAX_ROWS) };
  return layout.map((item) => (item.id === id ? { ...item, ...next } : item));
}

function isLayoutItem(value: unknown): value is LayoutItem {
  if (typeof value !== 'object' || value === null) return false;
  const item = value as Record<string, unknown>;
  return typeof item.id === 'string' && Number.isFinite(item.cols) && Number.isFinite(item.rows);
}

/** The saved layout for a user, or null when none is stored or it cannot be read. */
export function loadStoredLayout(userKey: string): LayoutItem[] | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_PREFIX + userKey);
    if (raw === null) return null;
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) && parsed.every(isLayoutItem) ? parsed : null;
  } catch {
    return null;
  }
}

export function storeLayout(userKey: string, layout: readonly LayoutItem[] | null) {
  try {
    if (layout === null) window.localStorage.removeItem(STORAGE_PREFIX + userKey);
    else window.localStorage.setItem(STORAGE_PREFIX + userKey, JSON.stringify(layout));
  } catch {
    // The layout only lasts for this page view.
  }
}
