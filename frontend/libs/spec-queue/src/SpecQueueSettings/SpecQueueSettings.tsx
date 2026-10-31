import { useSelectedProject } from '@openjcockpit/hub-contract';
import { useTranslation } from '@openjcockpit/i18n';
import { StatusMessage } from '@openjcockpit/ui';
import { AutoMergeSettingToggle } from '../AutoMergeSettingToggle/AutoMergeSettingToggle';
import { messageOf, useSpecQueue } from '../SpecQueueDashboard/useSpecQueue';

/**
 * Content of the host's "Spec queue" settings section; the host owns the heading and only
 * mounts this with a project selected. It reads and writes the same cache entry as the queue
 * page, so the page reflects a change here without a reload.
 */
export function SpecQueueSettings() {
  const { t } = useTranslation('specQueue');
  const project = useSelectedProject();
  const { queue, loadError, applyAutoMergeAllowed } = useSpecQueue(project.id);

  if (!queue) {
    return loadError ? (
      <StatusMessage variant="error" role="alert">
        {t('dashboard.loadError', { error: messageOf(loadError, t('dashboard.loadFailed')) })}
      </StatusMessage>
    ) : (
      <StatusMessage variant="loading">{t('dashboard.loading')}</StatusMessage>
    );
  }

  return (
    <AutoMergeSettingToggle
      projectId={project.id}
      autoMergeAllowed={queue.autoMergeAllowed}
      onChanged={(settings) => void applyAutoMergeAllowed(settings.autoMergeAllowed)}
    />
  );
}
