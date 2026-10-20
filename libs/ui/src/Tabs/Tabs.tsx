import type { ButtonHTMLAttributes, HTMLAttributes } from 'react';
import { cx } from '../cx';
import styles from './Tabs.module.scss';

/** Class applied to the selected tab; exposed so tests can assert the selection. */
export const tabActiveClass = styles.tabActive;

interface TabNavProps extends HTMLAttributes<HTMLElement> {
  /** Adds the bottom margin used when the nav sits above panel content. */
  secondary?: boolean;
}

export function TabNav({ secondary, className, ...rest }: TabNavProps) {
  return <nav className={cx(styles.nav, secondary && styles.navSecondary, className)} {...rest} />;
}

interface TabProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  active?: boolean;
}

export function Tab({ active, className, ...rest }: TabProps) {
  return <button className={cx(styles.tab, active && styles.tabActive, className)} {...rest} />;
}
