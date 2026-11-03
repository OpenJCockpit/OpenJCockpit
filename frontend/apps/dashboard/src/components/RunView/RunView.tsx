import { useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { useTranslation } from '@openjcockpit/i18n';
import { AppShell, Button, Panel, Icon } from '@openjcockpit/ui';
import { WorkflowExecution } from '@openjcockpit/workflow';
import { useProjectWorkflows } from '@openjcockpit/workflow/queries';
import type { Project } from '@openjcockpit/api';
import { ROUTES } from '../../routes';
import { SiteHeader } from '../SiteHeader/SiteHeader';
import styles from './RunView.module.scss';

interface Props {
  projects: Project[];
  project: Project;
  onProjectChange: (projectId: string) => void;
  onLogout: () => void;
  user?: { firstName: string; username: string } | null;
}

/** A started workflow run on its own screen, reached from the dashboard's start button. */
export function RunView({ projects, project, onProjectChange, onLogout, user }: Props) {
  const { t } = useTranslation('dashboard');
  const navigate = useNavigate();
  const { runId = '' } = useParams();
  const [params] = useSearchParams();
  const workflows = useProjectWorkflows(project.name);
  const workflow = workflows.find((w) => w.id === params.get('workflow')) ?? null;
  const [ended, setEnded] = useState(false);

  const backToDashboard = () => void navigate(ROUTES.home);

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
      <main className={styles.runScreen}>
        <div className={styles.runActions}>
          <Button variant="link" onClick={backToDashboard}>
            <Icon name="arrow-left" /> {t('flow.backToDashboard')}
          </Button>
          {ended && (
            <Button onClick={backToDashboard}>
              <Icon name="reset" /> {t('flow.startNewWorkflow')}
            </Button>
          )}
        </div>
        {workflow ? (
          <WorkflowExecution
            workflow={workflow}
            runId={runId}
            onRunEnded={() => setEnded(true)}
            showHistory={false}
          />
        ) : (
          <Panel className={styles.runMissing}>
            <p role="alert">{t('flow.runNotFound')}</p>
          </Panel>
        )}
      </main>
    </AppShell>
  );
}
