import type { Workspace } from '../../types';

export function StatusBar({ footer }: { footer: Workspace['footer'] }) {
  return (
    <footer className="status-bar">
      <span>● Environment: {footer.environment}</span>
      <span>☁ Region: {footer.region}</span>
      <span>⬡ Classification: {footer.dataClassification}</span>
      <span>Embabel version: {footer.embabelVersion}</span>
      <span>Spring AI Control Service: {footer.springAiControlVersion}</span>
      <span>© 2026 Metafactory B.V.</span>
    </footer>
  );
}
