import { lazy } from 'react';

// One chunk per top-level view: a feature lib is downloaded only when its route is first opened.
export const ProjectSelection = lazy(() =>
  import('@openjcockpit/project').then((m) => ({ default: m.ProjectSelection })),
);
export const ProjectSettings = lazy(() =>
  import('@openjcockpit/project').then((m) => ({ default: m.ProjectSettings })),
);
export const SkillsHubDashboard = lazy(() =>
  import('@openjcockpit/skills-hub').then((m) => ({ default: m.SkillsHubDashboard })),
);
export const SkillsHubSettings = lazy(() =>
  import('@openjcockpit/skills-hub').then((m) => ({ default: m.SkillsHubSettings })),
);
export const SpecFilesDashboard = lazy(() =>
  import('@openjcockpit/spec-files').then((m) => ({ default: m.SpecFilesDashboard })),
);
export const SpecQueueDashboard = lazy(() =>
  import('@openjcockpit/spec-queue').then((m) => ({ default: m.SpecQueueDashboard })),
);
export const AddToQueueDialog = lazy(() =>
  import('@openjcockpit/spec-queue').then((m) => ({ default: m.AddToQueueDialog })),
);
export const WorkflowDashboard = lazy(() =>
  import('@openjcockpit/workflow').then((m) => ({ default: m.WorkflowDashboard })),
);
export const WorkflowPromptDialog = lazy(() =>
  import('@openjcockpit/workflow').then((m) => ({ default: m.WorkflowPromptDialog })),
);
