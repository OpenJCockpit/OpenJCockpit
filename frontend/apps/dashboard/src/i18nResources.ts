import { apiResources } from '@openjcockpit/api';
import { mergeResources } from '@openjcockpit/i18n';
import { landingResources } from '@openjcockpit/landing-hub';
import { projectResources } from '@openjcockpit/project';
import { skillsHubResources } from '@openjcockpit/skills-hub';
import { specFilesResources } from '@openjcockpit/spec-files';
import { specQueueResources } from '@openjcockpit/spec-queue';
import { workflowResources } from '@openjcockpit/workflow';
import { dashboardResources } from './i18n';

/** Every lib's translations, registered once with the app's i18n instance. */
export const appResources = mergeResources(
  apiResources,
  specQueueResources,
  specFilesResources,
  skillsHubResources,
  projectResources,
  landingResources,
  dashboardResources,
  workflowResources,
);
