import type { QualityControl } from '../../types';
import { IconBox } from '../IconBox/IconBox';

export function QualityPanel({ controls }: { controls: QualityControl[] }) {
  return (
    <aside className="panel quality-panel">
      <header className="panel-header panel-header--quality">
        <div className="panel-title">
          <IconBox label="✓" compact /> <h2>Quality status</h2>
        </div>
        <button className="small-link">Open quality dashboard ↗</button>
      </header>

      <div className="quality-list">
        {controls.map((control) => (
          <article className="quality-row" key={control.name}>
            <IconBox label="⌬" compact />
            <strong>{control.name}</strong>
            <span className="check">✓</span>
          </article>
        ))}
      </div>

      <button className="link-button">
        View all quality rules <span>→</span>
      </button>
    </aside>
  );
}
