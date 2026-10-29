import { useId } from 'react';
import { useTranslation } from '@openjcockpit/i18n';
import { StatusChip, Button } from '@openjcockpit/ui';
import { failureReasonText, itemActions, itemPresentation, safeHttpUrl } from '../queueStatus';
import type { SpecQueueItem } from '@openjcockpit/api';
import styles from './SpecQueueItemRow.module.scss';

export type MoveDirection = 'up' | 'down';

interface Props {
  item: SpecQueueItem;
  /** 0-based index among QUEUED items; undefined when the item is not queued. */
  queuedIndex?: number;
  queuedCount: number;
  autoMergeAllowed: boolean;
  /** Disables every control. */
  busy: boolean;
  onMove: (item: SpecQueueItem, direction: MoveDirection) => void;
  onAutoMergeChange: (item: SpecQueueItem, autoMerge: boolean) => void;
  onRemove: (item: SpecQueueItem) => void;
  onCancelRun: (item: SpecQueueItem) => void;
  onRetry: (item: SpecQueueItem) => void;
  onSkip: (item: SpecQueueItem) => void;
  onOpenRun?: (workflowId: string, runId: string) => void;
}

export function SpecQueueItemRow({
  item,
  queuedIndex,
  queuedCount,
  autoMergeAllowed,
  busy,
  onMove,
  onAutoMergeChange,
  onRemove,
  onCancelRun,
  onRetry,
  onSkip,
  onOpenRun,
}: Props) {
  const { t } = useTranslation('specQueue');
  const noteId = useId();
  const actions = itemActions(item.status);
  const presentation = itemPresentation(item, t);
  const spec = item.specFile;
  const runId = item.workflowRunId;
  const prUrls = item.pullRequestUrls.map(safeHttpUrl).filter((url): url is string => url !== null);

  return (
    <li
      className={styles.queueItem}
      data-testid="queue-item"
      data-item-id={item.id}
      data-status={item.status}
    >
      <div className={styles.queueItemMain}>
        {queuedIndex !== undefined && (
          <span className={styles.queueItemPosition} data-testid="queue-item-position">
            {queuedIndex + 1}
          </span>
        )}
        <div className={styles.queueItemTitle}>
          <strong data-testid="queue-item-spec">{spec}</strong>
          <span className={styles.queueItemWorkflow} data-testid="queue-item-workflow">
            {item.workflowName ?? item.workflowId}
          </span>
        </div>
        <span data-testid="queue-item-status">
          <StatusChip
            label={`${presentation.icon} ${presentation.label}`}
            kind={presentation.kind}
          />
        </span>
      </div>

      {item.status === 'FAILED' && item.failureReason && (
        <p className={styles.queueItemFailure} data-testid="queue-item-failure">
          {failureReasonText(item.failureReason, t)}
        </p>
      )}
      {item.status === 'MERGING' && item.mergeState && (
        <p className={styles.queueItemNote} data-testid="queue-item-merge-state">
          {t(`row.mergeState.${item.mergeState}`)}
        </p>
      )}

      <div className={styles.queueItemMeta}>
        {actions.canEdit ? (
          <>
            <label
              className={styles.queueItemAutoMerge}
              title={autoMergeAllowed ? undefined : t('row.autoMergeNotAllowed')}
            >
              <input
                type="checkbox"
                data-testid="queue-item-auto-merge"
                aria-label={t('row.autoMergeAria', { spec })}
                aria-describedby={noteId}
                checked={item.autoMerge}
                disabled={busy || !autoMergeAllowed}
                onChange={(e) => onAutoMergeChange(item, e.target.checked)}
              />
              <span aria-hidden="true">{t('row.autoMerge')}</span>
            </label>
            <span
              id={noteId}
              className={styles.queueItemAutoMergeNote}
              data-testid="queue-item-auto-merge-note"
            >
              {t('row.autoMergeNote')}
              {!autoMergeAllowed && ` ${t('row.autoMergeNoteNotAllowed')}`}
            </span>
          </>
        ) : (
          <span data-testid="queue-item-auto-merge-indicator">
            {item.autoMerge ? t('row.autoMergeIndicatorOn') : t('row.autoMergeIndicatorOff')}
          </span>
        )}
        {prUrls.map((url, index) => (
          <a
            key={url}
            href={url}
            target="_blank"
            rel="noopener noreferrer"
            data-testid="queue-item-pr-link"
          >
            {prUrls.length > 1 ? t('row.pullRequestN', { n: index + 1 }) : t('row.pullRequest')}
          </a>
        ))}
        {runId && onOpenRun && (
          <Button
            type="button"
            variant="link"
            data-testid="queue-item-run-link"
            aria-label={t('row.viewRunAria', { spec })}
            onClick={() => onOpenRun(item.workflowId, runId)}
          >
            {t('row.viewRun')}
          </Button>
        )}
      </div>

      <div className={styles.queueItemActions}>
        {actions.canMove && (
          <>
            <Button
              type="button"
              size="sm"
              data-testid="queue-item-move-up"
              aria-label={t('row.moveUpAria', { spec })}
              disabled={busy || queuedIndex === 0}
              onClick={() => onMove(item, 'up')}
            >
              ↑
            </Button>
            <Button
              type="button"
              size="sm"
              data-testid="queue-item-move-down"
              aria-label={t('row.moveDownAria', { spec })}
              disabled={busy || queuedIndex === queuedCount - 1}
              onClick={() => onMove(item, 'down')}
            >
              ↓
            </Button>
          </>
        )}
        {actions.canRemove && (
          <Button
            type="button"
            size="sm"
            data-testid="queue-item-remove"
            aria-label={t('row.removeAria', { spec })}
            disabled={busy}
            onClick={() => onRemove(item)}
          >
            {t('row.remove')}
          </Button>
        )}
        {actions.canCancel && (
          <Button
            type="button"
            size="sm"
            data-testid="queue-item-cancel"
            aria-label={t('row.cancelAria', { spec })}
            disabled={busy}
            onClick={() => onCancelRun(item)}
          >
            {t('row.cancel')}
          </Button>
        )}
        {actions.canRetry && (
          <Button
            type="button"
            size="sm"
            data-testid="queue-item-retry"
            aria-label={t('row.retryAria', { spec })}
            disabled={busy}
            onClick={() => onRetry(item)}
          >
            {t('row.retry')}
          </Button>
        )}
        {actions.canSkip && (
          <Button
            type="button"
            size="sm"
            data-testid="queue-item-skip"
            aria-label={t('row.skipAria', { spec })}
            disabled={busy}
            onClick={() => onSkip(item)}
          >
            {t('row.skip')}
          </Button>
        )}
      </div>
    </li>
  );
}
