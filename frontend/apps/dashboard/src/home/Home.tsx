import { useMemo, useState, type CSSProperties } from 'react';
import { useNavigate } from 'react-router';
import { useTranslation } from '@openjcockpit/i18n';
import {
  useAppRegistry,
  useHubHeader,
  useHubSession,
  type WidgetDefinition,
} from '@openjcockpit/hub-contract';
import {
  AppShell,
  Button,
  Eyebrow,
  Panel,
  PanelHeader,
  PanelTitle,
  cx,
  pageClass,
  pageHeaderClass,
} from '@openjcockpit/ui';
import { RemoteSlot } from '../federation/RemoteSlot';
import {
  COL_STEPS,
  GRID_COLUMNS,
  MAX_ROWS,
  addItem,
  defaultLayout,
  loadStoredLayout,
  moveItem,
  removeItem,
  resizeCols,
  resizeRows,
  resolveLayout,
  storeLayout,
  type LayoutItem,
} from './homeLayout';
import styles from './Home.module.scss';

export function Home() {
  const { t } = useTranslation('dashboard');
  const navigate = useNavigate();
  const session = useHubSession();
  const { visibleWidgets, visibleQuickActions } = useAppRegistry();
  const header = useHubHeader();
  const userKey = session.user?.id ?? session.user?.username ?? 'anonymous';

  // `null` means the user never customised: the default follows the widgets they may see.
  const [saved, setSaved] = useState<LayoutItem[] | null>(() => loadStoredLayout(userKey));
  const [customising, setCustomising] = useState(false);

  const layout = useMemo(
    () =>
      saved
        ? resolveLayout(saved, visibleWidgets)
        : defaultLayout(visibleWidgets, (permission) => session.has(permission)),
    // eslint-disable-next-line react-hooks/exhaustive-deps -- `permissions` stands in for `session.has`
    [saved, visibleWidgets, session.permissions],
  );
  const widgetsById = useMemo(
    () => new Map(visibleWidgets.map((widget) => [widget.id, widget])),
    [visibleWidgets],
  );
  const addable = visibleWidgets.filter(
    (widget) => widget.available !== false && !layout.some((item) => item.id === widget.id),
  );

  function update(next: LayoutItem[] | null) {
    setSaved(next);
    storeLayout(userKey, next);
  }

  // Remote keys are not in the host's typed catalog.
  const text = (key: string) => String(t(key as never));
  const greeting = session.user?.firstName || session.user?.username;

  return (
    <AppShell>
      {header}
      <main className={pageClass} data-testid="home">
        <div className={cx(pageHeaderClass, styles.heading)}>
          <div>
            <Eyebrow>{t('home.eyebrow')}</Eyebrow>
            <h1>{greeting ? t('home.greeting', { name: greeting }) : t('home.title')}</h1>
          </div>
          <div className={styles.toolbar}>
            {customising && (
              <Button size="sm" onClick={() => update(null)} data-testid="home-reset">
                {t('home.reset')}
              </Button>
            )}
            <Button
              size="sm"
              variant={customising ? 'primary' : 'default'}
              aria-pressed={customising}
              onClick={() => setCustomising(!customising)}
              data-testid="home-customise"
            >
              {customising ? t('home.done') : t('home.customise')}
            </Button>
          </div>
        </div>

        {visibleQuickActions.length > 0 && (
          <nav className={styles.quickActions} aria-label={t('home.quickActions')}>
            {visibleQuickActions.map((action) => (
              <Button
                key={action.id}
                size="sm"
                title={action.descriptionKey ? text(action.descriptionKey) : undefined}
                onClick={() => void navigate(action.link)}
                data-testid={`quick-action-${action.id}`}
              >
                {text(action.labelKey)}
              </Button>
            ))}
          </nav>
        )}

        {layout.length === 0 ? (
          <p className={styles.empty} data-testid="home-empty">
            {t('home.empty')}
          </p>
        ) : (
          <div className={styles.grid} data-testid="home-grid">
            {layout.map((item, index) => {
              // The layout is resolved against `visibleWidgets`, so every item has its widget.
              const widget = widgetsById.get(item.id)!;
              return (
                <WidgetCard
                  key={item.id}
                  widget={widget}
                  item={item}
                  customising={customising}
                  first={index === 0}
                  last={index === layout.length - 1}
                  onChange={update}
                  layout={layout}
                />
              );
            })}
          </div>
        )}

        {customising && (
          <section className={styles.available} aria-label={t('home.available')}>
            <h2>{t('home.available')}</h2>
            {addable.length === 0 ? (
              <p className={styles.empty}>{t('home.noneAvailable')}</p>
            ) : (
              <ul>
                {addable.map((widget) => (
                  <li key={widget.id}>
                    <span>{text(widget.titleKey)}</span>
                    {widget.descriptionKey && <small>{text(widget.descriptionKey)}</small>}
                    <Button
                      size="sm"
                      onClick={() => update(addItem(layout, widget))}
                      data-testid={`widget-add-${widget.id}`}
                    >
                      {t('home.add')}
                    </Button>
                  </li>
                ))}
              </ul>
            )}
          </section>
        )}
      </main>
    </AppShell>
  );
}

