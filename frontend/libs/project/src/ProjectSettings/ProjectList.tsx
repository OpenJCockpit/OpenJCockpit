import type { Project } from '@openjcockpit/api';
import { useTranslation } from '@openjcockpit/i18n';
import { GitStatusBadge } from '../GitStatusBadge/GitStatusBadge';
import { Actions, DataTable, DataTableRow, fieldHintClass, Button, cx } from '@openjcockpit/ui';
import styles from './ProjectList.module.scss';

const BUSY_ICON = '⟳';

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
  const { t } = useTranslation('project');
  return (
    <DataTable>
      <thead>
        <tr>
          <th>{t('list.name')}</th>
          <th>{t('list.gitStatus')}</th>
          <th>{t('list.credentials')}</th>
          <th>{t('list.status')}</th>
          <th>{t('list.actions')}</th>
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
                <span className={styles.credentialsSet}>{t('list.credentialsSet')}</span>
              ) : (
                <span className={styles.credentialsNone}>{t('list.credentialsNone')}</span>
              )}
            </td>
            <td>
              <span
                className={cx(
                  styles.statusBadge,
                  project.active === 1 ? styles.statusBadgeActive : styles.statusBadgeInactive,
                )}
              >
                {project.active === 1 ? t('list.active') : t('list.inactive')}
              </span>
            </td>
            <Actions as="td">
              <Button variant="secondary" onClick={() => onEdit(project)}>
                {t('list.edit')}
              </Button>
              <Button
                variant="secondary"
                onClick={() => onGitCheck(project.id)}
                disabled={checkingId === project.id}
              >
                {checkingId === project.id ? BUSY_ICON : t('list.gitCheck')}
              </Button>
              <Button variant="secondary" onClick={() => onEditCredentials(project.id)}>
                {project.hasCredentials ? t('list.updateCredentials') : t('list.addCredentials')}
              </Button>
              {project.hasCredentials && (
                <Button variant="danger" onClick={() => onDeleteCredentials(project.id)}>
                  {t('list.deleteCredentials')}
                </Button>
              )}
              <Button variant="secondary" onClick={() => onToggleActive(project)}>
                {project.active === 1 ? t('list.deactivate') : t('list.activate')}
              </Button>
            </Actions>
          </DataTableRow>
        ))}
      </tbody>
    </DataTable>
  );
}
