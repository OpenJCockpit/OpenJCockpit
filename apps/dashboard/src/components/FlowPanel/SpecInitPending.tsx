import type { SpecInitStatus } from '../../types';
import { StatusChip } from '../StatusChip/StatusChip';

interface Props {
  status: SpecInitStatus;
  onReload: () => void;
}

/** A spec-init branch / pull request is already open on the repository. */
export function SpecInitPending({ status, onReload }: Props) {
  return (
    <div className="spec-init-result">
      <StatusChip label="Pull request open" kind="open" />
      <p>
        A branch with the spec template is already prepared
        {status.branch ? `: ${status.branch}` : ''}. Merge the corresponding pull request to add the
        .specify folder.
      </p>
      {status.branchUrl && (
        <a className="button" href={status.branchUrl} target="_blank" rel="noreferrer">
          ⎇ View branch on the repository
        </a>
      )}
      {status.pullRequestUrl && (
        <a className="link-button" href={status.pullRequestUrl} target="_blank" rel="noreferrer">
          Open pull request <span>→</span>
        </a>
      )}
      <button className="button button--small" onClick={onReload}>
        ⟳ Reload specs
      </button>
    </div>
  );
}
