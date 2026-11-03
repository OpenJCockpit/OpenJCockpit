import { beforeEach, describe, expect, it } from 'vitest';
import type { WidgetDefinition } from '@openjcockpit/hub-contract';
import {
  addItem,
  insertItem,
  defaultLayout,
  loadStoredLayout,
  moveItem,
  moveItemTo,
  removeItem,
  resizeTo,
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
      [widget('off', { available: false }), widget('on', { cols: 7, rows: 9 })],
      none,
    );
    expect(layout).toEqual([{ id: 'on', cols: 5, rows: 8 }]);
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
    expect(resolved).toEqual([{ id: 'a', cols: 5, rows: 2 }]);
  });
});

describe('editing', () => {
  const base = [
    { id: 'a', cols: 2, rows: 1 },
    { id: 'b', cols: 2, rows: 1 },
  ];

  it('moves within bounds only', () => {
    expect(moveItem(base, 'a', 1).map((i) => i.id)).toEqual(['b', 'a']);
    expect(moveItem(base, 'a', -1).map((i) => i.id)).toEqual(['a', 'b']);
    expect(moveItem(base, 'missing', 1)).toEqual(base);
  });

  it('moves an item into the place of another by id', () => {
    const three = [...base, { id: 'c', cols: 2, rows: 1 }];
    expect(moveItemTo(three, 'a', 'c').map((i) => i.id)).toEqual(['b', 'c', 'a']);
    expect(moveItemTo(three, 'c', 'a').map((i) => i.id)).toEqual(['c', 'a', 'b']);
    expect(moveItemTo(three, 'a', 'a')).toEqual(three);
    expect(moveItemTo(three, 'a', 'missing')).toEqual(three);
  });

  it('resizes to the nearest allowed span and clamps rows', () => {
    expect(resizeTo(base, 'a', 3.6, 2.4)[0]).toMatchObject({ cols: 4, rows: 2 });
    expect(resizeTo(base, 'a', 99, 99)[0]).toMatchObject({ cols: 5, rows: 8 });
    expect(resizeTo(base, 'a', 0, 0)[0]).toMatchObject({ cols: 2, rows: 2 });
    expect(resizeTo(base, 'missing', 5, 4)).toEqual(base);
  });

  it('removes and adds without duplicating', () => {
    expect(removeItem(base, 'a').map((i) => i.id)).toEqual(['b']);
    expect(addItem(base, widget('a'))).toEqual(base);
    expect(addItem(base, widget('c')).map((i) => i.id)).toEqual(['a', 'b', 'c']);
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
    localStorage.setItem('openjcockpit.homeLayout.v2.u1', '{not json');
    expect(loadStoredLayout('u1')).toBeNull();
    localStorage.setItem('openjcockpit.homeLayout.v2.u1', JSON.stringify([{ id: 1 }]));
    expect(loadStoredLayout('u1')).toBeNull();
    localStorage.setItem('openjcockpit.homeLayout.v2.u1', JSON.stringify([null]));
    expect(loadStoredLayout('u1')).toBeNull();
  });
});

describe('insertItem', () => {
  const layout = [
    { id: 'a', cols: 2, rows: 2 },
    { id: 'b', cols: 2, rows: 2 },
  ];

  it('inserts in front of the given widget', () => {
    expect(insertItem(layout, widget('c'), 'b').map((i) => i.id)).toEqual(['a', 'c', 'b']);
  });

  it('appends when no anchor is given or the anchor is absent', () => {
    expect(insertItem(layout, widget('c')).map((i) => i.id)).toEqual(['a', 'b', 'c']);
    expect(insertItem(layout, widget('c'), 'gone').map((i) => i.id)).toEqual(['a', 'b', 'c']);
  });

  it('leaves the layout unchanged when the widget is already placed', () => {
    expect(insertItem(layout, widget('a'), 'b')).toEqual(layout);
  });
});
