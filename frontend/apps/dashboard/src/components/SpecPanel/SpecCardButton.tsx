import type { SpecFile } from '@openjcockpit/api';
import { useFormatters, useLocale, useTranslation } from '@openjcockpit/i18n';
import { Icon, cx } from '@openjcockpit/ui';
import { formatRelativeTime, specTitle } from './specMeta';
import styles from './SpecPanel.module.scss';

interface Props {
  spec: SpecFile;
  selected: boolean;
  onSelect: (id: string) => void;
}

/** A selectable card for one spec file: title, file name and how recently it changed. */
export function SpecCardButton({ spec, selected, onSelect }: Props) {
  const { t } = useTranslation('dashboard');
  const { formatDateTime } = useFormatters();
  const { locale } = useLocale();
  return (
    <button
      className={cx(styles.card, selected && styles.cardSelected)}
      onClick={() => onSelect(spec.id)}
      aria-pressed={selected}
    >
      <span className={styles.cardIcon}>
        <Icon name="file" size={18} className={styles.glyph} />
      </span>
      <span className={styles.cardText}>
        <strong className={styles.cardTitle}>{specTitle(spec)}</strong>
        <span className={styles.cardMeta}>
          <span className={styles.fileName}>{spec.fileName}</span>
          <time
            dateTime={spec.lastChanged}
            title={t('specPanel.lastModified', { value: formatDateTime(spec.lastChanged) })}
          >
            {formatRelativeTime(spec.lastChanged, locale)}
          </time>
        </span>
      </span>
    </button>
  );
}
