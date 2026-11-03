import type { SpecFile } from '@openjcockpit/api';
import { useFormatters, useLocale, useTranslation } from '@openjcockpit/i18n';
import { cx } from '@openjcockpit/ui';
import { formatRelativeTime, specTitle } from './specMeta';
import styles from './SpecPanel.module.scss';

interface Props {
  spec: SpecFile;
  selected: boolean;
  onSelect: (id: string) => void;
}

function FileGlyph() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" className={styles.glyph}>
      <path
        d="M14 3H7a2 2 0 00-2 2v14a2 2 0 002 2h10a2 2 0 002-2V8zM14 3v5h5M9 13h6M9 17h6"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
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
        <FileGlyph />
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
