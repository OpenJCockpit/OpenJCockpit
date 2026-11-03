import { useTranslation } from '@openjcockpit/i18n';
import { SpecMarkdown } from './SpecMarkdown';
import styles from './SpecPreview.module.scss';

interface Props {
  content: string;
}

/** The selected spec rendered as markdown, filling its container and scrolling inside it. */
export function SpecPreview({ content }: Props) {
  const { t } = useTranslation('dashboard');
  return (
    <div className={styles.preview} data-testid="spec-preview">
      <header className={styles.previewHeader}>
        <span className={styles.previewLabel}>{t('specPanel.preview')}</span>
      </header>
      <div className={styles.previewBody}>
        <SpecMarkdown content={content} />
      </div>
    </div>
  );
}
