import { useRef, useState, type ReactNode } from 'react';
import type { Project, SpecQueueItem } from '@openjcockpit/api';
import { useFormatters, useTranslation } from '@openjcockpit/i18n';
import { AddToQueueDialog } from '../AddToQueueDialog/AddToQueueDialog';
import { ConfirmActionDialog } from '../ConfirmActionDialog/ConfirmActionDialog';
import { pollErrorText, queueStateText } from '../queueStatus';
import { SpecQueueItemRow } from '../SpecQueueItemRow/SpecQueueItemRow';
import {
  AppHeader,
  AppShell,
  Button,
  feedbackErrorClass,
  fieldHintClass,
  Eyebrow,
  Panel,
  StatusChip,
  cx,
  pageClass,
  pageHeaderClass,
} from '@openjcockpit/ui';
import styles from './SpecQueueDashboard.module.scss';
import { confirmationCopy, useQueueActions } from './useQueueActions';
import { useSpecQueue } from './useSpecQueue';

interface Props {
  project: Project | null;
  onBack: () => void;
  onOpenRun?: (workflowId: string, runId: string) => void;
  /** Opens the queue's section in the host's settings; without it the shortcut is not shown. */
  onSettings?: () => void;
  /** Replaces the default back-button header, e.g. with the app's shared top menu. */
  header?: ReactNode;
}

