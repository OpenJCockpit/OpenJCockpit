import type { ReactNode } from 'react';
import styles from './AppShell.module.scss';
import { Button } from '../Button/Button';
import { Backdrop } from '../Backdrop/Backdrop';
import { BrandLogo } from '../BrandLogo/BrandLogo';

/** Page chrome shared by every dashboard view: the animated background and the content wrapper. */
export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className={styles.appShell}>
      <Backdrop />
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
    <header className={styles.siteHeader}>
      <div className={styles.brand}>
        <BrandLogo />
      </div>
      <div className={styles.headerActions}>
        {children}
        {onBack && (
          <Button variant="secondary" onClick={onBack} title={backTitle}>
            {backLabel}
          </Button>
        )}
      </div>
    </header>
  );
}
