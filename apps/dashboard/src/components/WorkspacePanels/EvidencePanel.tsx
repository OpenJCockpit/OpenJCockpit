import type { EvidenceEvent } from '../../types';
import { IconBox } from '../IconBox/IconBox';
import styles from './WorkspacePanels.module.scss';
import { Panel, PanelHeader, PanelTitle } from '../../ui/Panel/Panel';

export function EvidencePanel({ events }: { events: EvidenceEvent[] }) {
  return (
    <Panel as="section" className={styles.evidencePanel}>
      <PanelHeader simple>
        <PanelTitle>
          <IconBox label="⌁" compact /> <h2>Execution evidence</h2>
        </PanelTitle>
        <button className={styles.smallLink}>
          View evidence <span>→</span>
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
                <span>By: {event.actor}</span>
              </div>
              <span className={styles.check}>✓</span>
            </article>
          ))}
      </div>
    </Panel>
  );
}
