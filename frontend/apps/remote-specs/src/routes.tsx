import type { ReactNode } from 'react';
import { Outlet, useNavigate, useParams, type RouteObject } from 'react-router';
import {
  AddToQueueDialog,
  SpecQueueDashboard,
  WithSpecQueueTranslations,
} from '@openjcockpit/spec-queue';
import { SpecFilesDashboard, WithSpecFilesTranslations } from '@openjcockpit/spec-files';
import { WithWorkflowTranslations, WorkflowDashboard } from '@openjcockpit/workflow';
import {
  DASHBOARD_PATH,
  RequireProject,
  useHubHeader,
  useSelectedProject,
} from '@openjcockpit/hub-contract';
import { workflowRunPath } from './routePaths';

/** Every page needs a project: without one the user is sent to project selection and back. */
function projectRoute(translations: (props: { children: ReactNode }) => ReactNode): {
  element: ReactNode;
} {
  const Translations = translations;
  return {
    element: (
      <RequireProject>
        <Translations>
          <Outlet />
        </Translations>
      </RequireProject>
    ),
  };
}

function useBack() {
  const navigate = useNavigate();
  return () => void navigate(DASHBOARD_PATH);
}

function SpecFilesPage() {
  const project = useSelectedProject();
  return (
    <SpecFilesDashboard
      project={project}
      onBack={useBack()}
      header={useHubHeader()}
      renderAddToQueue={({ project: target, fixedSpecFile, onAdded, onCancel }) => (
        <WithSpecQueueTranslations>
          <AddToQueueDialog
            project={target}
            fixedSpecFile={fixedSpecFile}
            onAdded={onAdded}
            onCancel={onCancel}
          />
        </WithSpecQueueTranslations>
      )}
    />
  );
}

function SpecQueuePage() {
  const project = useSelectedProject();
  const navigate = useNavigate();
  return (
    <SpecQueueDashboard
      project={project}
      onBack={useBack()}
      header={useHubHeader()}
      onOpenRun={(workflowId, runId) => void navigate(workflowRunPath(workflowId, runId))}
    />
  );
}

function WorkflowPage() {
  const project = useSelectedProject();
  const { workflowId, runId } = useParams();
  return (
    <WorkflowDashboard
      key={`${workflowId ?? ''}/${runId ?? ''}`}
      project={project}
      initialExecution={workflowId && runId ? { workflowId, runId } : undefined}
      onBack={useBack()}
      header={useHubHeader()}
    />
  );
}

/** Relative to each app route, which the host mounts as `<route>/*`. */
export const specFilesRoutes: RouteObject[] = [
  {
    ...projectRoute(WithSpecFilesTranslations),
    children: [{ index: true, element: <SpecFilesPage /> }],
  },
];

export const specQueueRoutes: RouteObject[] = [
  {
    ...projectRoute(WithSpecQueueTranslations),
    children: [{ index: true, element: <SpecQueuePage /> }],
  },
];

export const workflowRoutes: RouteObject[] = [
  {
    ...projectRoute(WithWorkflowTranslations),
    children: [
      { index: true, element: <WorkflowPage /> },
      { path: ':workflowId/runs/:runId', element: <WorkflowPage /> },
    ],
  },
];
