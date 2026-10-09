import './SpecQueueDashboard.scss';
import { useCallback, useEffect, useRef, useState } from 'react';
import type { Project } from '../../types';
import { AddToQueueDialog } from '../AddToQueueDialog/AddToQueueDialog';
import { AutoMergeSettingToggle } from '../AutoMergeSettingToggle/AutoMergeSettingToggle';
import { ConfirmActionDialog } from '../ConfirmActionDialog/ConfirmActionDialog';
import { pollErrorText, queueStateText } from '../queueStatus';
import {
  getSpecQueue,
  pauseSpecQueue,
  removeSpecQueueItem,
  reorderSpecQueue,
  resumeSpecQueue,
  retrySpecQueueItem,
  skipSpecQueueItem,
  updateSpecQueueItem,
} from '../specQueueApi';
import type { SpecQueue, SpecQueueItem } from '../specQueueTypes';
import { SpecQueueItemRow } from '../SpecQueueItemRow/SpecQueueItemRow';
import type { MoveDirection } from '../SpecQueueItemRow/SpecQueueItemRow';

interface Props {
  project: Project | null;
  onBack: () => void;
  onOpenRun?: (workflowId: string, runId: string) => void;
}

type ConfirmationKind = 'remove' | 'cancel' | 'skip';
interface Confirmation {
  kind: ConfirmationKind;
  item: SpecQueueItem;
}

const POLL_INTERVAL_MS = 5000;
const MAX_POLL_INTERVAL_MS = 60000;

function messageOf(e: unknown, fallback: string): string {
  return e instanceof Error && e.message ? e.message : fallback;
}

function localized(iso: string): string {
  return new Date(iso).toLocaleString();
}

function confirmationCopy({ kind, item }: Confirmation) {
  const spec = item.specFile;
  switch (kind) {
    case 'remove':
      return {
        title: `Remove ${spec}?`,
        message: 'The spec file leaves the queue. It can be added again later.',
        confirmLabel: 'Remove',
        done: `${spec} removed`,
      };
    case 'cancel':
      return {
        title: `Cancel ${spec}?`,
        message:
          'The running workflow is stopped and the queue is paused. Any pull request that was already opened stays open.',
        confirmLabel: 'Cancel run',
        done: `${spec} cancelled`,
      };
    case 'skip':
      return {
        title: `Skip ${spec}?`,
        message: 'The failed item is marked as skipped so the queue can continue.',
        confirmLabel: 'Skip',
        done: `${spec} skipped`,
      };
  }
}

