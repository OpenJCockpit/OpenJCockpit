import { cx } from '../cx';
import styles from './BrandLogo.module.scss';

interface Props {
  className?: string;
  /** The square icon instead of the wide wordmark, for narrow spaces. */
  compact?: boolean;
}

/** The OpenJCockpit logo on its white plate. */
export function BrandLogo({ className, compact }: Props) {
  return (
    <img
      src={compact ? '/icon-192.png' : '/openjcockpit-logo.png'}
      // eslint-disable-next-line i18next/no-literal-string -- brand name, not translated
      alt="OpenJCockpit"
      className={cx(styles.logo, compact && styles.compact, className)}
    />
  );
}
