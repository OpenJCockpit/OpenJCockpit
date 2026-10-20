import { cx } from '../cx';
import styles from './BrandLogo.module.scss';

/** The OpenJCockpit logo on its white plate. */
export function BrandLogo({ className }: { className?: string }) {
  return (
    <img src="/openjcockpit-logo.png" alt="OpenJCockpit" className={cx(styles.logo, className)} />
  );
}
