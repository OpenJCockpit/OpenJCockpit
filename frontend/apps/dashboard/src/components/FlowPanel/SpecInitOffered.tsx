import type { SpecInitResult } from '@openjcockpit/api';
import { StatusChip } from '../StatusChip/StatusChip';
import { ToolbarButton } from '../ToolbarButton/ToolbarButton';
import { buttonClass, feedbackResultClass } from '@openjcockpit/ui';

interface Props {
  result: SpecInitResult;
  onReload: () => void;
}

/** The spec-init pull request was just created. */
export function SpecInitOffered({ result, onReload }: Props) {
  return (
    <div className={feedbackResultClass}>
      <StatusChip label="Pull request offered" kind="ok" />
      <p>{result.message}</p>
      {result.pullRequestUrl && (
        <a
          className={buttonClass({ variant: 'link' })}
          href={result.pullRequestUrl}
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
