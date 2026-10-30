import { cx } from '../cx';
import styles from './BrandLogo.module.scss';

/** The OpenJCockpit logo on its white plate. */
export function BrandLogo({ className }: { className?: string }) {
  return (
    // eslint-disable-next-line i18next/no-literal-string -- brand name, not translated
    <img src="/openjcockpit-logo.png" alt="OpenJCockpit" className={cx(styles.logo, className)} />
  );
}
