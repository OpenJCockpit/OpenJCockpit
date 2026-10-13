import type { EvidenceEvent } from '../../types';
import { IconBox } from '../IconBox/IconBox';

export function EvidencePanel({ events }: { events: EvidenceEvent[] }) {
  return (
    <section className="panel evidence-panel">
      <header className="panel-header panel-header--simple">
        <div className="panel-title">
          <IconBox label="⌁" compact /> <h2>Execution evidence</h2>
        </div>
        <button className="small-link">
          View evidence <span>→</span>
        </button>
      </header>

      <div className="evidence-timeline">
        {[...events]
          .reverse()
          .slice(0, 3)
          .map((event) => (
            <article className="evidence-event" key={`${event.time}-${event.title}`}>
              <time>{event.time}</time>
              <div>
                <strong>{event.title}</strong>
                <span>By: {event.actor}</span>
              </div>
              <span className="check">✓</span>
            </article>
          ))}
      </div>
    </section>
  );
}
