import type { PullRequestSummary } from '../../types';
import { IconBox } from '../IconBox/IconBox';
import { StatusChip } from '../StatusChip/StatusChip';
import styles from './WorkspacePanels.module.scss';
import { cx } from '../../ui/cx';

export function PullRequestPanel({ pullRequests }: { pullRequests: PullRequestSummary[] }) {
  return (
    <section className={cx('panel', styles.prPanel)}>
      <header className="panel-header panel-header--simple">
        <div className="panel-title">
          <IconBox label="⌯" compact /> <h2>Open pull requests</h2>
        </div>
        <button className={styles.smallLink}>
          View all pull requests <span>→</span>
        </button>
      </header>

      <div className={styles.prList}>
        {pullRequests.slice(0, 2).map((pr) => (
          <article className={styles.prCard} key={pr.id}>
            <span className={styles.prBadge}>PR #{pr.id}</span>
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
