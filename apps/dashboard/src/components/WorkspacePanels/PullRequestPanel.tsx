import type { PullRequestSummary } from '../../types';
import { IconBox } from '../IconBox/IconBox';
import { StatusChip } from '../StatusChip/StatusChip';

export function PullRequestPanel({ pullRequests }: { pullRequests: PullRequestSummary[] }) {
  return (
    <section className="panel pr-panel">
      <header className="panel-header panel-header--simple">
        <div className="panel-title">
          <IconBox label="⌯" compact /> <h2>Open pull requests</h2>
        </div>
        <button className="small-link">
          View all pull requests <span>→</span>
        </button>
      </header>

      <div className="pr-list">
        {pullRequests.slice(0, 2).map((pr) => (
          <article className="pr-card" key={pr.id}>
            <span className="pr-badge">PR #{pr.id}</span>
            <div>
              <strong>{pr.title}</strong>
              <span>
                {pr.ownerName} · {pr.createdAt.replace(' today', '')}
              </span>
            </div>
            <StatusChip label={pr.status} kind={pr.statusKind} />
          </article>
        ))}
      </div>
    </section>
  );
}
