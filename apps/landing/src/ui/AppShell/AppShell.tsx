import type { ReactNode } from 'react';
import { cx } from '../cx';
import styles from './AppShell.module.scss';

type AppShellProps = {
  className?: string;
  'data-testid'?: string;
  children?: ReactNode;
};

export default function AppShell({ className, children, ...rest }: AppShellProps) {
  return (
    <div className={styles.root}>
      <main className={cx(styles.shell, className)} {...rest}>
        {children}
      </main>
    </div>
  );
}
