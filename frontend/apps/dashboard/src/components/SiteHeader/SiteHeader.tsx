import { useNavigate } from 'react-router';
import type { Project } from '@openjcockpit/api';
import styles from './SiteHeader.module.scss';
import { Button, BrandLogo, dropdownClass } from '@openjcockpit/ui';
import { ROUTES } from '../../routes';

export type SiteSection = 'platform' | 'workflow' | 'specFiles' | 'specQueue' | 'skillsHub';

const NAV_ITEMS: { section: SiteSection; label: string; path: string; testId: string }[] = [
  { section: 'platform', label: 'Platform', path: ROUTES.dashboard, testId: 'nav-platform' },
  {
    section: 'workflow',
    label: 'Workflow Design & Execution',
    path: ROUTES.workflow,
    testId: 'nav-workflow',
  },
  { section: 'specFiles', label: 'Spec Files', path: ROUTES.specFiles, testId: 'nav-spec-files' },
  { section: 'specQueue', label: 'Spec Queue', path: ROUTES.specQueue, testId: 'nav-spec-queue' },
  { section: 'skillsHub', label: 'Skills Hub', path: ROUTES.skillsHub, testId: 'nav-skills-hub' },
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
  const navigate = useNavigate();

  return (
    <header className={styles.siteHeader}>
      <button
        type="button"
        className={styles.brand}
        onClick={() => void navigate(ROUTES.landing)}
        title="Back to hub"
      >
        <BrandLogo />
      </button>

      <nav className={styles.mainNav} aria-label="Primary navigation">
        {NAV_ITEMS.map((item) => (
          <button
            key={item.section}
            type="button"
            className={item.section === active ? styles.active : undefined}
            aria-current={item.section === active ? 'page' : undefined}
            onClick={() => void navigate(item.path)}
            data-testid={item.testId}
          >
            {item.label}
          </button>
        ))}
      </nav>

      <div className={styles.headerActions}>
        {projects.length > 0 && (
          <select
            className={dropdownClass}
            value={selectedProject?.id ?? ''}
            onChange={(e) => onProjectChange(e.target.value)}
            title="Active project"
          >
            {projects.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        )}
        <Button
          variant="secondary"
          onClick={() => void navigate(ROUTES.settings)}
          title="Project management"
        >
          ⚙
        </Button>
        <Button
          variant="secondary"
          onClick={() => void navigate(ROUTES.landing)}
          title="Back to hub"
          data-testid="nav-hub"
        >
          ← Hub
        </Button>
        <Button variant="secondary" onClick={onLogout} title="Log out" data-testid="nav-logout">
          Logout
        </Button>
        <span className={styles.notification}>3</span>
        <span className={styles.avatar}>D</span>
      </div>
    </header>
  );
}
