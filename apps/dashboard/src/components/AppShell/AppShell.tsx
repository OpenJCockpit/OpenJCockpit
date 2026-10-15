import type { ReactNode } from 'react';
import styles from './AppShell.module.scss';

/** Page chrome shared by every dashboard view: the animated background and the content wrapper. */
export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className={styles.appShell}>
      <div className="neural-bg" aria-hidden="true" />
      <div className="orb orb--left" aria-hidden="true" />
      <div className="orb orb--right" aria-hidden="true" />
      {children}
    </div>
  );
}

interface AppHeaderProps {
  onBack?: () => void;
  backLabel?: string;
  backTitle?: string;
  /** Extra header buttons, rendered before the back button. */
  children?: ReactNode;
}

/** The logo header with a back button (and optional extra actions) used by the sub-views. */
export function AppHeader({ onBack, backLabel = '← Back', backTitle, children }: AppHeaderProps) {
  return (
    <header className="site-header">
      <div className="brand">
        <img src="/openjcockpit-logo.png" alt="OpenJCockpit" className="brand-logo" />
      </div>
      <div className="header-actions">
        {children}
        {onBack && (
          <button className="back-button" onClick={onBack} title={backTitle}>
            {backLabel}
          </button>
        )}
      </div>
    </header>
  );
}
