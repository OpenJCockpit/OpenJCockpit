import { useMemo, useState } from 'react';
import { useTranslation } from '@openjcockpit/i18n';
import { useAppRegistry, useHubHeader, useHubProject, useHubSession } from '@openjcockpit/hub-contract';
import { AppShell, Button, Eyebrow, cx, pageClass, pageHeaderClass } from '@openjcockpit/ui';
import {
  defaultLayout,
  loadStoredLayout,
  resolveLayout,
  storeLayout,
  type LayoutItem,
} from './homeLayout';
import { CustomerHero } from '../components/CustomerHero/CustomerHero';
import { HomeCustomiser } from './HomeCustomiser';
import { WidgetCard } from './WidgetCard';
import styles from './Home.module.scss';
import gridStyles from './HomeGrid.module.scss';

export function Home() {
  const { t } = useTranslation('dashboard');
  const session = useHubSession();
  const { visibleWidgets } = useAppRegistry();
  const header = useHubHeader();
  const { selected } = useHubProject();
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
  function update(next: LayoutItem[] | null) {
    setSaved(next);
    storeLayout(userKey, next);
  }

  function defaults() {
    return defaultLayout(visibleWidgets, (permission) => session.has(permission));
  }

  const greeting = session.user?.firstName || session.user?.username;

  return (
    <AppShell>
      {header}
      <main className={cx(pageClass, styles.wide)} data-testid="home">
        <div className={cx(pageHeaderClass, styles.heading)}>
          {selected ? (
            <CustomerHero selectedProject={selected} />
          ) : (
            <div>
              <Eyebrow>{t('home.eyebrow')}</Eyebrow>
              <h1>{greeting ? t('home.greeting', { name: greeting }) : t('home.title')}</h1>
            </div>
          )}
          <div className={styles.toolbar}>
            <Button size="sm" onClick={() => setCustomising(true)} data-testid="home-customise">
              {t('home.customise')}
            </Button>
          </div>
        </div>

        {customising ? null : layout.length === 0 ? (
          <p className={styles.empty} data-testid="home-empty">
            {t('home.empty')}
          </p>
        ) : (
          <div className={gridStyles.grid} data-testid="home-grid">
            {layout.map((item) => (
              // The layout is resolved against `visibleWidgets`, so every item has its widget.
              <WidgetCard key={item.id} widget={widgetsById.get(item.id)!} item={item} />
            ))}
          </div>
        )}
      </main>

      {customising && (
        <HomeCustomiser
          initial={layout}
          defaultItems={defaults()}
          widgets={visibleWidgets.filter((widget) => widget.available !== false)}
          onDone={(next, isDefault) => {
            update(isDefault ? null : next);
            setCustomising(false);
          }}
          onCancel={() => setCustomising(false)}
        />
      )}
    </AppShell>
  );
}
