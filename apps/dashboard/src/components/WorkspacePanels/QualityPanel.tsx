import type { QualityControl } from '../../types';
import { IconBox } from '../IconBox/IconBox';
import styles from './WorkspacePanels.module.scss';
import { Panel, PanelHeader, PanelTitle, Button } from '@openjcockpit/ui';

export function QualityPanel({ controls }: { controls: QualityControl[] }) {
  return (
    <Panel as="aside" className={styles.qualityPanel}>
      <PanelHeader className={styles.panelHeaderQuality}>
        <PanelTitle>
          <IconBox label="✓" compact /> <h2>Quality status</h2>
        </PanelTitle>
        <button className={styles.smallLink}>Open quality dashboard ↗</button>
      </PanelHeader>

      <div className={styles.qualityList}>
        {controls.map((control) => (
          <article className={styles.qualityRow} key={control.name}>
            <IconBox label="⌬" compact />
            <strong>{control.name}</strong>
            <span className={styles.check}>✓</span>
          </article>
        ))}
      </div>

      <Button variant="link">
        View all quality rules <span>→</span>
      </Button>
    </Panel>
  );
}