export function SpecQueueDashboard({ project, onBack, onOpenRun }: Props) {
  const projectId = project?.id ?? null;
  const [queue, setQueue] = useState<SpecQueue | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [announcement, setAnnouncement] = useState('');
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null);
  const [confirmBusy, setConfirmBusy] = useState(false);
  const [confirmError, setConfirmError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);

  const listRef = useRef<HTMLOListElement>(null);
  const pendingFocus = useRef<{ itemId: string; direction: MoveDirection } | null>(null);
  const mounted = useRef(true);
  // Bumped on every queue write and on project change: a response only applies if no newer
  // write happened while it was in flight.
  const generation = useRef(0);
  const pendingLoads = useRef(0);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const applyQueue = useCallback((next: SpecQueue) => {
    generation.current += 1;
    setQueue(next);
  }, []);

  /** Resolves false only when the load itself failed (a superseded response is not a failure). */
  const refresh = useCallback(async (): Promise<boolean> => {
    if (!projectId) return true;
    const ticket = ++generation.current;
    pendingLoads.current += 1;
    try {
      const loaded = await getSpecQueue(projectId);
      if (!mounted.current || ticket !== generation.current) return true;
      setQueue(loaded);
      setLoadError(null);
      return true;
    } catch (e) {
      if (!mounted.current || ticket !== generation.current) return true;
      setLoadError(messageOf(e, 'Failed to load the spec queue'));
      return false;
    } finally {
      pendingLoads.current -= 1;
    }
  }, [projectId]);

  useEffect(() => {
    setQueue(null);
    setLoadError(null);
    setActionError(null);
    if (!projectId) return;
    // Poll while the tab is visible; back off exponentially while loads keep failing.
    let timer: ReturnType<typeof setTimeout> | undefined;
    let failures = 0;
    let stopped = false;
    const schedule = () => {
      clearTimeout(timer);
      if (stopped || document.hidden) return;
      const delay = Math.min(POLL_INTERVAL_MS * 2 ** failures, MAX_POLL_INTERVAL_MS);
      timer = setTimeout(tick, delay);
    };
    const load = async () => {
      failures = (await refresh()) ? 0 : failures + 1;
      schedule();
    };
    const tick = () => {
      if (pendingLoads.current === 0) void load();
      else schedule();
    };
    const onVisibilityChange = () => {
      clearTimeout(timer);
      if (!document.hidden && pendingLoads.current === 0) void load();
    };
    void load();
    document.addEventListener('visibilitychange', onVisibilityChange);
    return () => {
      stopped = true;
      clearTimeout(timer);
      document.removeEventListener('visibilitychange', onVisibilityChange);
      generation.current += 1; // drop responses for the previous project
    };
  }, [projectId, refresh]);

  // After a reorder, keep keyboard focus on the moved row's move button.
  useEffect(() => {
    if (busy || !pendingFocus.current) return;
    const { itemId, direction } = pendingFocus.current;
    pendingFocus.current = null;
    const row = listRef.current?.querySelector<HTMLElement>(
      `[data-item-id="${CSS.escape(itemId)}"]`,
    );
    if (!row) return;
    const same = row.querySelector<HTMLButtonElement>(
      `[data-testid="queue-item-move-${direction}"]`,
    );
    const opposite = row.querySelector<HTMLButtonElement>(
      `[data-testid="queue-item-move-${direction === 'up' ? 'down' : 'up'}"]`,
    );
    (same && !same.disabled ? same : opposite)?.focus();
  }, [queue, busy]);

  async function run(action: () => Promise<unknown>, fallback: string, reload = true) {
    setBusy(true);
    setActionError(null);
    try {
      await action();
      if (reload) await refresh();
    } catch (e) {
      if (mounted.current) setActionError(messageOf(e, fallback));
      // Resync anyway so a stale-order 409 shows the current order.
      await refresh();
    } finally {
      if (mounted.current) setBusy(false);
    }
  }

  const queuedItems = queue?.items.filter((i) => i.status === 'QUEUED') ?? [];

  function handleMove(item: SpecQueueItem, direction: MoveDirection) {
    if (!projectId) return;
    const ids = queuedItems.map((i) => i.id);
    const from = ids.indexOf(item.id);
    const to = direction === 'up' ? from - 1 : from + 1;
    if (from < 0 || to < 0 || to >= ids.length) return;
    [ids[from], ids[to]] = [ids[to], ids[from]];
    void run(
      async () => {
        const response = await reorderSpecQueue(projectId, ids);
        if (!mounted.current) return;
        pendingFocus.current = { itemId: item.id, direction };
        applyQueue(response);
        setAnnouncement(`${item.specFile} moved to position ${to + 1} of ${ids.length}`);
      },
      'Failed to reorder the queue',
      false,
    );
  }

  function handlePauseResume(pause: boolean) {
    if (!projectId) return;
    void run(
      async () => {
        const response = await (pause ? pauseSpecQueue(projectId) : resumeSpecQueue(projectId));
        if (!mounted.current) return;
        applyQueue(response);
        setAnnouncement(pause ? 'Queue paused' : 'Queue resumed');
      },
      pause ? 'Failed to pause the queue' : 'Failed to resume the queue',
      false,
    );
  }

  function openConfirmation(kind: ConfirmationKind, item: SpecQueueItem) {
    setConfirmError(null);
    setConfirmation({ kind, item });
  }

  async function handleConfirm() {
    if (!projectId || !confirmation) return;
    const { kind, item } = confirmation;
    const copy = confirmationCopy(confirmation);
    setConfirmBusy(true);
    setConfirmError(null);
    try {
      await (kind === 'skip'
        ? skipSpecQueueItem(projectId, item.id)
        : removeSpecQueueItem(projectId, item.id));
      if (!mounted.current) return;
      setConfirmation(null);
      setAnnouncement(copy.done);
      await refresh();
    } catch (e) {
      if (mounted.current) setConfirmError(messageOf(e, 'The action failed'));
    } finally {
      if (mounted.current) setConfirmBusy(false);
    }
  }

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
        onMove={handleMove}
        onAutoMergeChange={(it, autoMerge) =>
          projectId &&
          void run(
            () => updateSpecQueueItem(projectId, it.id, { autoMerge }),
            'Failed to update the queue item',
          )
        }
        onRemove={(it) => openConfirmation('remove', it)}
        onCancelRun={(it) => openConfirmation('cancel', it)}
        onSkip={(it) => openConfirmation('skip', it)}
        onRetry={(it) =>
          projectId &&
          void run(() => retrySpecQueueItem(projectId, it.id), 'Failed to retry the queue item')
        }
        onOpenRun={
          onOpenRun
            ? (it) => {
                if (it.workflowRunId) onOpenRun(it.workflowId, it.workflowRunId);
              }
            : undefined
        }
      />
    );
  }

  const copy = confirmation ? confirmationCopy(confirmation) : null;

  return (
    <div className="app-shell">
      <div className="neural-bg" aria-hidden="true" />
      <div className="orb orb--left" aria-hidden="true" />
      <div className="orb orb--right" aria-hidden="true" />

      <header className="site-header">
        <div className="brand">
          <img src="/openjcockpit-logo.png" alt="OpenJCockpit" className="brand-logo" />
        </div>
        <div className="header-actions">
          <button className="back-button" onClick={onBack} title="Back to dashboard">
            ← Back
          </button>
        </div>
      </header>

      <main className="spec-queue">
        <div className="spec-queue__header">
          <div>
            <span className="eyebrow">Spec Queue</span>
            <h1>{project ? project.name : 'No project selected'}</h1>
          </div>
          {project && queue && (
            <div className="spec-queue__controls">
              <span data-testid="queue-state">State: {queueStateText(queue.state)}</span>
              {queue.state === 'ACTIVE' ? (
                <button
                  type="button"
                  className="button button--small"
                  data-testid="queue-pause"
                  disabled={busy}
                  onClick={() => handlePauseResume(true)}
                >
                  Pause queue
                </button>
              ) : (
                <button
                  type="button"
                  className="button button--small"
                  data-testid="queue-resume"
                  disabled={busy}
                  onClick={() => handlePauseResume(false)}
                >
                  Resume queue
                </button>
              )}
              <button
                type="button"
                className="button button--small button--start"
                data-testid="queue-add"
                onClick={() => setAdding(true)}
              >
                Add to queue
              </button>
            </div>
          )}
        </div>

        <div
          className="spec-queue__visually-hidden"
          role="status"
          aria-live="polite"
          aria-atomic="true"
          data-testid="queue-announcer"
        >
          {announcement}
        </div>

        {!project && (
          <p className="settings-desc">Select a project first to manage its spec queue.</p>
        )}
        {project && !queue && !loadError && (
          <p className="settings-desc" data-testid="queue-loading">
            Loading the queue…
          </p>
        )}
        {project && !queue && loadError && (
          <p className="settings-error" role="alert" data-testid="queue-load-error">
            The queue could not be loaded — {loadError}
          </p>
        )}

        {project && queue && (
          <>
            {queue.state === 'PAUSED' && (
              <p className="spec-queue__banner" role="status" data-testid="queue-banner-paused">
                The queue is paused. Queued items wait until you resume it.
              </p>
            )}
            {queue.state === 'HALTED' && (
              <p
                className="spec-queue__banner spec-queue__banner--warn"
                role="status"
                data-testid="queue-banner-halted"
              >
                The queue is halted because an item failed. Retry or skip the failed item, then
                resume the queue.
              </p>
            )}
            {!queue.runner.enabled && (
              <p
                className="spec-queue__banner spec-queue__banner--warn"
                role="status"
                data-testid="queue-banner-runner-disabled"
              >
                The queue runner is disabled. Queued items are not started automatically.
              </p>
            )}
            {queue.runner.enabled && !queue.runner.configured && (
              <p
                className="spec-queue__banner spec-queue__banner--warn"
                role="status"
                data-testid="queue-banner-runner-not-configured"
              >
                The queue runner has no identity configured. Queued items are not started
                automatically.
              </p>
            )}
            {queue.lastPollError && (
              <p
                className="spec-queue__banner spec-queue__banner--warn"
                role="status"
                data-testid="queue-banner-poll-error"
              >
                The last queue check failed: {pollErrorText(queue.lastPollError.code)} (
                {localized(queue.lastPollError.at)}).
              </p>
            )}
            {loadError && (
              <p className="settings-error" role="alert" data-testid="queue-refresh-error">
                The queue could not be refreshed — {loadError}
              </p>
            )}
            {actionError && (
              <p className="settings-error" role="alert" data-testid="queue-action-error">
                {actionError}
              </p>
            )}

            <section className="panel spec-queue__panel">
              <h2>Queue settings</h2>
              <AutoMergeSettingToggle
                projectId={project.id}
                autoMergeAllowed={queue.autoMergeAllowed}
                onChanged={(settings) => {
                  generation.current += 1;
                  setQueue((q) => (q ? { ...q, autoMergeAllowed: settings.autoMergeAllowed } : q));
                }}
              />
            </section>

            <section className="panel spec-queue__panel">
              <h2>Queue</h2>
              {queue.items.length === 0 ? (
                <p className="settings-desc" data-testid="queue-empty">
                  The queue is empty. Use “Add to queue” to queue a spec file.
                </p>
              ) : (
                <ol className="spec-queue__items" data-testid="queue-items" ref={listRef}>
                  {queue.items.map((item) => {
                    const index = queuedItems.indexOf(item);
                    return renderRow(item, index >= 0 ? index : undefined);
                  })}
                </ol>
              )}
              {queue.lastPolledAt && (
                <p className="spec-queue__polled" data-testid="queue-last-polled">
                  Runner last checked: {localized(queue.lastPolledAt)}
                </p>
              )}
            </section>

            {queue.recentlyFinished.length > 0 && (
              <section className="panel spec-queue__panel">
                <h2>Recently finished</h2>
                <ul className="spec-queue__items" data-testid="queue-recent">
                  {queue.recentlyFinished.map((item) => renderRow(item))}
                </ul>
              </section>
            )}
          </>
        )}
      </main>

      {confirmation && copy && (
        <ConfirmActionDialog
          title={copy.title}
          message={copy.message}
          confirmLabel={copy.confirmLabel}
          busy={confirmBusy}
          error={confirmError}
          onConfirm={() => void handleConfirm()}
          onCancel={() => setConfirmation(null)}
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
    </div>
  );
}
