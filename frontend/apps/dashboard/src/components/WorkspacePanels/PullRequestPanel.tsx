import type { PullRequestSummary } from '../../types';
import { IconBox } from '../IconBox/IconBox';
import { StatusChip } from '../StatusChip/StatusChip';
import styles from './WorkspacePanels.module.scss';
import { Panel, PanelHeader, PanelTitle } from '@openjcockpit/ui';

export function PullRequestPanel({ pullRequests }: { pullRequests: PullRequestSummary[] }) {
  return (
    <Panel as="section" className={styles.prPanel}>
      <PanelHeader simple>
        <PanelTitle>
          <IconBox label="⌯" compact /> <h2>Open pull requests</h2>
        </PanelTitle>
        <button className={styles.smallLink}>
          View all pull requests <span>→</span>
        </button>
      </PanelHeader>

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
    </Panel>
  );
}
