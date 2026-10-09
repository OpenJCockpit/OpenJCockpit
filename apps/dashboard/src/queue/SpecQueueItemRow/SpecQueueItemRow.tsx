import '../../components/StatusChip/StatusChip.scss';
import './SpecQueueItemRow.scss';
import { useId } from 'react';
import { failureReasonText, itemActions, itemPresentation, safeHttpUrl } from '../queueStatus';
import type { SpecQueueItem } from '../specQueueTypes';

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
  onOpenRun?: (item: SpecQueueItem) => void;
}

const MERGE_STATE_TEXT = {
  WAITING_FOR_MERGEABILITY: 'Waiting until the pull request can be merged',
  MERGE_IN_PROGRESS: 'Merge in progress',
} as const;

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
  const noteId = useId();
  const actions = itemActions(item.status);
  const presentation = itemPresentation(item);
  const spec = item.specFile;
  const prUrls = item.pullRequestUrls.map(safeHttpUrl).filter((url): url is string => url !== null);

  return (
    <li
      className="queue-item"
      data-testid="queue-item"
      data-item-id={item.id}
      data-status={item.status}
    >
      <div className="queue-item__main">
        {queuedIndex !== undefined && (
          <span className="queue-item__position" data-testid="queue-item-position">
            {queuedIndex + 1}
          </span>
        )}
        <div className="queue-item__title">
          <strong data-testid="queue-item-spec">{spec}</strong>
          <span className="queue-item__workflow" data-testid="queue-item-workflow">
            {item.workflowName ?? item.workflowId}
          </span>
        </div>
        <span
          className={`status-chip status-chip--${presentation.kind}`}
          data-testid="queue-item-status"
        >
          <span aria-hidden="true">{presentation.icon}</span>&nbsp;{presentation.label}
        </span>
      </div>

      {item.status === 'FAILED' && item.failureReason && (
        <p className="queue-item__failure" data-testid="queue-item-failure">
          {failureReasonText(item.failureReason)}
        </p>
      )}
      {item.status === 'MERGING' && item.mergeState && (
        <p className="queue-item__note" data-testid="queue-item-merge-state">
          {MERGE_STATE_TEXT[item.mergeState]}
        </p>
      )}

      <div className="queue-item__meta">
        {actions.canEdit ? (
          <>
            <label className="queue-item__auto-merge">
              <input
                type="checkbox"
                data-testid="queue-item-auto-merge"
                aria-label={`Auto-merge ${spec}`}
                aria-describedby={noteId}
                checked={item.autoMerge}
                disabled={busy || !autoMergeAllowed}
                onChange={(e) => onAutoMergeChange(item, e.target.checked)}
              />
              <span aria-hidden="true">Auto-merge</span>
            </label>
            <span id={noteId} className="queue-item__auto-merge-note">
              Merged without human review unless the workflow has an approval gate.
              {!autoMergeAllowed && ' Auto-merge is not allowed for this project.'}
            </span>
          </>
        ) : (
          <span data-testid="queue-item-auto-merge-indicator">
            Auto-merge: {item.autoMerge ? 'on' : 'off'}
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
            {prUrls.length > 1 ? `Pull request ${index + 1} ↗` : 'Pull request ↗'}
          </a>
        ))}
        {item.workflowRunId && onOpenRun && (
          <button
            type="button"
            className="link-button"
            data-testid="queue-item-run-link"
            aria-label={`View the run of ${spec}`}
            onClick={() => onOpenRun(item)}
          >
            View run →
          </button>
        )}
      </div>

      <div className="queue-item__actions">
        {actions.canMove && (
          <>
            <button
              type="button"
              className="button button--small"
              data-testid="queue-item-move-up"
              aria-label={`Move ${spec} up`}
              disabled={busy || queuedIndex === 0}
              onClick={() => onMove(item, 'up')}
            >
              ↑
            </button>
            <button
              type="button"
              className="button button--small"
              data-testid="queue-item-move-down"
              aria-label={`Move ${spec} down`}
              disabled={busy || queuedIndex === queuedCount - 1}
              onClick={() => onMove(item, 'down')}
            >
              ↓
            </button>
          </>
        )}
        {actions.canRemove && (
          <button
            type="button"
            className="button button--small"
            data-testid="queue-item-remove"
            aria-label={`Remove ${spec} from the queue`}
            disabled={busy}
            onClick={() => onRemove(item)}
          >
            Remove
          </button>
        )}
        {actions.canCancel && (
          <button
            type="button"
            className="button button--small"
            data-testid="queue-item-cancel"
            aria-label={`Cancel ${spec}`}
            disabled={busy}
            onClick={() => onCancelRun(item)}
          >
            Cancel
          </button>
        )}
        {actions.canRetry && (
          <button
            type="button"
            className="button button--small"
            data-testid="queue-item-retry"
            aria-label={`Retry ${spec}`}
            disabled={busy}
            onClick={() => onRetry(item)}
          >
            Retry
          </button>
        )}
        {actions.canSkip && (
          <button
            type="button"
            className="button button--small"
            data-testid="queue-item-skip"
            aria-label={`Skip ${spec}`}
            disabled={busy}
            onClick={() => onSkip(item)}
          >
            Skip
          </button>
        )}
      </div>
    </li>
  );
}
