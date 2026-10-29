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
import { Button, BrandLogo, dropdownClass } from '@openjcockpit/ui';
import { ROUTES } from '../../routes';

export type SiteSection = 'platform' | 'workflow' | 'specFiles' | 'specQueue' | 'skillsHub';

const NAV_ITEMS: { section: SiteSection; path: string; testId: string }[] = [
  { section: 'platform', path: ROUTES.dashboard, testId: 'nav-platform' },
  {
    section: 'workflow',
    path: ROUTES.workflow,
    testId: 'nav-workflow',
  },
  { section: 'specFiles', path: ROUTES.specFiles, testId: 'nav-spec-files' },
  { section: 'specQueue', path: ROUTES.specQueue, testId: 'nav-spec-queue' },
  { section: 'skillsHub', path: ROUTES.skillsHub, testId: 'nav-skills-hub' },
];

interface Props {
  /** The menu item shown as selected: the section of the page being viewed. */
  active: SiteSection;
  projects: Project[];
  selectedProject: Project | null;
  onProjectChange: (projectId: string) => void;
  onLogout: () => void;
}

/** The top menu shared by the platform overview and the feature pages it links to. */
export function SiteHeader({
  active,
  projects,
  selectedProject,
  onProjectChange,
  onLogout,
}: Props) {
  const { t } = useTranslation('dashboard');
  const { locale, setLocale } = useLocale();
  const navigate = useNavigate();

  return (
    <header className={styles.siteHeader}>
      <button
        type="button"
        className={styles.brand}
        onClick={() => void navigate(ROUTES.landing)}
        title={t('siteHeader.backToHub')}
      >
        <BrandLogo />
      </button>

      <nav className={styles.mainNav} aria-label={t('siteHeader.primaryNavigation')}>
        {NAV_ITEMS.map((item) => (
          <button
            key={item.section}
            type="button"
            className={item.section === active ? styles.active : undefined}
            aria-current={item.section === active ? 'page' : undefined}
            onClick={() => void navigate(item.path)}
            data-testid={item.testId}
          >
            {t(`siteHeader.nav.${item.section}`)}
          </button>
        ))}
      </nav>

      <div className={styles.headerActions}>
        {projects.length > 0 && (
          <select
            className={dropdownClass}
            value={selectedProject?.id ?? ''}
            onChange={(e) => onProjectChange(e.target.value)}
            title={t('siteHeader.activeProject')}
          >
            {projects.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        )}
        <select
          className={dropdownClass}
          value={locale}
          // A failed locale download keeps the current language; the select stays on it.
          onChange={(e) => void setLocale(e.target.value as Locale).catch(() => undefined)}
          aria-label={t('siteHeader.language')}
          data-testid="locale-select"
        >
          {SUPPORTED_LOCALES.map((code) => (
            <option key={code} value={code} lang={code}>
              {LOCALE_NAMES[code]}
            </option>
          ))}
        </select>
        <Button
          variant="secondary"
          onClick={() => void navigate(ROUTES.settings)}
          title={t('siteHeader.projectManagement')}
        >
          ⚙
        </Button>
        <Button
          variant="secondary"
          onClick={() => void navigate(ROUTES.landing)}
          title={t('siteHeader.backToHub')}
          data-testid="nav-hub"
        >
          {t('siteHeader.hub')}
        </Button>
        <Button
          variant="secondary"
          onClick={onLogout}
          title={t('siteHeader.logOut')}
          data-testid="nav-logout"
        >
          {t('siteHeader.logout')}
        </Button>
        <span className={styles.notification}>3</span>
        <span className={styles.avatar}>D</span>
      </div>
    </header>
  );
}
