import type { ButtonHTMLAttributes } from 'react';
import { cx } from '../cx';
import styles from './NavButton.module.scss';

// Callers pass `type`; the DOM stays a plain <button>.
export default function NavButton({ className, ...rest }: ButtonHTMLAttributes<HTMLButtonElement>) {
  return <button className={cx(styles.navButton, className)} {...rest} />;
}
