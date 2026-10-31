import type { ReactNode } from 'react';
import { useTranslation } from '@openjcockpit/i18n';
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
export function AppHeader({ onBack, backLabel, backTitle, children }: AppHeaderProps) {
  const { t } = useTranslation();
  return (
    <header className={styles.siteHeader}>
      <div className={styles.brand}>
        <BrandLogo />
      </div>
      <div className={styles.headerActions}>
        {children}
        {onBack && (
          <Button variant="secondary" onClick={onBack} title={backTitle}>
            {/* The label names the destination; the generic one is the last resort. */}
            {backLabel ?? (backTitle ? `← ${backTitle}` : t('back'))}
          </Button>
        )}
      </div>
    </header>
  );
}
