import './WorkflowExecutionHistory.scss';
import type { WorkflowExecutionPage } from '../workflowTypes';
import { StatusChip } from '../../components/StatusChip/StatusChip';
import { formatRunDuration, runStatusKind, runStatusLabel } from '../runStatus';

interface Props {
  page: WorkflowExecutionPage | null;
  loading: boolean;
  error: string | null;
  onSelect: (runId: string) => void;
  onRetry: () => void;
  onLoadMore: () => void;
  selectedRunId?: string | null;
}

export function WorkflowExecutionHistory({
  page,
  loading,
  error,
  onSelect,
  onRetry,
  onLoadMore,
  selectedRunId,
}: Props) {
  return (
    <section className="workflow-execution-history">
      <h3>Execution history</h3>

      {error && (
        <div className="workflow-execution-history-error" role="alert">
          <p>{error}</p>
          <button type="button" className="button" onClick={onRetry}>
            Retry
          </button>
        </div>
      )}

      {!error && loading && page === null && (
        <p className="workflow-execution-history-loading">Loading executions…</p>
      )}

      {!error && !loading && page !== null && page.items.length === 0 && (
        <p className="workflow-execution-history-empty">This workflow has not been executed yet.</p>
      )}

      {!error && page !== null && page.items.length > 0 && (
        <>
          <table className="workflow-execution-history-table">
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
                    item.runId === selectedRunId ? 'workflow-execution-history-row--selected' : ''
                  }
                  aria-current={item.runId === selectedRunId ? 'true' : undefined}
                >
                  <td>
                    <button type="button" onClick={() => onSelect(item.runId)}>
                      {item.runId}
                    </button>
                  </td>
                  <td>{new Date(item.startedAt).toLocaleString()}</td>
                  <td>{item.completedAt ? new Date(item.completedAt).toLocaleString() : 'n/a'}</td>
                  <td>{formatRunDuration(item.startedAt, item.completedAt)}</td>
                  <td>
                    <StatusChip
                      label={runStatusLabel(item.status)}
                      kind={runStatusKind(item.status)}
                    />
                  </td>
                  <td>{item.startedBy ?? 'n/a'}</td>
                </tr>
              ))}
            </tbody>
          </table>

          {page.hasMore && (
            <button
              type="button"
              className="button workflow-execution-history-load-more"
              onClick={onLoadMore}
            >
              Load more
            </button>
          )}
        </>
      )}
    </section>
  );
}
