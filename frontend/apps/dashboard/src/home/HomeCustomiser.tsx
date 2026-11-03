import {
  useId,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type DragEvent,
  type KeyboardEvent,
  type PointerEvent,
} from 'react';
import { useTranslation } from '@openjcockpit/i18n';
import { useAppRegistry, type WidgetDefinition } from '@openjcockpit/hub-contract';
import { Button, Icon, IconBox, ModalShell, Panel, cx, formInputClass } from '@openjcockpit/ui';
import { RemoteSlot } from '../federation/RemoteSlot';
import {
  COL_STEPS,
  GRID_COLUMNS,
  MAX_ROWS,
  sizeLimits,
  insertItem,
  moveItem,
  moveItemTo,
  removeItem,
  resizeTo,
  type LayoutItem,
} from './homeLayout';
import styles from './HomeCustomiser.module.scss';
import gridStyles from './HomeGrid.module.scss';

type Drag =
  { kind: 'tile'; id: string } | { kind: 'library'; id: string; widget: WidgetDefinition };

interface Props {
  initial: LayoutItem[];
  /** The layout "Reset" returns to. */
  defaultItems: LayoutItem[];
  widgets: readonly WidgetDefinition[];
  /** `isDefault` is true when the user reset and changed nothing after, so Home keeps following the defaults. */
  onDone: (layout: LayoutItem[], isDefault: boolean) => void;
  onCancel: () => void;
}

const GLYPH = {
  earlier: <Icon name="arrow-left" />,
  later: <Icon name="arrow-right" />,
  handle: <Icon name="handle" />,
  grip: <Icon name="resize" />,
  check: <Icon name="check" />,
  plus: <Icon name="plus" />,
  reset: <Icon name="reset" />,
  remove: <Icon name="close" />,
} as const;

type Translate = (key: never) => string;

/** The per-tile buttons: move earlier/later, each disabled at its end. */
function tileControls(layout: LayoutItem[], item: LayoutItem, index: number, t: Translate) {
  return [
    {
      id: 'earlier',
      label: t('home.moveEarlier' as never),
      off: index === 0,
      next: moveItem(layout, item.id, -1),
    },
    {
      id: 'later',
      label: t('home.moveLater' as never),
      off: index === layout.length - 1,
      next: moveItem(layout, item.id, 1),
    },
  ] as const;
}

/**
 * Full-screen editor for the Home layout. Works on a draft: Done saves it, Cancel drops it.
 * Tiles reorder by dragging, drop onto the library to be removed, and library entries drop
 * onto the grid to be added; the buttons do the same for keyboard users.
 */
