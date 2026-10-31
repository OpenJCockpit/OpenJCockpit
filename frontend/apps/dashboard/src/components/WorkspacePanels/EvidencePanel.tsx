import type { EvidenceEvent } from '@openjcockpit/api';
import { useTranslation } from '@openjcockpit/i18n';
import { IconBox, Panel, PanelHeader, PanelTitle, StatusChip } from '@openjcockpit/ui';
import styles from './WorkspacePanels.module.scss';
import { actorName, eventTitle } from './sampleData';

/** Decorative glyph, not translatable copy. */
const CHECK_MARK = '\u2713';

export function EvidencePanel({ events }: { events: EvidenceEvent[] }) {
  const { t } = useTranslation('dashboard');
  return (
    <Panel as="section" className={styles.evidencePanel}>
      <PanelHeader simple>
        <PanelTitle>
          <IconBox label="⌁" compact /> <h2>{t('evidence.title')}</h2>
          <StatusChip label={t('sampleData')} kind="waiting" />
        </PanelTitle>
      </PanelHeader>

      <div className={styles.evidenceTimeline}>
        {[...events]
          .reverse()
          .slice(0, 3)
          .map((event) => (
            <article className={styles.evidenceEvent} key={`${event.time}-${event.title}`}>
              <time>{event.time}</time>
              <div>
                <strong>{eventTitle(t, event.title)}</strong>
                <span>{t('evidence.by', { actor: actorName(t, event.actor) })}</span>
              </div>
              <span className={styles.check}>{CHECK_MARK}</span>
            </article>
          ))}
      </div>
    </Panel>
  );
}
