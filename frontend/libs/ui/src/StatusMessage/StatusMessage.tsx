import type { HTMLAttributes } from 'react';
import { fieldErrorClass, fieldHintClass } from '../Form/Form';
import { cx } from '../cx';

import styles from './StatusMessage.module.scss';

interface StatusMessageProps extends HTMLAttributes<HTMLElement> {
  /**
   * `error` renders the red error line, `info` the muted hint line and `loading` a
   * full-height centred message (rendered as a div) for screens that are still loading.
   * `page` is the same centred block with the content stacked, for full-page notices
   * such as 403, 404 and crashes that carry an action.
   */
  variant?: 'error' | 'info' | 'loading' | 'page';
}

/** Inline feedback shared by the settings and workflow panels. */
export function StatusMessage({
  variant = 'info',
  className,
  children,
  ...rest
}: StatusMessageProps) {
  if (variant === 'loading' || variant === 'page') {
    return (
      <div className={cx(styles.loading, variant === 'page' && styles.page, className)} {...rest}>
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
