import type { Project } from '../../types';
import styles from './SiteHeader.module.scss';
import { Button } from '../../ui/Button/Button';

interface Props {
  projects: Project[];
  selectedProject: Project | null;
  onProjectChange: (projectId: string) => void;
  onWorkflow: () => void;
  onSpecFiles: () => void;
  onSkillsHub: () => void;
  onSettings: () => void;
}

export function SiteHeader({
  projects,
  selectedProject,
  onProjectChange,
  onWorkflow,
  onSpecFiles,
  onSkillsHub,
  onSettings,
}: Props) {
  return (
    <header className="site-header">
      <div className="brand">
        <img src="/openjcockpit-logo.png" alt="OpenJCockpit" className="brand-logo" />
      </div>

      <nav className={styles.mainNav} aria-label="Primary navigation">
        <a href="#approach">Approach</a>
        <a href="#platform" className={styles.active}>
          Platform
        </a>
        <a href="#solutions">Solutions</a>
      </nav>

      <div className="header-actions">
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
          onClick={onSkillsHub}
          title="Skills Hub"
          data-testid="nav-skills-hub"
        >
          🧩 Skills Hub
        </Button>
        {projects.length > 0 && (
          <select
            className="project-dropdown"
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
        <Button
          variant="secondary"
          onClick={() => {
            window.location.href = 'http://localhost:3000';
          }}
          title="Back to portal"
          data-testid="nav-portal"
        >
          ← Portal
        </Button>
        <span className={styles.notification}>3</span>
        <span className={styles.avatar}>D</span>
      </div>
    </header>
  );
}
