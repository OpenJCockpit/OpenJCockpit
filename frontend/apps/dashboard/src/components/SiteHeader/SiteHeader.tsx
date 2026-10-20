import type { Project } from '@openjcockpit/api';
import styles from './SiteHeader.module.scss';
import { Button, BrandLogo, dropdownClass } from '@openjcockpit/ui';
import shell from '../AppShell/AppShell.module.scss';

interface Props {
  projects: Project[];
  selectedProject: Project | null;
  onProjectChange: (projectId: string) => void;
  onWorkflow: () => void;
  onSpecFiles: () => void;
  onSpecQueue: () => void;
  onSkillsHub: () => void;
  onSettings: () => void;
  onHub: () => void;
  onLogout: () => void;
}

export function SiteHeader({
  projects,
  selectedProject,
  onProjectChange,
  onWorkflow,
  onSpecFiles,
  onSpecQueue,
  onSkillsHub,
  onSettings,
  onHub,
  onLogout,
}: Props) {
  return (
    <header className={shell.siteHeader}>
      <button type="button" className={shell.brand} onClick={onHub} title="Back to hub">
        <BrandLogo />
      </button>

      <nav className={styles.mainNav} aria-label="Primary navigation">
        <a href="#approach">Approach</a>
        <a href="#platform" className={styles.active}>
          Platform
        </a>
        <a href="#solutions">Solutions</a>
      </nav>

      <div className={shell.headerActions}>
        <Button
          variant="workflow"
          onClick={onWorkflow}
          title="Workflow Design & Execution"
          data-testid="nav-workflow"
        >
          ⚡ Workflow Design &amp; Execution
        </Button>
        <Button
          variant="workflow"
          onClick={onSpecFiles}
          title="Spec Files"
          data-testid="nav-spec-files"
        >
          📄 Spec Files
        </Button>
        <Button
          variant="workflow"
          onClick={onSpecQueue}
          title="Spec Queue"
          data-testid="nav-spec-queue"
        >
          📋 Spec Queue
        </Button>
        <Button
          variant="workflow"
          onClick={onSkillsHub}
          title="Skills Hub"
          data-testid="nav-skills-hub"
        >
          🧩 Skills Hub
        </Button>
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
        <Button variant="secondary" onClick={onSettings} title="Project management">
          ⚙
        </Button>
        <Button variant="secondary" onClick={onHub} title="Back to hub" data-testid="nav-hub">
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
