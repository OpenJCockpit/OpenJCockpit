import type { Project } from '../../types';
import { GitStatusBadge } from '../GitStatusBadge/GitStatusBadge';
import { Actions, DataTable, DataTableRow } from '../../ui/DataTable/DataTable';
import { fieldHintClass } from '../../ui/Form/Form';
import { Button } from '../../ui/Button/Button';
import styles from './ProjectList.module.scss';
import { cx } from '../../ui/cx';

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
                <span className={cx(fieldHintClass, styles.gitUrl)}>{project.gitUrl}</span>
              )}
            </td>
            <td>
              <GitStatusBadge status={project.gitStatus} />
              {project.gitStatusMessage && (
                <span className={cx(fieldHintClass, styles.gitMessage)}>
                  {project.gitStatusMessage}
                </span>
              )}
            </td>
            <td>
              {project.hasCredentials ? (
                <span className={styles.credentialsSet}>Credentials set</span>
              ) : (
                <span className={styles.credentialsNone}>None</span>
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
              <Button variant="secondary" onClick={() => onEdit(project)}>
                Edit
              </Button>
              <Button
                variant="secondary"
                onClick={() => onGitCheck(project.id)}
                disabled={checkingId === project.id}
              >
                {checkingId === project.id ? '⟳' : 'Git check'}
              </Button>
              <Button variant="secondary" onClick={() => onEditCredentials(project.id)}>
                {project.hasCredentials ? 'Update credentials' : 'Add credentials'}
              </Button>
              {project.hasCredentials && (
                <Button
                  variant="secondary"
                  className={styles.danger}
                  onClick={() => onDeleteCredentials(project.id)}
                >
                  Delete credentials
                </Button>
              )}
              <Button
                variant="secondary"
                onClick={() => onToggleActive(project)}
                className={project.active === 1 ? styles.danger : styles.activate}
              >
                {project.active === 1 ? 'Deactivate' : 'Activate'}
              </Button>
            </Actions>
          </DataTableRow>
        ))}
      </tbody>
    </DataTable>
  );
}
