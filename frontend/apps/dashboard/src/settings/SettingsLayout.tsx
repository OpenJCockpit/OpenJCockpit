import { useMemo } from 'react';
import { NavLink, Navigate, Route, Routes, useParams } from 'react-router';
import { useTranslation } from '@openjcockpit/i18n';
import {
  FORBIDDEN_PATH,
  useAppRegistry,
  useHubHeader,
  type SettingsScreen,
  type SettingsSectionDefinition,
} from '@openjcockpit/hub-contract';
import {
  AppShell,
  Eyebrow,
  Panel,
  StatusMessage,
  cx,
  pageClass,
  pageHeaderClass,
} from '@openjcockpit/ui';
import { NotFound } from '../routeViews/NotFound';
import { ROUTES } from '../routes';
import { SectionFrame } from './SectionFrame';
import styles from './SettingsLayout.module.scss';

const DEFAULT_GROUP = 'settings.groups.other';

export interface SettingsGroup {
  key: string;
  sections: SettingsSectionDefinition[];
}

/** Groups by `groupKey`, ordered by the lowest `order` of their sections; sections are pre-sorted. */
export function groupSections(sections: readonly SettingsSectionDefinition[]): SettingsGroup[] {
  const groups = new Map<string, SettingsSectionDefinition[]>();
  for (const section of sections) {
    const key = section.groupKey ?? DEFAULT_GROUP;
    groups.set(key, [...(groups.get(key) ?? []), section]);
  }
  return [...groups].map(([key, list]) => ({ key, sections: list }));
}

/** Where each screen is mounted. */
export const SCREEN_BASE: Record<SettingsScreen, string> = {
  settings: ROUTES.settings,
  project: ROUTES.projectSettings,
};

export const settingsPath = (section: Pick<SettingsSectionDefinition, 'id' | 'screen'>) =>
  `${SCREEN_BASE[section.screen ?? 'settings']}/${section.id}`;

/** The sections one screen shows: `visibleSettings` already holds only what the session may open. */
export function useScreenSections(screen: SettingsScreen) {
  const { visibleSettings } = useAppRegistry();
  return useMemo(
    () => visibleSettings.filter((section) => (section.screen ?? 'settings') === screen),
    [visibleSettings, screen],
  );
}

/** Text of a key a remote contributed: not part of the host's typed catalog. */
function useRemoteText() {
  const { t } = useTranslation('dashboard');
  return (key: string) => String(t(key as never));
}

function SettingsNav({
  sections,
  screen,
}: {
  sections: readonly SettingsSectionDefinition[];
  screen: SettingsScreen;
}) {
  const { t } = useTranslation('dashboard');
  const text = useRemoteText();
  const groups = useMemo(() => groupSections(sections), [sections]);
  return (
    <Panel
      as="nav"
      className={styles.nav}
      aria-label={t(screen === 'project' ? 'settings.projectNavigation' : 'settings.navigation')}
      data-testid="settings-nav"
    >
      {groups.map((group) => (
        <div key={group.key} className={styles.group}>
          <p className={styles.groupTitle}>{text(group.key)}</p>
          {group.sections.map((section) => (
            <NavLink
              key={section.id}
              to={settingsPath(section)}
              className={({ isActive }) => cx(styles.link, isActive && styles.active)}
              data-testid={`settings-nav-${section.id}`}
            >
              {text(section.titleKey)}
            </NavLink>
          ))}
        </div>
      ))}
    </Panel>
  );
}

/** `/settings/*` or `/project-settings/*`: the screen's sub-navigation next to the active section. */
export function SettingsLayout({ screen = 'settings' }: { screen?: SettingsScreen }) {
  const { t } = useTranslation('dashboard');
  const header = useHubHeader();
  const { allApps } = useAppRegistry();
  const visibleSettings = useScreenSections(screen);
  const first = visibleSettings[0];

  const known = useMemo(
    () =>
      new Set(
        allApps
          .flatMap((app) => app.settings ?? [])
          .filter((section) => (section.screen ?? 'settings') === screen)
          .map((section) => section.id),
      ),
    [allApps, screen],
  );

  return (
    <AppShell>
      {header}
      <main
        className={pageClass}
        data-testid={screen === 'project' ? 'project-settings' : 'settings'}
      >
        <div className={pageHeaderClass}>
          <Eyebrow>
            {t(screen === 'project' ? 'settings.projectEyebrow' : 'settings.eyebrow')}
          </Eyebrow>
        </div>
        {first ? (
          <div className={styles.layout}>
            <SettingsNav sections={visibleSettings} screen={screen} />
            <div className={styles.content}>
              <Routes>
                <Route index element={<Navigate to={settingsPath(first)} replace />} />
                {visibleSettings.map((section) => (
                  <Route
                    key={section.id}
                    path={section.id}
                    element={<SectionFrame section={section} />}
                  />
                ))}
                <Route path=":sectionId" element={<UnavailableSection known={known} />} />
              </Routes>
            </div>
          </div>
        ) : (
          <StatusMessage data-testid="settings-empty">{t('settings.empty')}</StatusMessage>
        )}
      </main>
    </AppShell>
  );
}

/** A section that is not visible: unknown ids are 404, known-but-not-permitted ones are 403. */
function UnavailableSection({ known }: { known: ReadonlySet<string> }) {
  const { sectionId = '' } = useParams();
  return known.has(sectionId) ? <Navigate to={FORBIDDEN_PATH} replace /> : <NotFound />;
}