export function SpecQueueDashboard({ project, onBack, onOpenRun, onSettings, header }: Props) {
  const { t } = useTranslation('specQueue');
  const { formatDateTime } = useFormatters();
  const projectId = project?.id ?? null;
  const { queue, loadError, applyQueue, refresh } = useSpecQueue(projectId);
  const [announcement, setAnnouncement] = useState('');
  const [adding, setAdding] = useState(false);
  const queuedItems = queue?.items.filter((i) => i.status === 'QUEUED') ?? [];
  const listRef = useRef<HTMLOListElement>(null);
  const actions = useQueueActions({
    projectId,
    queue,
    queuedItems,
    listRef,
    applyQueue,
    refresh,
    announce: setAnnouncement,
  });
  const { busy, confirmation } = actions;

  const autoMergeAllowed = queue?.autoMergeAllowed ?? false;

  function renderRow(item: SpecQueueItem, queuedIndex?: number) {
    return (
      <SpecQueueItemRow
        key={item.id}
        item={item}
        queuedIndex={queuedIndex}
        queuedCount={queuedItems.length}
        autoMergeAllowed={autoMergeAllowed}
        busy={busy}
        onMove={actions.move}
        onAutoMergeChange={actions.setAutoMerge}
        onRemove={(it) => actions.openConfirmation('remove', it)}
        onCancelRun={(it) => actions.openConfirmation('cancel', it)}
        onSkip={(it) => actions.openConfirmation('skip', it)}
        onRetry={actions.retry}
        onOpenRun={onOpenRun}
      />
    );
  }

  const copy = confirmation ? confirmationCopy(confirmation, t) : null;

  return (
    <AppShell>
      {header ?? <AppHeader onBack={onBack} backTitle={t('backToDashboard')} />}

      <main className={cx(pageClass, styles.specQueue)}>
        <div className={cx(pageHeaderClass, styles.specQueueHeader)}>
          <div>
            <Eyebrow>{t('dashboard.eyebrow')}</Eyebrow>
            <h1>{project ? project.name : t('noProject')}</h1>
          </div>
          {project && queue && (
            <div className={styles.specQueueControls}>
              <span data-testid="queue-state">
                <StatusChip
                  label={t('dashboard.state', { state: queueStateText(queue.state, t) })}
                  kind={
                    queue.state === 'ACTIVE'
                      ? 'ok'
                      : queue.state === 'HALTED'
                        ? 'changes'
                        : 'waiting'
                  }
                />
              </span>
              {queue.state === 'ACTIVE' ? (
                <Button
                  type="button"
                  size="sm"
                  data-testid="queue-pause"
                  disabled={busy}
                  onClick={() => actions.pauseResume(true)}
                >
                  {t('dashboard.pause')}
                </Button>
              ) : (
                <Button
                  type="button"
                  size="sm"
                  data-testid="queue-resume"
                  disabled={busy}
                  onClick={() => actions.pauseResume(false)}
                >
                  {t('dashboard.resume')}
                </Button>
              )}
              {onSettings && (
                <Button type="button" size="sm" data-testid="queue-settings" onClick={onSettings}>
                  {t('dashboard.settings')}
                </Button>
              )}
              <Button
                type="button"
                size="sm"
                variant="primary"
                data-testid="queue-add"
                onClick={() => setAdding(true)}
              >
                {t('dashboard.add')}
              </Button>
            </div>
          )}
        </div>

        <div
          className={styles.specQueueVisuallyHidden}
          role="status"
          aria-live="polite"
          aria-atomic="true"
          data-testid="queue-announcer"
        >
          {announcement}
        </div>

        {!project && <p className={fieldHintClass}>{t('dashboard.selectProject')}</p>}
        {project && !queue && !loadError && (
          <p className={fieldHintClass} data-testid="queue-loading">
            {t('dashboard.loading')}
          </p>
        )}
        {project && !queue && loadError && (
          <p className={feedbackErrorClass} role="alert" data-testid="queue-load-error">
            {t('dashboard.loadError', { error: loadError })}
          </p>
        )}

        {project && queue && (
          <>
            {queue.state === 'PAUSED' && (
              <p className={styles.specQueueBanner} role="status" data-testid="queue-banner-paused">
                {t('dashboard.bannerPaused')}
              </p>
            )}
            {queue.state === 'HALTED' && (
              <p
                className={cx(styles.specQueueBanner, styles.specQueueBannerWarn)}
                role="status"
                data-testid="queue-banner-halted"
              >
                {t('dashboard.bannerHalted')}
              </p>
            )}
            {!queue.runner.enabled && (
              <p
                className={cx(styles.specQueueBanner, styles.specQueueBannerWarn)}
                role="status"
                data-testid="queue-banner-runner-disabled"
              >
                {t('dashboard.bannerRunnerDisabled')}
              </p>
            )}
            {queue.runner.enabled && !queue.runner.configured && (
              <p
                className={cx(styles.specQueueBanner, styles.specQueueBannerWarn)}
                role="status"
                data-testid="queue-banner-runner-not-configured"
              >
                {t('dashboard.bannerRunnerNotConfigured')}
              </p>
            )}
            {queue.lastPollError && (
              <p
                className={cx(styles.specQueueBanner, styles.specQueueBannerWarn)}
                role="status"
                data-testid="queue-banner-poll-error"
              >
                {t('dashboard.bannerPollError', {
                  reason: pollErrorText(queue.lastPollError.code, t),
                  at: formatDateTime(queue.lastPollError.at),
                })}
              </p>
            )}
            {loadError && (
              <p className={feedbackErrorClass} role="alert" data-testid="queue-refresh-error">
                {t('dashboard.refreshError', { error: loadError })}
              </p>
            )}
            {actions.actionError && (
              <p className={feedbackErrorClass} role="alert" data-testid="queue-action-error">
                {actions.actionError}
              </p>
            )}

            <Panel className={styles.specQueuePanel}>
              <h2>{t('dashboard.queue')}</h2>
              {queue.items.length === 0 ? (
                <p className={fieldHintClass} data-testid="queue-empty">
                  {t('dashboard.empty')}
                </p>
              ) : (
                <ol className={styles.specQueueItems} data-testid="queue-items" ref={listRef}>
                  {queue.items.map((item) => {
                    const index = queuedItems.indexOf(item);
                    return renderRow(item, index >= 0 ? index : undefined);
                  })}
                </ol>
              )}
              {queue.lastPolledAt && (
                <p className={styles.specQueuePolled} data-testid="queue-last-polled">
                  {t('dashboard.lastPolled', { at: formatDateTime(queue.lastPolledAt) })}
                </p>
              )}
            </Panel>

            {queue.recentlyFinished.length > 0 && (
              <Panel className={styles.specQueuePanel}>
                <h2>{t('dashboard.recentlyFinished')}</h2>
                <ul className={styles.specQueueItems} data-testid="queue-recent">
                  {queue.recentlyFinished.map((item) => renderRow(item))}
                </ul>
              </Panel>
            )}
          </>
        )}
      </main>

      {confirmation && copy && (
        <ConfirmActionDialog
          title={copy.title}
          message={copy.message}
          confirmLabel={copy.confirmLabel}
          busy={actions.confirmBusy}
          error={actions.confirmError}
          onConfirm={actions.confirm}
          onCancel={actions.closeConfirmation}
        />
      )}
      {adding && project && (
        <AddToQueueDialog
          project={project}
          onCancel={() => setAdding(false)}
          onAdded={(item) => {
            setAdding(false);
            setAnnouncement(t('dashboard.added', { specFile: item.specFile }));
            void refresh();
          }}
        />
      )}
    </AppShell>
  );
}
