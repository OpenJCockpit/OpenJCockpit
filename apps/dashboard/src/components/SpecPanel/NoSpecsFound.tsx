import { IconBox } from '../IconBox/IconBox';

interface Props {
  onGoToWorkflow: () => void;
}

/** Empty state shown when the repository has no spec files. */
export function NoSpecsFound({ onGoToWorkflow }: Props) {
  return (
    <>
      <article className="spec-card spec-card--empty">
        <IconBox label="📄" compact />
        <div>
          <strong>No Specs Found</strong>
          <span>No specs/ folder with specifications in the repository</span>
        </div>
      </article>
      <button className="button" onClick={onGoToWorkflow}>
        ⚡ Go to Workflow Design &amp; Execution
      </button>
      <p className="spec-empty-hint">Design or select a workflow there to create spec files.</p>
    </>
  );
}
