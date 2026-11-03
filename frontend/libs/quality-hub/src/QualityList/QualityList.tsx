import type { QualityControl } from '@openjcockpit/api';
import { useTranslation } from '@openjcockpit/i18n';
import { Icon, IconBox } from '@openjcockpit/ui';
import { controlDetail, controlName } from '../sampleData';
import styles from './QualityList.module.scss';

/** Decorative glyph, not translatable copy. */
const CHECK_MARK = '\u2713';

export function QualityList({ controls }: { controls: QualityControl[] }) {
  const { t } = useTranslation('quality');
  return (
    <div className={styles.qualityList}>
      {controls.map((control) => (
        <article className={styles.qualityRow} key={control.name}>
          <IconBox label={<Icon name="agent" />} compact />
          <div>
            <strong>{controlName(t, control.name)}</strong>
            {control.statusText && <small>{controlDetail(t, control.statusText)}</small>}
          </div>
          <span className={styles.check}>{CHECK_MARK}</span>
        </article>
      ))}
    </div>
  );
}