export function HomeCustomiser({ initial, defaultItems, widgets, onDone, onCancel }: Props) {
  const { t } = useTranslation('dashboard');
  const { allApps } = useAppRegistry();
  const titleId = useId();
  const [layout, setLayout] = useState(initial);
  const [isDefault, setIsDefault] = useState(false);
  const [drag, setDrag] = useState<Drag | null>(null);
  const [overId, setOverId] = useState<string | null>(null);
  const [overLibrary, setOverLibrary] = useState(false);
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState<string | null>(null);
  const resizing = useRef<{
    id: string;
    x: number;
    y: number;
    cols: number;
    rows: number;
    colUnit: number;
    rowUnit: number;
  } | null>(null);

  const text = (key: string) => String(t(key as never));
  const widgetsById = useMemo(() => new Map(widgets.map((w) => [w.id, w])), [widgets]);
  const appLabel = (id: string) => {
    const app = allApps.find((a) => a.id === id);
    return app ? text(app.labelKey) : id;
  };
  const categoryOf = (w: WidgetDefinition) =>
    w.categoryKey ? text(w.categoryKey) : appLabel(w.sourceAppId);
  const sizeOf = (w: WidgetDefinition) => {
    const cols = w.cols ?? 2;
    if (cols <= 1) return t('home.sizeCompact');
    if (cols <= 2) return t('home.sizeStandard');
    if (cols <= 4) return t('home.sizeWide');
    return t('home.sizeFull');
  };

  const categoryCounts = useMemo(() => {
    const counts = new Map<string, number>();
    for (const w of widgets) counts.set(categoryOf(w), (counts.get(categoryOf(w)) ?? 0) + 1);
    return counts;
    // eslint-disable-next-line react-hooks/exhaustive-deps -- labels follow the language
  }, [widgets, allApps, t]);

  const needle = query.trim().toLowerCase();
  const visible = widgets.filter((w) => {
    if (category && categoryOf(w) !== category) return false;
    if (!needle) return true;
    return [text(w.titleKey), w.descriptionKey ? text(w.descriptionKey) : '', categoryOf(w)].some(
      (value) => value.toLowerCase().includes(needle),
    );
  });
  const onHome = new Set(layout.map((item) => item.id));

  function change(next: LayoutItem[]) {
    setLayout(next);
    setIsDefault(false);
  }

  function endDrag() {
    setDrag(null);
    setOverId(null);
    setOverLibrary(false);
  }

  function dropOnTile(targetId: string) {
    if (drag?.kind === 'tile') change(moveItemTo(layout, drag.id, targetId));
    else if (drag?.kind === 'library') change(insertItem(layout, drag.widget, targetId));
    endDrag();
  }

  function dropOnGrid() {
    if (drag?.kind === 'library') change(insertItem(layout, drag.widget));
    endDrag();
  }

  function dropOnLibrary() {
    if (drag?.kind === 'tile') change(removeItem(layout, drag.id));
    endDrag();
  }

  /** Corner resize: the pointer's travel is turned into grid spans and snapped to the allowed sizes. */
  function startResize(event: PointerEvent<HTMLElement>, item: LayoutItem) {
    // The grip only renders inside a tile, and tiles only inside the grid.
    const tile = event.currentTarget.closest('article')!;
    const grid = tile.parentElement!;
    event.preventDefault();
    event.currentTarget.setPointerCapture?.(event.pointerId);
    resizing.current = {
      id: item.id,
      x: event.clientX,
      y: event.clientY,
      cols: item.cols,
      rows: item.rows,
      colUnit: grid.getBoundingClientRect().width / GRID_COLUMNS || 1,
      rowUnit: tile.getBoundingClientRect().height / item.rows || 1,
    };
  }

  function moveResize(event: PointerEvent<HTMLElement>) {
    const start = resizing.current;
    if (!start) return;
    setLayout((current) =>
      resizeTo(
        current,
        start.id,
        start.cols + (event.clientX - start.x) / start.colUnit,
        start.rows + (event.clientY - start.y) / start.rowUnit,
        widgetsById.get(start.id),
      ),
    );
    setIsDefault(false);
  }

  function endResize() {
    resizing.current = null;
  }

  function resizeWithKeys(event: KeyboardEvent<HTMLElement>, item: LayoutItem) {
    const widget = widgetsById.get(item.id);
    const { minCols, minRows } = sizeLimits(widget);
    const steps = COL_STEPS.filter((s) => s >= minCols);
    const step = steps.indexOf(item.cols as (typeof COL_STEPS)[number]);
    const moves: Record<string, [number, number]> = {
      ArrowLeft: [steps[Math.max(0, step - 1)]!, item.rows],
      ArrowRight: [steps[Math.min(steps.length - 1, step + 1)]!, item.rows],
      ArrowUp: [item.cols, Math.max(minRows, item.rows - 1)],
      ArrowDown: [item.cols, Math.min(MAX_ROWS, item.rows + 1)],
    };
    const move = moves[event.key];
    if (!move) return;
    event.preventDefault();
    change(resizeTo(layout, item.id, move[0], move[1], widget));
  }

  function startDrag(event: DragEvent<HTMLElement>, next: Drag) {
    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData('text/plain', next.id);
    setDrag(next);
  }

  function allowDrop(event: DragEvent) {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
  }

  return (
    <ModalShell
      labelledBy={titleId}
      onDismiss={onCancel}
      className={styles.surface}
      overlayClassName={styles.overlay}
    >
      <header className={styles.header}>
        <div>
          <h1 id={titleId}>{t('home.customiseTitle')}</h1>
          <p>{t('home.customiseHint')}</p>
        </div>
        <div className={styles.actions}>
          <Button
            onClick={() => {
              setLayout(defaultItems);
              setIsDefault(true);
            }}
            data-testid="home-reset"
          >
            {GLYPH.reset} {t('home.reset')}
          </Button>
          <Button onClick={onCancel} data-testid="home-cancel">
            {t('home.cancel')}
          </Button>
          <Button
            variant="primary"
            onClick={() => onDone(layout, isDefault)}
            data-testid="home-done"
          >
            {GLYPH.check} {t('home.done')}
          </Button>
        </div>
      </header>

      <div className={styles.body}>
        <section
          className={styles.canvas}
          onDragOver={allowDrop}
          onDrop={(e) => {
            e.preventDefault();
            dropOnGrid();
          }}
        >
          <div className={gridStyles.grid} data-testid="home-grid">
            {layout.map((item, index) => {
              const widget = widgetsById.get(item.id);
              if (!widget) return null;
              const title = text(widget.titleKey);
              const controls = tileControls(layout, item, index, t);
              return (
                <Panel
                  as="article"
                  key={item.id}
                  className={cx(
                    gridStyles.tile,
                    styles.tile,
                    drag?.id === item.id && styles.dragging,
                    drag && drag.id !== item.id && overId === item.id && styles.dropTarget,
                  )}
                  // eslint-disable-next-line no-restricted-syntax -- runtime grid spans passed as custom properties
                  style={{ '--cols': item.cols, '--rows': item.rows } as CSSProperties}
                  onDragEnter={() => drag && setOverId(item.id)}
                  onDragOver={allowDrop}
                  onDrop={(e: DragEvent) => {
                    e.preventDefault();
                    e.stopPropagation();
                    dropOnTile(item.id);
                  }}
                  data-testid={`widget-${widget.id}`}
                  data-cols={item.cols}
                  data-rows={item.rows}
                >
                  <header className={gridStyles.tileHeader}>
                    <h2 className={gridStyles.tileTitle}>{title}</h2>
                    <Button
                      size="sm"
                      className={styles.remove}
                      aria-label={`${t('home.remove')}: ${title}`}
                      title={t('home.remove')}
                      onClick={() => change(removeItem(layout, item.id))}
                      data-testid={`widget-remove-${widget.id}`}
                    >
                      {' '}
                      {GLYPH.remove}{' '}
                    </Button>
                  </header>
                  <div className={cx(gridStyles.tileBody, styles.tileBody)}>
                    <RemoteSlot item={widget} />
                  </div>
                  <div className={styles.controls}>
                    <span
                      className={styles.handle}
                      title={t('home.dragHandle')}
                      aria-hidden="true"
                      draggable
                      onDragStart={(e: DragEvent<HTMLElement>) => {
                        // Only the handle starts a drag, but the whole tile is what is shown moving.
                        e.dataTransfer.setDragImage(e.currentTarget.closest('article')!, 24, 24);
                        startDrag(e, { kind: 'tile', id: item.id });
                      }}
                      onDragEnd={endDrag}
                      data-testid={`widget-handle-${widget.id}`}
                    >
                      {GLYPH.handle}
                    </span>
                    {controls.map(({ id, label, off, next }) => (
                      <Button
                        key={id}
                        size="sm"
                        aria-label={`${label}: ${title}`}
                        title={label}
                        disabled={off}
                        onClick={() => change(next)}
                        data-testid={`widget-${id}-${widget.id}`}
                      >
                        {GLYPH[id]}
                      </Button>
                    ))}
                  </div>
                  <button
                    type="button"
                    className={styles.grip}
                    aria-label={`${t('home.resize')}: ${title}`}
                    title={t('home.resizeHint')}
                    onPointerDown={(e) => startResize(e, item)}
                    onPointerMove={moveResize}
                    onPointerUp={endResize}
                    onPointerCancel={endResize}
                    onKeyDown={(e) => resizeWithKeys(e, item)}
                    data-testid={`widget-resize-${widget.id}`}
                  >
                    {GLYPH.grip}
                  </button>
                </Panel>
              );
            })}
          </div>
        </section>

        <aside
          className={cx(styles.library, drag?.kind === 'tile' && overLibrary && styles.libraryDrop)}
          aria-label={t('home.libraryTitle')}
          data-testid="home-library"
          onDragOver={allowDrop}
          onDragEnter={() => setOverLibrary(true)}
          onDragLeave={(e) => {
            if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setOverLibrary(false);
          }}
          onDrop={(e) => {
            e.preventDefault();
            dropOnLibrary();
          }}
        >
          <div className={styles.libraryHead}>
            <h2>{t('home.libraryTitle')}</h2>
            <p>{drag?.kind === 'tile' ? t('home.dropToRemove') : t('home.librarySubtitle')}</p>
            <input
              type="search"
              className={formInputClass}
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={t('home.search')}
              aria-label={t('home.search')}
              data-testid="library-search"
            />
            <div className={styles.chips}>
              <Button
                size="sm"
                aria-pressed={category === null}
                className={cx(styles.chip, category === null && styles.chipActive)}
                onClick={() => setCategory(null)}
              >
                {t('home.all')} <span>{widgets.length}</span>
              </Button>
              {[...categoryCounts].map(([label, count]) => (
                <Button
                  key={label}
                  size="sm"
                  aria-pressed={category === label}
                  className={cx(styles.chip, category === label && styles.chipActive)}
                  onClick={() => setCategory(category === label ? null : label)}
                >
                  {label} <span>{count}</span>
                </Button>
              ))}
            </div>
          </div>

          <ul className={styles.list}>
            {visible.length === 0 && <li className={styles.empty}>{t('home.noMatches')}</li>}
            {visible.map((widget) => {
              const placed = onHome.has(widget.id);
              return (
                <li
                  key={widget.id}
                  className={styles.entry}
                  draggable={!placed}
                  onDragStart={(e) =>
                    !placed && startDrag(e, { kind: 'library', id: widget.id, widget })
                  }
                  onDragEnd={endDrag}
                  data-testid={`library-${widget.id}`}
                >
                  <IconBox label={<Icon name="grid" />} compact />
                  <div className={styles.entryText}>
                    <strong>{text(widget.titleKey)}</strong>
                    {widget.descriptionKey && <span>{text(widget.descriptionKey)}</span>}
                    <small>
                      {categoryOf(widget)} · {sizeOf(widget)}
                    </small>
                  </div>
                  {placed ? (
                    <span className={styles.badge}>
                      {GLYPH.check} {t('home.onHome')}
                    </span>
                  ) : (
                    <Button
                      size="sm"
                      onClick={() => change(insertItem(layout, widget))}
                      data-testid={`widget-add-${widget.id}`}
                    >
                      {GLYPH.plus} {t('home.add')}
                    </Button>
                  )}
                </li>
              );
            })}
          </ul>
        </aside>
      </div>
    </ModalShell>
  );
}
