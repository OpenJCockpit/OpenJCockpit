import { apiResources } from '@openjcockpit/api';
import { mergeResources } from '@openjcockpit/i18n';
import { specQueueResources } from '@openjcockpit/spec-queue';

/** Every lib's translations, registered once with the app's i18n instance. */
export const appResources = mergeResources(apiResources, specQueueResources);
