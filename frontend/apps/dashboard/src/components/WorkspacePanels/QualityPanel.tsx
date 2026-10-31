import type { QualityControl } from '@openjcockpit/api';
import { useTranslation } from '@openjcockpit/i18n';
import { IconBox, Panel, PanelHeader, PanelTitle, StatusChip } from '@openjcockpit/ui';
import styles from './WorkspacePanels.module.scss';
import { controlDetail, controlName } from './sampleData';

/** Decorative glyph, not translatable copy. */
const CHECK_MARK = '\u2713';

export function QualityPanel({ controls }: { controls: QualityControl[] }) {
  const { t } = useTranslation('dashboard');
  return (
    <Panel as="aside" className={styles.qualityPanel}>
      <PanelHeader className={styles.panelHeaderQuality}>
        <PanelTitle>
          <IconBox label="✓" compact /> <h2>{t('quality.title')}</h2>
          <StatusChip label={t('sampleData')} kind="waiting" />
        </PanelTitle>
      </PanelHeader>

      <div className={styles.qualityList}>
        {controls.map((control) => (
          <article className={styles.qualityRow} key={control.name}>
            <IconBox label="⌬" compact />
            <div>
              <strong>{controlName(t, control.name)}</strong>
              {control.statusText && <small>{controlDetail(t, control.statusText)}</small>}
            </div>
            <span className={styles.check}>{CHECK_MARK}</span>
          </article>
        ))}
      </div>
    </Panel>
  );
}
