import type { Project } from '../../types';
import { GitStatusBadge } from '../GitStatusBadge/GitStatusBadge';
import { Actions, DataTable, DataTableRow } from '../../ui/DataTable/DataTable';
import { fieldHintClass } from '../../ui/Form/Form';

interface Props {
  projects: Project[];
  checkingId: string | null;
  onEdit: (project: Project) => void;
  onGitCheck: (projectId: string) => void;
  onEditCredentials: (projectId: string) => void;
  onDeleteCredentials: (projectId: string) => void;
  onToggleActive: (project: Project) => void;
}

export function ProjectList({
  projects,
  checkingId,
  onEdit,
  onGitCheck,
  onEditCredentials,
  onDeleteCredentials,
  onToggleActive,
}: Props) {
  return (
    <DataTable>
      <thead>
        <tr>
          <th>Name</th>
          <th>Git status</th>
          <th>Credentials</th>
          <th>Status</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        {projects.map((project) => (
          <DataTableRow key={project.id} inactive={project.active === 0}>
            <td>
              <strong>{project.name}</strong>
              {project.description && <span className={fieldHintClass}>{project.description}</span>}
              {project.gitUrl && (
                <span className={fieldHintClass} style={{ color: 'var(--faint)', fontSize: 11 }}>
                  {project.gitUrl}
                </span>
              )}
            </td>
            <td>
              <GitStatusBadge status={project.gitStatus} />
              {project.gitStatusMessage && (
                <span className={fieldHintClass} style={{ fontSize: 11 }}>
                  {project.gitStatusMessage}
                </span>
              )}
            </td>
            <td>
              {project.hasCredentials ? (
                <span style={{ color: 'var(--teal)', fontSize: 12 }}>Credentials set</span>
              ) : (
                <span style={{ color: 'var(--faint)', fontSize: 12 }}>None</span>
              )}
            </td>
            <td>
              <span
                className={`status-badge ${project.active === 1 ? 'status-badge--active' : 'status-badge--inactive'}`}
              >
                {project.active === 1 ? 'Active' : 'Inactive'}
              </span>
            </td>
            <Actions as="td">
              <button className="back-button" onClick={() => onEdit(project)}>
                Edit
              </button>
              <button
                className="back-button"
                onClick={() => onGitCheck(project.id)}
                disabled={checkingId === project.id}
              >
                {checkingId === project.id ? '⟳' : 'Git check'}
              </button>
              <button className="back-button" onClick={() => onEditCredentials(project.id)}>
                {project.hasCredentials ? 'Update credentials' : 'Add credentials'}
              </button>
              {project.hasCredentials && (
                <button
                  className="back-button"
                  style={{ color: 'var(--danger)' }}
                  onClick={() => onDeleteCredentials(project.id)}
                >
                  Delete credentials
                </button>
              )}
              <button
                className="back-button"
                onClick={() => onToggleActive(project)}
                style={{
                  color: project.active === 1 ? 'var(--danger)' : 'var(--cyan)',
                }}
              >
                {project.active === 1 ? 'Deactivate' : 'Activate'}
              </button>
            </Actions>
          </DataTableRow>
        ))}
      </tbody>
    </DataTable>
  );
}
