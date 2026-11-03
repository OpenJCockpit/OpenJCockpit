import type { EvidenceEvent } from '@openjcockpit/api';
import { useTranslation } from '@openjcockpit/i18n';
import { actorName, eventTitle } from '../sampleData';
import styles from './EvidenceList.module.scss';

/** Decorative glyph, not translatable copy. */
const CHECK_MARK = '\u2713';

interface Props {
  events: EvidenceEvent[];
  /** Newest events to show; all when omitted. */
  limit?: number;
}

export function EvidenceList({ events, limit }: Props) {
  const { t } = useTranslation('evidence');
  return (
    <div className={styles.evidenceTimeline}>
      {[...events]
        .reverse()
        .slice(0, limit)
        .map((event) => (
          <article className={styles.evidenceEvent} key={`${event.time}-${event.title}`}>
            <time>{event.time}</time>
            <div>
              <strong>{eventTitle(t, event.title)}</strong>
              <span>{t('by', { actor: actorName(t, event.actor) })}</span>
            </div>
            <span className={styles.check}>{CHECK_MARK}</span>
          </article>
        ))}
    </div>
  );
}
