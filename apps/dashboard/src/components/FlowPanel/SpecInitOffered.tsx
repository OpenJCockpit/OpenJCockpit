import type { SpecInitResult } from '../../types';
import { StatusChip } from '../StatusChip/StatusChip';
import { ToolbarButton } from '../ToolbarButton/ToolbarButton';

interface Props {
  result: SpecInitResult;
  onReload: () => void;
}

/** The spec-init pull request was just created. */
export function SpecInitOffered({ result, onReload }: Props) {
  return (
    <div className="spec-init-result">
      <StatusChip label="Pull request offered" kind="ok" />
      <p>{result.message}</p>
      {result.pullRequestUrl && (
        <a className="link-button" href={result.pullRequestUrl} target="_blank" rel="noreferrer">
          Open pull request <span>→</span>
        </a>
      )}
      <ToolbarButton variant="small" onClick={onReload}>
        ⟳ Reload specs
      </ToolbarButton>
    </div>
  );
}
