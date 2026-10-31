import { beforeEach, describe, expect, it } from 'vitest';
import type { WidgetDefinition } from '@openjcockpit/hub-contract';
import {
  addItem,
  defaultLayout,
  loadStoredLayout,
  moveItem,
  removeItem,
  resizeCols,
  resizeRows,
  resolveLayout,
  storeLayout,
} from './homeLayout';

function widget(id: string, extra: Partial<WidgetDefinition> = {}): WidgetDefinition {
  return {
    id,
    titleKey: `${id}.title`,
    sourceAppId: id,
    requiredPermissions: [],
    component: () => Promise.resolve({ default: () => null }),
    defaultFor: [],
    link: `/${id}`,
    ...extra,
  };
}

const none = () => false;

describe('defaultLayout', () => {
  it('includes widgets without defaultFor, and those whose role the user has', () => {
    const widgets = [
      widget('everyone'),
      widget('admins', { defaultFor: ['admin'] }),
      widget('ops', { defaultFor: ['ops'] }),
    ];
    const ids = defaultLayout(widgets, (p) => p === 'admin').map((i) => i.id);
    expect(ids).toEqual(['everyone', 'admins']);
  });

  it('skips unavailable widgets and snaps declared sizes to the grid', () => {
    const layout = defaultLayout(
      [widget('off', { available: false }), widget('on', { cols: 5, rows: 9 })],
      none,
    );
    expect(layout).toEqual([{ id: 'on', cols: 6, rows: 4 }]);
  });
});

describe('resolveLayout', () => {
  it('drops unknown, hidden and duplicate entries and repairs sizes', () => {
    const widgets = [widget('a'), widget('b', { available: false })];
    const resolved = resolveLayout(
      [
        { id: 'a', cols: 100, rows: 0 },
        { id: 'a', cols: 3, rows: 1 },
        { id: 'b', cols: 3, rows: 1 },
        { id: 'gone', cols: 3, rows: 1 },
      ],
      widgets,
    );
    expect(resolved).toEqual([{ id: 'a', cols: 12, rows: 1 }]);
  });
});

describe('editing', () => {
  const base = [
    { id: 'a', cols: 6, rows: 1 },
    { id: 'b', cols: 6, rows: 1 },
  ];

  it('moves within bounds only', () => {
    expect(moveItem(base, 'a', 1).map((i) => i.id)).toEqual(['b', 'a']);
    expect(moveItem(base, 'a', -1).map((i) => i.id)).toEqual(['a', 'b']);
    expect(moveItem(base, 'missing', 1)).toEqual(base);
  });

  it('removes and adds without duplicating', () => {
    expect(removeItem(base, 'a').map((i) => i.id)).toEqual(['b']);
    expect(addItem(base, widget('a'))).toEqual(base);
    expect(addItem(base, widget('c')).map((i) => i.id)).toEqual(['a', 'b', 'c']);
  });

  it('steps sizes and stops at the limits', () => {
    expect(resizeCols(base, 'a', 1)[0]?.cols).toBe(9);
    expect(resizeCols([{ id: 'a', cols: 12, rows: 1 }], 'a', 1)[0]?.cols).toBe(12);
    expect(resizeCols([{ id: 'a', cols: 3, rows: 1 }], 'a', -1)[0]?.cols).toBe(3);
    expect(resizeRows(base, 'a', -1)[0]?.rows).toBe(1);
    expect(resizeRows([{ id: 'a', cols: 6, rows: 4 }], 'a', 1)[0]?.rows).toBe(4);
  });
});

describe('storage', () => {
  beforeEach(() => localStorage.clear());

  it('round-trips a layout per user and clears it with null', () => {
    const layout = [{ id: 'a', cols: 6, rows: 2 }];
    storeLayout('u1', layout);
    expect(loadStoredLayout('u1')).toEqual(layout);
    expect(loadStoredLayout('u2')).toBeNull();
    storeLayout('u1', null);
    expect(loadStoredLayout('u1')).toBeNull();
  });

  it('ignores corrupt data', () => {
    localStorage.setItem('openjcockpit.homeLayout.u1', '{not json');
    expect(loadStoredLayout('u1')).toBeNull();
    localStorage.setItem('openjcockpit.homeLayout.u1', JSON.stringify([{ id: 1 }]));
    expect(loadStoredLayout('u1')).toBeNull();
    localStorage.setItem('openjcockpit.homeLayout.u1', JSON.stringify([null]));
    expect(loadStoredLayout('u1')).toBeNull();
  });
});
