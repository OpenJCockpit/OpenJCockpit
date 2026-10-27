import { useRef, useState, type ReactNode } from 'react';
import type { Project, SpecQueueItem } from '@openjcockpit/api';
import { AddToQueueDialog } from '../AddToQueueDialog/AddToQueueDialog';
import { AutoMergeSettingToggle } from '../AutoMergeSettingToggle/AutoMergeSettingToggle';
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
  cx,
} from '@openjcockpit/ui';
import styles from './SpecQueueDashboard.module.scss';
import { confirmationCopy, useQueueActions } from './useQueueActions';
import { useSpecQueue } from './useSpecQueue';

interface Props {
  project: Project | null;
  onBack: () => void;
  onOpenRun?: (workflowId: string, runId: string) => void;
  /** Replaces the default back-button header, e.g. with the app's shared top menu. */
  header?: ReactNode;
}

function localized(iso: string): string {
  return new Date(iso).toLocaleString();
}

export function SpecQueueDashboard({ project, onBack, onOpenRun, header }: Props) {
  const projectId = project?.id ?? null;
  const { queue, loadError, applyQueue, refresh, applyAutoMergeAllowed } = useSpecQueue(projectId);
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

  const copy = confirmation ? confirmationCopy(confirmation) : null;

  return (
    <AppShell>
      {header ?? <AppHeader onBack={onBack} backTitle="Back to dashboard" />}

      <main className={styles.specQueue}>
        <div className={styles.specQueueHeader}>
          <div>
            <Eyebrow>Spec Queue</Eyebrow>
            <h1>{project ? project.name : 'No project selected'}</h1>
          </div>
          {project && queue && (
            <div className={styles.specQueueControls}>
              <span data-testid="queue-state">State: {queueStateText(queue.state)}</span>
              {queue.state === 'ACTIVE' ? (
                <Button
                  type="button"
                  size="sm"
                  data-testid="queue-pause"
                  disabled={busy}
                  onClick={() => actions.pauseResume(true)}
                >
                  Pause queue
                </Button>
              ) : (
                <Button
                  type="button"
                  size="sm"
                  data-testid="queue-resume"
                  disabled={busy}
                  onClick={() => actions.pauseResume(false)}
                >
                  Resume queue
                </Button>
              )}
              <Button
                type="button"
                size="sm"
                variant="primary"
                data-testid="queue-add"
                onClick={() => setAdding(true)}
              >
                Add to queue
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

        {!project && (
          <p className={fieldHintClass}>Select a project first to manage its spec queue.</p>
        )}
        {project && !queue && !loadError && (
          <p className={fieldHintClass} data-testid="queue-loading">
            Loading the queue…
          </p>
        )}
        {project && !queue && loadError && (
          <p className={feedbackErrorClass} role="alert" data-testid="queue-load-error">
            The queue could not be loaded — {loadError}
          </p>
        )}

        {project && queue && (
          <>
            {queue.state === 'PAUSED' && (
              <p className={styles.specQueueBanner} role="status" data-testid="queue-banner-paused">
                The queue is paused. Queued items wait until you resume it.
              </p>
            )}
            {queue.state === 'HALTED' && (
              <p
                className={cx(styles.specQueueBanner, styles.specQueueBannerWarn)}
                role="status"
                data-testid="queue-banner-halted"
              >
                The queue is halted because an item failed. Retry or skip the failed item, then
                resume the queue.
              </p>
            )}
            {!queue.runner.enabled && (
              <p
                className={cx(styles.specQueueBanner, styles.specQueueBannerWarn)}
                role="status"
                data-testid="queue-banner-runner-disabled"
              >
                The queue runner is disabled. Queued items are not started automatically.
              </p>
            )}
            {queue.runner.enabled && !queue.runner.configured && (
              <p
                className={cx(styles.specQueueBanner, styles.specQueueBannerWarn)}
                role="status"
                data-testid="queue-banner-runner-not-configured"
              >
                The queue runner has no identity configured. Queued items are not started
                automatically.
              </p>
            )}
            {queue.lastPollError && (
              <p
                className={cx(styles.specQueueBanner, styles.specQueueBannerWarn)}
                role="status"
                data-testid="queue-banner-poll-error"
              >
                The last queue check failed: {pollErrorText(queue.lastPollError.code)} (
                {localized(queue.lastPollError.at)}).
              </p>
            )}
            {loadError && (
              <p className={feedbackErrorClass} role="alert" data-testid="queue-refresh-error">
                The queue could not be refreshed — {loadError}
              </p>
            )}
            {actions.actionError && (
              <p className={feedbackErrorClass} role="alert" data-testid="queue-action-error">
                {actions.actionError}
              </p>
            )}

            <Panel className={styles.specQueuePanel}>
              <h2>Queue settings</h2>
              <AutoMergeSettingToggle
                projectId={project.id}
                autoMergeAllowed={queue.autoMergeAllowed}
                onChanged={(settings) => void applyAutoMergeAllowed(settings.autoMergeAllowed)}
              />
            </Panel>

            <Panel className={styles.specQueuePanel}>
              <h2>Queue</h2>
              {queue.items.length === 0 ? (
                <p className={fieldHintClass} data-testid="queue-empty">
                  The queue is empty. Use “Add to queue” to queue a spec file.
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
                  Runner last checked: {localized(queue.lastPolledAt)}
                </p>
              )}
            </Panel>

            {queue.recentlyFinished.length > 0 && (
              <Panel className={styles.specQueuePanel}>
                <h2>Recently finished</h2>
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
            setAnnouncement(`${item.specFile} added to the queue`);
            void refresh();
          }}
        />
      )}
    </AppShell>
  );
}
