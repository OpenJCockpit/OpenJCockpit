import type { Workspace } from '@openjcockpit/api';
import styles from './StatusBar.module.scss';

export function StatusBar({ footer }: { footer: Workspace['footer'] }) {
  return (
    <footer className={styles.statusBar}>
      <span>● Environment: {footer.environment}</span>
      <span>☁ Region: {footer.region}</span>
      <span>⬡ Classification: {footer.dataClassification}</span>
      <span>Embabel version: {footer.embabelVersion}</span>
      <span>Spring AI Control Service: {footer.springAiControlVersion}</span>
      <span>© 2026 Metafactory B.V.</span>
    </footer>
  );
}
