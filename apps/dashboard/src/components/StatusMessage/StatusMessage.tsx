import type { HTMLAttributes } from 'react';
import { fieldErrorClass, fieldHintClass } from '../../ui/Form/Form';
import { cx } from '../../ui/cx';
import styles from './StatusMessage.module.scss';

interface StatusMessageProps extends HTMLAttributes<HTMLElement> {
  /**
   * `error` renders the red error line, `info` the muted hint line and `loading` a
   * full-height centred message (rendered as a div) for screens that are still loading.
   */
  variant?: 'error' | 'info' | 'loading';
}

/** Inline feedback shared by the settings and workflow panels. */
export function StatusMessage({
  variant = 'info',
  className,
  children,
  ...rest
}: StatusMessageProps) {
  if (variant === 'loading') {
    return (
      <div className={cx(styles.loading, className)} {...rest}>
        {children}
      </div>
    );
  }
  const base = variant === 'error' ? fieldErrorClass : fieldHintClass;
  return (
    <p className={cx(base, className)} {...rest}>
      {children}
    </p>
  );
}
