import './ProjectSelection.scss';
import type { GitStatus, Project } from '../../types';

interface Props {
  projects: Project[];
  onSelect: (project: Project) => void;
  onSettings: () => void;
}

function GitStatusIndicator({ status }: { status: GitStatus }) {
  if (status === 'ACCESSIBLE') {
    return (
      <span className="git-status-ok" aria-label="Git repository reachable">
        {' '}
        ✓
      </span>
    );
  }
  if (status === 'NOT_ACCESSIBLE' || status === 'CHECK_FAILED') {
    return (
      <span className="git-status-error" aria-label="Git repository not reachable">
        {' '}
        ✗
      </span>
    );
  }
  return (
    <span className="git-status-unknown" aria-label="Git status unknown">
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
    <div className="project-selection-shell">
      <div className="neural-bg" aria-hidden="true" />
      <div className="orb orb--left" aria-hidden="true" />
      <div className="orb orb--right" aria-hidden="true" />

      <div className="project-selection-content">
        <div className="project-selection-header">
          <img src="/openjcockpit-logo.png" alt="OpenJCockpit" className="brand-logo" />
          <h1 className="project-selection-title">Select a project</h1>
          <p className="project-selection-subtitle">Choose the project you want to work on</p>
        </div>

        {displayProjects.length === 0 ? (
          <div className="project-empty">
            <p data-testid="project-selection-empty">No active projects available.</p>
            <button className="back-button" onClick={onSettings}>
              Manage projects →
            </button>
          </div>
        ) : (
          <div className="project-grid">
            {displayProjects.map((project) => {
              const selectable = canSelect(project);
              const showNotAccessible =
                project.gitStatus === 'NOT_ACCESSIBLE' || project.gitStatus === 'CHECK_FAILED';
              return (
                <button
                  key={project.id}
                  className={`project-card${!selectable ? ' project-card--disabled' : ''}`}
                  onClick={() => selectable && onSelect(project)}
                  disabled={!selectable}
                  onKeyDown={(e) => e.key === 'Enter' && selectable && onSelect(project)}
                  data-testid="project-card"
                >
                  <div className="project-card-icon">◇</div>
                  <div className="project-card-body">
                    <h2 className="project-card-name">
                      {project.name}
                      <GitStatusIndicator status={project.gitStatus} />
                    </h2>
                    {project.description && (
                      <p className="project-card-desc">{project.description}</p>
                    )}
                    <div className="project-card-meta">
                      {project.environment && <span>{project.environment}</span>}
                      {project.owner && <span>Owner: {project.owner}</span>}
                      {project.defaultBranch && <span>Branch: {project.defaultBranch}</span>}
                    </div>
                    {showNotAccessible && project.newProject === 0 && (
                      <p className="project-card-notice">
                        Repository not reachable. Update the project via ⚙ Manage projects.
                      </p>
                    )}
                  </div>
                  <span className="project-card-arrow" aria-hidden="true">
                    →
                  </span>
                </button>
              );
            })}
          </div>
        )}

        <div className="project-selection-footer">
          <button className="back-button" onClick={onSettings}>
            ⚙ Manage projects
          </button>
        </div>
      </div>
    </div>
  );
}
