import { useState } from 'react';
import { useNavigate } from 'react-router';
import {
  LOCALE_NAMES,
  type Locale,
  SUPPORTED_LOCALES,
  useLocale,
  useTranslation,
} from '@openjcockpit/i18n';
import type { Project } from '@openjcockpit/api';
import styles from './SiteHeader.module.scss';
import { BrandLogo, Select, cx } from '@openjcockpit/ui';
import { SidebarIcon, type SidebarIconName } from './SidebarIcon';
import { ROUTES } from '../../routes';

export type SiteSection = 'platform' | 'workflow' | 'specFiles' | 'specQueue' | 'skillsHub';

const NAV_ITEMS: { section: SiteSection; path: string; testId: string; icon: SidebarIconName }[] = [
  { section: 'platform', path: ROUTES.dashboard, testId: 'nav-platform', icon: 'platform' },
  { section: 'workflow', path: ROUTES.workflow, testId: 'nav-workflow', icon: 'workflow' },
  { section: 'specFiles', path: ROUTES.specFiles, testId: 'nav-spec-files', icon: 'specFiles' },
  { section: 'specQueue', path: ROUTES.specQueue, testId: 'nav-spec-queue', icon: 'specQueue' },
  { section: 'skillsHub', path: ROUTES.skillsHub, testId: 'nav-skills-hub', icon: 'skillsHub' },
];

const [HOME_ITEM, ...WORK_ITEMS] = NAV_ITEMS as [(typeof NAV_ITEMS)[number], ...typeof NAV_ITEMS];

const COLLAPSED_KEY = 'openjcockpit.sidebarCollapsed';

function readCollapsed() {
  try {
    return window.localStorage.getItem(COLLAPSED_KEY) === '1';
  } catch {
    return false;
  }
}

function storeCollapsed(collapsed: boolean) {
  try {
    window.localStorage.setItem(COLLAPSED_KEY, collapsed ? '1' : '0');
  } catch {
    // The preference only lasts for this page view.
  }
}

interface Props {
  /** The menu item shown as selected: the section of the page being viewed. */
  active: SiteSection;
  projects: Project[];
  selectedProject: Project | null;
  onProjectChange: (projectId: string) => void;
  onLogout: () => void;
  user?: { firstName: string; username: string } | null;
}

/** The sidebar menu shared by the platform overview and the feature pages it links to. */
export function SiteHeader({
  active,
  projects,
  selectedProject,
  onProjectChange,
  onLogout,
  user,
}: Props) {
  const { t } = useTranslation('dashboard');
  const { locale, setLocale } = useLocale();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(readCollapsed);

  function toggleCollapsed() {
    storeCollapsed(!collapsed);
    setCollapsed(!collapsed);
  }

  function navItem(item: (typeof NAV_ITEMS)[number]) {
    const label = t(`siteHeader.nav.${item.section}`);
    return (
      <button
        key={item.section}
        type="button"
        className={cx(styles.item, item.section === active && styles.active)}
        aria-current={item.section === active ? 'page' : undefined}
        aria-label={label}
        title={collapsed ? label : undefined}
        onClick={() => void navigate(item.path)}
        data-testid={item.testId}
      >
        <SidebarIcon name={item.icon} />
        <span className={styles.label}>{label}</span>
      </button>
    );
  }

  const settingsLabel = t('siteHeader.projectManagement');
  const hubLabel = t('siteHeader.nav.hub');
  const collapseLabel = t(collapsed ? 'siteHeader.expand' : 'siteHeader.collapse');
  const displayName = user ? user.firstName || user.username : null;

  return (
    <aside
      className={cx(styles.sidebar, collapsed && styles.collapsed)}
      data-app-sidebar={collapsed ? 'collapsed' : 'expanded'}
    >
      <button
        type="button"
        className={styles.brand}
        onClick={() => void navigate(ROUTES.landing)}
        title={t('siteHeader.backToHub')}
      >
        <BrandLogo />
      </button>

      {!collapsed && (
        <div className={styles.selectors}>
          {projects.length > 0 && (
            <Select
              block
              label={t('siteHeader.activeProject')}
              value={selectedProject?.id ?? ''}
              options={projects.map((p) => ({ value: p.id, label: p.name }))}
              onChange={onProjectChange}
            />
          )}
          <Select
            block
            label={t('siteHeader.language')}
            value={locale}
            options={SUPPORTED_LOCALES.map((code) => ({
              value: code,
              label: LOCALE_NAMES[code],
              lang: code,
            }))}
            // A failed locale download keeps the current language; the select stays on it.
            onChange={(code) => void setLocale(code as Locale).catch(() => undefined)}
            data-testid="locale-select"
          />
        </div>
      )}

      <nav className={styles.mainNav} aria-label={t('siteHeader.primaryNavigation')}>
        {navItem(HOME_ITEM)}
        <p className={styles.group}>{t('siteHeader.groups.work')}</p>
        {WORK_ITEMS.map(navItem)}
        <p className={styles.group}>{t('siteHeader.groups.account')}</p>
        <button
          type="button"
          className={styles.item}
          aria-label={settingsLabel}
          title={settingsLabel}
          onClick={() => void navigate(ROUTES.settings)}
        >
          <SidebarIcon name="settings" />
          <span className={styles.label}>{settingsLabel}</span>
        </button>
        <button
          type="button"
          className={styles.item}
          aria-label={hubLabel}
          title={t('siteHeader.backToHub')}
          onClick={() => void navigate(ROUTES.landing)}
          data-testid="nav-hub"
        >
          <SidebarIcon name="hub" />
          <span className={styles.label}>{hubLabel}</span>
        </button>
      </nav>

      <div className={styles.footer}>
        <button
          type="button"
          className={styles.item}
          aria-label={collapseLabel}
          aria-pressed={collapsed}
          title={collapsed ? collapseLabel : undefined}
          onClick={toggleCollapsed}
          data-testid="nav-collapse"
        >
          <SidebarIcon name="collapse" />
          <span className={styles.label}>{collapseLabel}</span>
        </button>
        <div className={styles.userCard}>
          {user && (
            <span className={styles.avatar} title={user.firstName} aria-hidden="true">
              {(displayName ?? '').charAt(0).toUpperCase()}
            </span>
          )}
          {user && (
            <span className={styles.userText}>
              <span className={styles.userName}>{displayName}</span>
              <span className={styles.userHandle}>{user.username}</span>
            </span>
          )}
          <button
            type="button"
            className={styles.logout}
            onClick={onLogout}
            title={t('siteHeader.logOut')}
            aria-label={t('siteHeader.logout')}
            data-testid="nav-logout"
          >
            <SidebarIcon name="logout" />
          </button>
        </div>
      </div>
    </aside>
  );
}
