import { IconBox } from '../IconBox/IconBox';
import styles from './SpecPanel.module.scss';
import { Button } from '../../ui/Button/Button';

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
      <Button onClick={onGoToWorkflow}>⚡ Go to Workflow Design &amp; Execution</Button>
      <p className={styles.specEmptyHint}>
        Design or select a workflow there to create spec files.
      </p>
    </>
  );
}
