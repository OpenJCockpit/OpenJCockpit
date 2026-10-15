import type { HTMLAttributes } from 'react';
import { fieldErrorClass, fieldHintClass } from '../../ui/Form/Form';
import { cx } from '../../ui/cx';

interface StatusMessageProps extends HTMLAttributes<HTMLParagraphElement> {
  /** `error` renders the red error line; `info` the muted hint line. */
  variant?: 'error' | 'info';
}

/** Inline feedback paragraph shared by the settings and workflow panels. */
export function StatusMessage({
  variant = 'info',
  className,
  children,
  ...rest
}: StatusMessageProps) {
  const base = variant === 'error' ? fieldErrorClass : fieldHintClass;
  return (
    <p className={cx(base, className)} {...rest}>
      {children}
    </p>
  );
}
