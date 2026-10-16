import { cx } from '../../ui/cx';
import styles from './IconBox.module.scss';

/** Base icon-box class, for markup that draws its own icon (see StackServiceIcon). */
export const iconBoxClass = styles.iconBox;

export function IconBox({
  label,
  variant,
  compact,
  className,
}: {
  label: string;
  variant?: 'hero';
  compact?: boolean;
  className?: string;
}) {
  return (
    <span
      className={cx(
        styles.iconBox,
        variant === 'hero' && styles.hero,
        compact && styles.compact,
        className,
      )}
      aria-hidden="true"
    >
      {label}
    </span>
  );
}
