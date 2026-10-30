import { apiResources } from '@openjcockpit/api';
import { mergeResources, type LocaleLoader } from '@openjcockpit/i18n';
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

// One lazy chunk per lib and locale; English is bundled above, everything else loads on demand.
const localeBundles = import.meta.glob<{ default: Record<string, unknown> }>([
  '../../../libs/*/src/locales/*.json',
  './locales/*.json',
  '!**/en.json',
]);

/** Loads and merges every lib's translations for one non-English locale. */
export const loadAppLocale: LocaleLoader = async (locale) => {
  const modules = await Promise.all(
    Object.entries(localeBundles)
      .filter(([path]) => path.endsWith(`/${locale}.json`))
      .map(([, load]) => load()),
  );
  return { [locale]: Object.assign({}, ...modules.map((module) => module.default)) };
};
