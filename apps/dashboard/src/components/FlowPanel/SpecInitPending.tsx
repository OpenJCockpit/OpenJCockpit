import type { SpecInitStatus } from '../../types';
import { StatusChip } from '../StatusChip/StatusChip';
import { ToolbarButton } from '../ToolbarButton/ToolbarButton';
import { buttonClass } from '../../ui/Button/Button';
import { feedbackResultClass } from '../../ui/Feedback/Feedback';

interface Props {
  status: SpecInitStatus;
  onReload: () => void;
}

/** A spec-init branch / pull request is already open on the repository. */
export function SpecInitPending({ status, onReload }: Props) {
  return (
    <div className={feedbackResultClass}>
      <StatusChip label="Pull request open" kind="open" />
      <p>
        A branch with the spec template is already prepared
        {status.branch ? `: ${status.branch}` : ''}. Merge the corresponding pull request to add the
        .specify folder.
      </p>
      {status.branchUrl && (
        <a className={buttonClass()} href={status.branchUrl} target="_blank" rel="noreferrer">
          ⎇ View branch on the repository
        </a>
      )}
      {status.pullRequestUrl && (
        <a
          className={buttonClass({ variant: 'link' })}
          href={status.pullRequestUrl}
          target="_blank"
          rel="noreferrer"
        >
          Open pull request <span>→</span>
        </a>
      )}
      <ToolbarButton variant="small" onClick={onReload}>
        ⟳ Reload specs
      </ToolbarButton>
    </div>
  );
}
