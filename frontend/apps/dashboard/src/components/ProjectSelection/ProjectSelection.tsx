import type { GitStatus, Project } from '../../types';
import styles from './ProjectSelection.module.scss';
import { Button, cx, Backdrop, BrandLogo } from '@openjcockpit/ui';
import gitStyles from '../GitStatusBadge/GitStatusBadge.module.scss';

interface Props {
  projects: Project[];
  onSelect: (project: Project) => void;
  onSettings: () => void;
}

function GitStatusIndicator({ status }: { status: GitStatus }) {
  if (status === 'ACCESSIBLE') {
    return (
      <span className={gitStyles.ok} aria-label="Git repository reachable">
        {' '}
        ✓
      </span>
    );
  }
  if (status === 'NOT_ACCESSIBLE' || status === 'CHECK_FAILED') {
    return (
      <span className={gitStyles.error} aria-label="Git repository not reachable">
        {' '}
        ✗
      </span>
    );
  }
  return (
    <span className={gitStyles.unknown} aria-label="Git status unknown">
      {' '}
      …
    </span>
  );
}

function canSelect(project: Project): boolean {
  return (
    project.active === 1 &&
    (project.gitStatus === 'ACCESSIBLE' ||
      project.newProject === 1 ||
      project.gitStatus === 'UNKNOWN')
  );
}

export function ProjectSelection({ projects, onSelect, onSettings }: Props) {
  const displayProjects = projects;

  return (
    <div className={styles.projectSelectionShell}>
      <Backdrop />

      <div className={styles.projectSelectionContent}>
        <div className={styles.projectSelectionHeader}>
          <BrandLogo className={styles.projectSelectionLogo} />
          <h1 className={styles.projectSelectionTitle}>Select a project</h1>
          <p className={styles.projectSelectionSubtitle}>Choose the project you want to work on</p>
        </div>

        {displayProjects.length === 0 ? (
          <div className={styles.projectEmpty}>
            <p data-testid="project-selection-empty">No active projects available.</p>
            <Button variant="secondary" onClick={onSettings}>
              Manage projects →
            </Button>
          </div>
        ) : (
          <div className={styles.projectGrid}>
            {displayProjects.map((project) => {
              const selectable = canSelect(project);
              const showNotAccessible =
                project.gitStatus === 'NOT_ACCESSIBLE' || project.gitStatus === 'CHECK_FAILED';
              return (
                <button
                  key={project.id}
                  className={cx(styles.projectCard, !selectable && styles.projectCardDisabled)}
                  onClick={() => selectable && onSelect(project)}
                  disabled={!selectable}
                  onKeyDown={(e) => e.key === 'Enter' && selectable && onSelect(project)}
                  data-testid="project-card"
                >
                  <div className={styles.projectCardIcon}>◇</div>
                  <div className={styles.projectCardBody}>
                    <h2 className={styles.projectCardName}>
                      {project.name}
                      <GitStatusIndicator status={project.gitStatus} />
                    </h2>
                    {project.description && (
                      <p className={styles.projectCardDesc}>{project.description}</p>
                    )}
                    <div className={styles.projectCardMeta}>
                      {project.environment && <span>{project.environment}</span>}
                      {project.owner && <span>Owner: {project.owner}</span>}
                      {project.defaultBranch && <span>Branch: {project.defaultBranch}</span>}
                    </div>
                    {showNotAccessible && project.newProject === 0 && (
                      <p className={styles.projectCardNotice}>
                        Repository not reachable. Update the project via ⚙ Manage projects.
                      </p>
                    )}
                  </div>
                  <span className={styles.projectCardArrow} aria-hidden="true">
                    →
                  </span>
                </button>
              );
            })}
          </div>
        )}

        <div className={styles.projectSelectionFooter}>
          <Button variant="secondary" onClick={onSettings}>
            ⚙ Manage projects
          </Button>
        </div>
      </div>
    </div>
  );
}
