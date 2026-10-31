import type { WidgetDefinition } from '@openjcockpit/hub-contract';

/** One placed widget: its place in the list is its place in the grid; sizes are grid spans. */
export interface LayoutItem {
  id: string;
  cols: number;
  rows: number;
}

export const GRID_COLUMNS = 12;
export const COL_STEPS = [3, 6, 9, 12] as const;
export const MAX_ROWS = 4;

const DEFAULT_COLS = 6;
const DEFAULT_ROWS = 1;
const STORAGE_PREFIX = 'openjcockpit.homeLayout.';

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

/** Snaps to the closest allowed column span so stored or contributed sizes cannot break the grid. */
function snapCols(cols: number): number {
  return COL_STEPS.reduce((best, step) =>
    Math.abs(step - cols) < Math.abs(best - cols) ? step : best,
  );
}

function place(widget: WidgetDefinition): LayoutItem {
  return {
    id: widget.id,
    cols: snapCols(widget.cols ?? DEFAULT_COLS),
    rows: clamp(Math.round(widget.rows ?? DEFAULT_ROWS), 1, MAX_ROWS),
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
  const known = new Set(widgets.filter((w) => w.available !== false).map((w) => w.id));
  const seen = new Set<string>();
  const result: LayoutItem[] = [];
  for (const item of stored) {
    if (!known.has(item.id) || seen.has(item.id)) continue;
    seen.add(item.id);
    result.push({
      id: item.id,
      cols: snapCols(item.cols),
      rows: clamp(Math.round(item.rows), 1, MAX_ROWS),
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

export function removeItem(layout: readonly LayoutItem[], id: string): LayoutItem[] {
  return layout.filter((item) => item.id !== id);
}

export function addItem(layout: readonly LayoutItem[], widget: WidgetDefinition): LayoutItem[] {
  return layout.some((item) => item.id === widget.id) ? [...layout] : [...layout, place(widget)];
}

/** Steps the column span through `COL_STEPS`, stopping at the ends. */
export function resizeCols(layout: readonly LayoutItem[], id: string, delta: -1 | 1): LayoutItem[] {
  return layout.map((item) => {
    if (item.id !== id) return item;
    const index = COL_STEPS.indexOf(snapCols(item.cols) as (typeof COL_STEPS)[number]);
    const cols = COL_STEPS[clamp(index + delta, 0, COL_STEPS.length - 1)]!;
    return { ...item, cols };
  });
}

export function resizeRows(layout: readonly LayoutItem[], id: string, delta: -1 | 1): LayoutItem[] {
  return layout.map((item) =>
    item.id === id ? { ...item, rows: clamp(item.rows + delta, 1, MAX_ROWS) } : item,
  );
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
