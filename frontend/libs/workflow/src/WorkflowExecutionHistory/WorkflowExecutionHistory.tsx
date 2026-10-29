import { formatRunDuration, runStatusKind, runStatusLabel } from '@openjcockpit/api';
import type { WorkflowExecutionPage } from '@openjcockpit/api';
import { useFormatters, useTranslation } from '@openjcockpit/i18n';
import { StatusChip, Button, cx } from '@openjcockpit/ui';
import styles from './WorkflowExecutionHistory.module.scss';

interface Props {
  page: WorkflowExecutionPage | null;
  loading: boolean;
  error: string | null;
  onSelect: (runId: string) => void;
  onRetry: () => void;
  onLoadMore: () => void;
  selectedRunId?: string | null;
  /** Extra class for the root element, e.g. to position it inside a parent panel. */
  className?: string;
}

export function WorkflowExecutionHistory({
  page,
  loading,
  error,
  onSelect,
  onRetry,
  onLoadMore,
  selectedRunId,
  className,
}: Props) {
  const { t } = useTranslation('api');
  const { formatDateTime } = useFormatters();
  return (
    <section className={cx(styles.workflowExecutionHistory, className)}>
      <h3>Execution history</h3>

      {error && (
        <div className={styles.workflowExecutionHistoryError} role="alert">
          <p>{error}</p>
          <Button type="button" onClick={onRetry}>
            Retry
          </Button>
        </div>
      )}

      {!error && loading && page === null && (
        <p className={styles.workflowExecutionHistoryLoading}>Loading executions…</p>
      )}

      {!error && !loading && page !== null && page.items.length === 0 && (
        <p className={styles.workflowExecutionHistoryEmpty}>
          This workflow has not been executed yet.
        </p>
      )}

      {!error && page !== null && page.items.length > 0 && (
        <>
          <table className={styles.workflowExecutionHistoryTable}>
            <caption>Executions for this workflow</caption>
            <thead>
              <tr>
                <th scope="col">Run ID</th>
                <th scope="col">Started</th>
                <th scope="col">Completed</th>
                <th scope="col">Duration</th>
                <th scope="col">Status</th>
                <th scope="col">Started by</th>
              </tr>
            </thead>
            <tbody>
              {page.items.map((item) => (
                <tr
                  key={item.runId}
                  className={
                    item.runId === selectedRunId
                      ? styles.workflowExecutionHistoryRowSelected
                      : undefined
                  }
                  aria-current={item.runId === selectedRunId ? 'true' : undefined}
                >
                  <td>
                    <button
                      type="button"
                      className={styles.workflowExecutionHistoryRunId}
                      onClick={() => onSelect(item.runId)}
                    >
                      {item.runId}
                    </button>
                  </td>
                  <td>{formatDateTime(item.startedAt)}</td>
                  <td>{item.completedAt ? formatDateTime(item.completedAt) : t('notAvailable')}</td>
                  <td>{formatRunDuration(item.startedAt, item.completedAt, t)}</td>
                  <td>
                    <StatusChip
                      label={runStatusLabel(item.status, t)}
                      kind={runStatusKind(item.status)}
                    />
                  </td>
                  <td>{item.startedBy ?? t('notAvailable')}</td>
                </tr>
              ))}
            </tbody>
          </table>

          {page.hasMore && (
            <Button
              type="button"
              className={styles.workflowExecutionHistoryLoadMore}
              onClick={onLoadMore}
            >
              Load more
            </Button>
          )}
        </>
      )}
    </section>
  );
}
