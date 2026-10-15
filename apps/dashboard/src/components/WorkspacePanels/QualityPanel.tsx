import type { QualityControl } from '../../types';
import { IconBox } from '../IconBox/IconBox';
import styles from './WorkspacePanels.module.scss';
import { cx } from '../../ui/cx';

export function QualityPanel({ controls }: { controls: QualityControl[] }) {
  return (
    <aside className={cx('panel', styles.qualityPanel)}>
      <header className={cx('panel-header', styles.panelHeaderQuality)}>
        <div className="panel-title">
          <IconBox label="✓" compact /> <h2>Quality status</h2>
        </div>
        <button className={styles.smallLink}>Open quality dashboard ↗</button>
      </header>

      <div className={styles.qualityList}>
        {controls.map((control) => (
          <article className={styles.qualityRow} key={control.name}>
            <IconBox label="⌬" compact />
            <strong>{control.name}</strong>
            <span className={styles.check}>✓</span>
          </article>
        ))}
      </div>

      <button className="link-button">
        View all quality rules <span>→</span>
      </button>
    </aside>
  );
}
