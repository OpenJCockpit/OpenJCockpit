import type { EvidenceEvent } from '@openjcockpit/api';
import { useTranslation } from '@openjcockpit/i18n';
import { IconBox, Panel, PanelHeader, PanelTitle } from '@openjcockpit/ui';
import styles from './WorkspacePanels.module.scss';

/** Decorative glyph, not translatable copy. */
const CHECK_MARK = '\u2713';

export function EvidencePanel({ events }: { events: EvidenceEvent[] }) {
  const { t } = useTranslation('dashboard');
  return (
    <Panel as="section" className={styles.evidencePanel}>
      <PanelHeader simple>
        <PanelTitle>
          <IconBox label="⌁" compact /> <h2>{t('evidence.title')}</h2>
        </PanelTitle>
        <button className={styles.smallLink}>
          {t('evidence.view')} <span>→</span>
        </button>
      </PanelHeader>

      <div className={styles.evidenceTimeline}>
        {[...events]
          .reverse()
          .slice(0, 3)
          .map((event) => (
            <article className={styles.evidenceEvent} key={`${event.time}-${event.title}`}>
              <time>{event.time}</time>
              <div>
                <strong>{event.title}</strong>
                <span>{t('evidence.by', { actor: event.actor })}</span>
              </div>
              <span className={styles.check}>{CHECK_MARK}</span>
            </article>
          ))}
      </div>
    </Panel>
  );
}