/** Control glyphs: symbols, not words; each button's accessible name is its translated label. */
const GLYPH = {
  earlier: '←',
  later: '→',
  narrower: '−',
  wider: '+',
  shorter: '↑',
  taller: '↓',
  remove: '×',
} as const;

interface CardProps {
  widget: WidgetDefinition;
  item: LayoutItem;
  layout: LayoutItem[];
  customising: boolean;
  first: boolean;
  last: boolean;
  onChange: (next: LayoutItem[]) => void;
}

function WidgetCard({ widget, item, layout, customising, first, last, onChange }: CardProps) {
  const { t } = useTranslation('dashboard');
  const navigate = useNavigate();
  const title = String(t(widget.titleKey as never));
  const controls = [
    {
      id: 'earlier',
      label: t('home.moveEarlier'),
      off: first,
      next: moveItem(layout, item.id, -1),
    },
    { id: 'later', label: t('home.moveLater'), off: last, next: moveItem(layout, item.id, 1) },
    {
      id: 'narrower',
      label: t('home.narrower'),
      off: item.cols <= COL_STEPS[0],
      next: resizeCols(layout, item.id, -1),
    },
    {
      id: 'wider',
      label: t('home.wider'),
      off: item.cols >= GRID_COLUMNS,
      next: resizeCols(layout, item.id, 1),
    },
    {
      id: 'shorter',
      label: t('home.shorter'),
      off: item.rows <= 1,
      next: resizeRows(layout, item.id, -1),
    },
    {
      id: 'taller',
      label: t('home.taller'),
      off: item.rows >= MAX_ROWS,
      next: resizeRows(layout, item.id, 1),
    },
    { id: 'remove', label: t('home.remove'), off: false, next: removeItem(layout, item.id) },
  ] as const;

  return (
    <Panel
      as="article"
      className={styles.card}
      // eslint-disable-next-line no-restricted-syntax -- runtime grid spans passed as custom properties
      style={{ '--cols': item.cols, '--rows': item.rows } as CSSProperties}
      data-testid={`widget-${widget.id}`}
      data-cols={item.cols}
      data-rows={item.rows}
    >
      <PanelHeader simple>
        <PanelTitle>{title}</PanelTitle>
        {customising ? (
          <div className={styles.controls}>
            {controls.map(({ id, label, off, next }) => (
              <Button
                key={id}
                size="sm"
                aria-label={`${label}: ${title}`}
                title={label}
                disabled={off}
                onClick={() => onChange(next)}
                data-testid={`widget-${id}-${widget.id}`}
              >
                {GLYPH[id]}
              </Button>
            ))}
          </div>
        ) : (
          <Button
            size="sm"
            variant="link"
            onClick={() => void navigate(widget.link)}
            data-testid={`widget-open-${widget.id}`}
          >
            {t('home.open')}
          </Button>
        )}
      </PanelHeader>
      <div className={styles.body}>
        <RemoteSlot item={widget} />
      </div>
    </Panel>
  );
}
