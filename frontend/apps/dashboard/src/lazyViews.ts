import { lazy } from 'react';

// One chunk per top-level view: a feature lib is downloaded only when its route is first opened.
export const ProjectSelection = lazy(() =>
  import('@openjcockpit/project').then((m) => ({ default: m.ProjectSelection })),
);
export const ProjectSettings = lazy(() =>
  import('@openjcockpit/project').then((m) => ({ default: m.ProjectSettings })),
);
export const WorkflowPromptDialog = lazy(() =>
  import('@openjcockpit/workflow').then((m) => ({ default: m.WorkflowPromptDialog })),
);
