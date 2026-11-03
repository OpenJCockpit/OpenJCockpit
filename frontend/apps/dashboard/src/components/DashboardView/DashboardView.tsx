import { useTranslation } from '@openjcockpit/i18n';
import { AppShell, Panel, StatusMessage } from '@openjcockpit/ui';
import type { Project } from '@openjcockpit/api';
import { CustomerHero } from '../CustomerHero/CustomerHero';
import { SiteHeader } from '../SiteHeader/SiteHeader';
import { SpecWorkspace } from '../SpecWorkspace/SpecWorkspace';
import { EvidencePanel } from '../WorkspacePanels/EvidencePanel';
import { QualityPanel } from '../WorkspacePanels/QualityPanel';
import { useWorkspaceQuery } from './useDashboardQueries';
import styles from './DashboardView.module.scss';

interface Props {
  projects: Project[];
  project: Project;
  onProjectChange: (projectId: string) => void;
  onLogout: () => void;
  user?: { firstName: string; username: string } | null;
}

/** The project dashboard. Mounted with `key={project.id}` so all local state resets per project. */
export function DashboardView({ projects, project, onProjectChange, onLogout, user }: Props) {
  const { t } = useTranslation('dashboard');
  const workspaceQuery = useWorkspaceQuery(project);

  const workspace = workspaceQuery.data;

  return (
    <AppShell>
      <SiteHeader
        active="platform"
        projects={projects}
        selectedProject={project}
        onProjectChange={onProjectChange}
        onLogout={onLogout}
        user={user}
      />

      <main className={styles.workspace}>
        {workspace ? (
          <CustomerHero selectedProject={project} workspace={workspace} />
        ) : (
          <StatusMessage
            variant={workspaceQuery.isError ? 'error' : 'loading'}
            data-testid="workspace-status"
          >
            {workspaceQuery.isError ? t('workspace.failed') : t('workspace.loading')}
          </StatusMessage>
        )}

        <section className={styles.dashboardGrid}>
          <Panel className={styles.specWorkspace}>
            <SpecWorkspace project={project} />
          </Panel>

          {workspace && (
            <>
              <QualityPanel controls={workspace.qualityControls} />
              <EvidencePanel events={workspace.evidenceEvents} />
            </>
          )}
        </section>
      </main>

    </AppShell>
  );
}
