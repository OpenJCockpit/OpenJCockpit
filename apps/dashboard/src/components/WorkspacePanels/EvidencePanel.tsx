import type { EvidenceEvent } from '../../types';
import { IconBox } from '../IconBox/IconBox';
import styles from './WorkspacePanels.module.scss';
import { cx } from '../../ui/cx';

export function EvidencePanel({ events }: { events: EvidenceEvent[] }) {
  return (
    <section className={cx('panel', styles.evidencePanel)}>
      <header className="panel-header panel-header--simple">
        <div className="panel-title">
          <IconBox label="⌁" compact /> <h2>Execution evidence</h2>
        </div>
        <button className={styles.smallLink}>
          View evidence <span>→</span>
        </button>
      </header>

      <div className={styles.evidenceTimeline}>
        {[...events]
          .reverse()
          .slice(0, 3)
          .map((event) => (
            <article className={styles.evidenceEvent} key={`${event.time}-${event.title}`}>
              <time>{event.time}</time>
              <div>
                <strong>{event.title}</strong>
                <span>By: {event.actor}</span>
              </div>
              <span className={styles.check}>✓</span>
            </article>
          ))}
      </div>
    </section>
  );
}
