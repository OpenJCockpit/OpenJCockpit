import { IconBox, Button, specStyles, cx } from '@openjcockpit/ui';
import styles from './SpecPanel.module.scss';

interface Props {
  onGoToWorkflow: () => void;
}

/** Empty state shown when the repository has no spec files. */
export function NoSpecsFound({ onGoToWorkflow }: Props) {
  return (
    <>
      <article className={cx(specStyles.specCard, specStyles.specCardEmpty)}>
        <IconBox label="📄" compact className={specStyles.icon} />
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
